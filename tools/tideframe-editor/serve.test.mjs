import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from './serve.mjs';
import { mkdtemp, readFile, readdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createScreen, serializeScreen } from './model.mjs';

test('static server serves only allowlisted files and rejects mutation and traversal', async () => {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const page = await fetch(`${base}/`); assert.equal(page.status, 200);
    assert.match(await page.text(), /Editor de pantallas/);
    assert.match(page.headers.get('content-security-policy'), /default-src 'self'/);
    assert.match(page.headers.get('content-security-policy'), /img-src data:/);
    assert.match(page.headers.get('content-security-policy'), /connect-src 'none'/);
    assert.doesNotMatch(await (await fetch(`${base}/`)).text(), /tideframe-publish/);
    const images = await fetch(`${base}/images.mjs`); assert.equal(images.status, 200);
    const font = await fetch(`${base}/tideframe.woff`); assert.equal(font.status, 200);
    assert.equal(font.headers.get('content-type'), 'font/woff');
    const gestures = await fetch(`${base}/interaction.mjs`); assert.equal(gestures.status, 200);
    assert.match(await gestures.text(), /export function applyPointerGesture/);
    const head = await fetch(`${base}/model.mjs`, { method: 'HEAD' }); assert.equal(head.status, 200); assert.equal(await head.text(), '');
    for (const path of ['/LICENSE', '/model.test.mjs', '/%2e%2e%2fLICENSE', '/%ZZ', '/.git/config'])
      assert.equal((await fetch(`${base}${path}`)).status, 404);
    assert.equal((await fetch(`${base}/`, { method: 'POST' })).status, 405);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('publish writes a validated screen only for this page (origin + token)', async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'tideframe-publish-'));
  const server = createServer({ screensDir: dir, token: 'secret-token' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const body = serializeScreen(createScreen());
  const post = (headers, payload = body) => fetch(`${base}/publish`, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: payload });
  try {
    const page = await fetch(`${base}/`); assert.match(page.headers.get('content-security-policy'), /connect-src 'self'/);
    assert.match(await page.text(), /<meta name="tideframe-publish" content="secret-token">/);
    assert.equal((await post({ origin: 'http://evil.example', 'x-tideframe-token': 'secret-token' })).status, 403);
    assert.equal((await post({ origin: base })).status, 403);
    assert.equal((await post({ origin: base, 'x-tideframe-token': 'wrong-token!' })).status, 403);
    assert.equal((await post({ origin: base, 'x-tideframe-token': 'secret-token' }, '{"version":99}')).status, 422);
    const ok = await post({ origin: base, 'x-tideframe-token': 'secret-token' });
    assert.equal(ok.status, 200); const { stamp, ...published } = await ok.json(); assert.deepEqual(published, { ok: true, file: 'menu.json', id: 'menu' }); assert.ok(Number.isInteger(stamp));
    assert.equal(await readFile(path.join(dir, 'menu.json'), 'utf8'), body);
    assert.deepEqual(await readdir(dir), ['menu.json'], 'no temporary files left');
  } finally { await new Promise(resolve => server.close(resolve)); }
});
