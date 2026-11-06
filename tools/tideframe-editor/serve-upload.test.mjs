import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from './serve.mjs';
import { mkdtemp, mkdir, readFile, readdir } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

const GIF = Buffer.concat([Buffer.from('GIF89a', 'latin1'), Buffer.alloc(16)]);
const PNG = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), Buffer.alloc(16)]);

test('uploads write real GIF and PNG files into images/, only for this page, never outside it', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'tideframe-upload-'));
  const screens = path.join(root, 'screens'), images = path.join(root, 'images');
  await mkdir(screens);
  const server = createServer({ screensDir: screens, token: 'secret-token' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const send = (name, body, { origin = base, token = 'secret-token', replace = false } = {}) => fetch(`${base}/upload-image?name=${encodeURIComponent(name)}${replace ? '&replace=1' : ''}`,
    { method: 'POST', headers: { origin, 'x-tideframe-token': token, 'content-type': 'application/octet-stream' }, body });
  try {
    const gif = await send('fuego', GIF);
    assert.equal(gif.status, 200);
    assert.deepEqual(await gif.json(), { ok: true, file: 'fuego.gif', folder: 'fuego', bytes: GIF.length });
    assert.deepEqual(await readFile(path.join(images, 'fuego.gif')), GIF);
    const frame = await send('chispa/001.png', PNG);
    assert.equal(frame.status, 200);
    assert.deepEqual(await readdir(path.join(images, 'chispa')), ['001.png'], 'no temporary files left');
    assert.equal((await send('fuego', GIF)).status, 409, 'an existing file is not replaced silently');
    assert.equal((await send('fuego', GIF, { replace: true })).status, 200);
    assert.equal((await send('fuego', PNG)).status, 415, 'a GIF name needs GIF bytes');
    assert.equal((await send('chispa/002.png', GIF)).status, 415, 'a frame needs PNG bytes');
    for (const bad of ['../fuera', 'Fuego', 'a/b/c/d/e', 'chispa/../../x.png', 'chispa/.png', 'fuego.gif', ''])
      assert.equal((await send(bad, GIF)).status, 400, bad);
    assert.equal((await send('otro', GIF, { origin: 'http://evil.example' })).status, 403);
    assert.equal((await send('otro', GIF, { token: 'wrong-token!' })).status, 403);
    assert.deepEqual((await readdir(root)).sort(), ['images', 'screens'], 'nothing written outside images/');
  } finally { await new Promise(resolve => server.close(resolve)); }
  const plain = createServer();
  await new Promise(resolve => plain.listen(0, '127.0.0.1', resolve));
  try { assert.equal((await fetch(`http://127.0.0.1:${plain.address().port}/upload-image?name=x`, { method: 'POST', body: GIF })).status, 405, 'without a server folder there is no upload'); }
  finally { await new Promise(resolve => plain.close(resolve)); }
});
