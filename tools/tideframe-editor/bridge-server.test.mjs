import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from './serve.mjs';
import { mkdtemp, mkdir, readFile, readdir, writeFile, stat } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createScreen, serializeScreen } from './model.mjs';

async function listen(options) {
  const server = createServer(options);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  return { server, base: `http://127.0.0.1:${server.address().port}`, close: () => new Promise(resolve => server.close(resolve)) };
}

test('the plugin status is read next to the screens folder; open requests are written there only for this page', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'tideframe-bridge-'));
  const screens = path.join(root, 'screens'); await mkdir(screens);
  const { base, close } = await listen({ screensDir: screens, token: 'secret-token' });
  const open = (body, { origin = base, token = 'secret-token' } = {}) => fetch(`${base}/open-in-game`,
    { method: 'POST', headers: { origin, 'x-tideframe-token': token, 'content-type': 'application/json' }, body: JSON.stringify(body) });
  try {
    assert.deepEqual(await (await fetch(`${base}/server-status`)).json(), { ok: null }, 'no status before the plugin writes one');
    const report = { time: 5, ok: true, error: null, screens: ['menu'], problems: {}, stamps: { menu: 1730000000123 }, open: null };
    await writeFile(path.join(root, 'editor-status.json'), JSON.stringify(report));
    assert.deepEqual(await (await fetch(`${base}/server-status`)).json(), report);
    assert.equal((await open({ screen: 'menu' }, { origin: 'http://evil.example' })).status, 403);
    assert.equal((await open({ screen: 'menu' }, { token: 'wrong-token!' })).status, 403);
    for (const bad of [{ screen: 'Menu' }, { screen: '../x' }, { screen: 'menu', player: 'not a name' }, { player: 'Steve' }])
      assert.equal((await open(bad)).status, 400, JSON.stringify(bad));
    assert.deepEqual((await readdir(root)).sort(), ['editor-status.json', 'screens'], 'nothing is written for a rejected request');
    const sent = await open({ screen: 'menu', player: 'Steve' });
    assert.equal(sent.status, 200);
    const { ok, request } = await sent.json();
    assert.equal(ok, true); assert.match(request, /^[0-9a-f]{16}$/);
    assert.deepEqual(JSON.parse(await readFile(path.join(root, 'editor-open.json'), 'utf8')), { screen: 'menu', player: 'Steve', request });
    const second = await (await open({ screen: 'menu', player: '' })).json();
    assert.notEqual(second.request, request, 'each request has its own id, so the page finds its own answer');
    assert.deepEqual(JSON.parse(await readFile(path.join(root, 'editor-open.json'), 'utf8')), { screen: 'menu', request: second.request }, 'an empty name is no name');
    assert.deepEqual((await readdir(root)).sort(), ['editor-open.json', 'editor-status.json', 'screens'], 'no temporary files left');
  } finally { await close(); }
});

test('publish answers with the file time in whole milliseconds, the number the plugin reports once the file is live', async () => {
  const screens = await mkdtemp(path.join(os.tmpdir(), 'tideframe-stamp-'));
  const { base, close } = await listen({ screensDir: screens, token: 'secret-token' });
  try {
    const before = Date.now();
    const response = await fetch(`${base}/publish`, { method: 'POST', headers: { origin: base, 'x-tideframe-token': 'secret-token', 'content-type': 'application/json' }, body: serializeScreen(createScreen()) });
    const { stamp } = await response.json();
    assert.ok(Number.isInteger(stamp) && Math.abs(stamp - before) < 60000, String(stamp));
    // Java's FileTime.toMillis() rounds down; rounding to nearest would miss the plugin's number half the time.
    assert.equal(stamp, Number((await stat(path.join(screens, 'menu.json'), { bigint: true })).mtimeNs / 1000000n));
  } finally { await close(); }
});

test('without a screens folder there is no status and no way to open anything', async () => {
  const { base, close } = await listen();
  try {
    assert.equal((await fetch(`${base}/server-status`)).status, 404);
    assert.equal((await fetch(`${base}/open-in-game`, { method: 'POST', headers: { origin: base, 'content-type': 'application/json' }, body: '{"screen":"menu"}' })).status, 405);
  } finally { await close(); }
});
