import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from './serve.mjs';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

test('Probar reads frame animations from the server images folder, read only and only inside it', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'tideframe-images-'));
  const screens = path.join(root, 'screens'), images = path.join(root, 'images');
  await mkdir(path.join(images, 'fire'), { recursive: true }); await mkdir(screens);
  for (const name of ['10.png', '2.png', '1.png', 'notes.txt']) await writeFile(path.join(images, 'fire', name), 'x');
  await writeFile(path.join(images, 'star.gif'), 'GIF89a');
  await writeFile(path.join(root, 'secret.png'), 'no');
  const server = createServer({ screensDir: screens, token: 't' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    assert.match((await fetch(`${base}/`)).headers.get('content-security-policy'), /img-src data: 'self'/);
    assert.deepEqual(await (await fetch(`${base}/server-images/fire`)).json(),
      { type: 'png', frames: ['/server-images/fire/1.png', '/server-images/fire/2.png', '/server-images/fire/10.png'] }, 'natural order, like the plugin');
    assert.deepEqual(await (await fetch(`${base}/server-images/star`)).json(), { type: 'gif', url: '/server-images/star.gif' });
    assert.deepEqual(await (await fetch(`${base}/server-images/`)).json(), { folders: ['fire', 'star'] }, 'the checker learns which animations exist');
    const gif = await fetch(`${base}/server-images/star.gif`); assert.equal(gif.status, 200); assert.equal(gif.headers.get('content-type'), 'image/gif');
    for (const bad of ['/server-images/missing', '/server-images/fire/notes.txt', '/server-images/../secret.png', '/server-images/%2e%2e/secret.png',
      '/server-images/fire/..%2f..%2fsecret.png', '/server-images/.hidden.png'])
      assert.equal((await fetch(`${base}${bad}`)).status, 404, bad);
    assert.equal((await fetch(`${base}/server-images/fire`, { method: 'POST' })).status, 405);
  } finally { await new Promise(resolve => server.close(resolve)); }
  const plain = createServer();
  await new Promise(resolve => plain.listen(0, '127.0.0.1', resolve));
  try { assert.equal((await fetch(`http://127.0.0.1:${plain.address().port}/server-images/fire`)).status, 404, 'without a server folder there is no image route'); }
  finally { await new Promise(resolve => plain.close(resolve)); }
});
