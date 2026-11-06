// Custom sounds (idea 30): the editor uploads Ogg Vorbis files to the server's sounds folder, lists them and plays them.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer, vorbisChannels } from './serve.mjs';
import { mkdtemp, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

/** The start of an Ogg Vorbis file (as CustomSoundsTest.vorbis): an Ogg page with the Vorbis identification header. */
function vorbis(channels, size = 64) {
  const out = Buffer.alloc(Math.max(size, 64));
  out.write('OggS', 0, 'latin1'); out[5] = 2; out[26] = 1; out[27] = 30; out[28] = 1; out.write('vorbis', 29, 'latin1'); out[39] = channels;
  return out;
}

test('only Ogg Vorbis counts as a sound, as in the plugin', () => {
  assert.equal(vorbisChannels(vorbis(1)), 1);
  assert.equal(vorbisChannels(vorbis(2)), 2);
  const opus = vorbis(1); opus.write('OpusHe', 28, 'latin1');
  assert.equal(vorbisChannels(opus), 0);
  assert.equal(vorbisChannels(Buffer.from('ID3'.padEnd(80, '.'))), 0);
  assert.equal(vorbisChannels(vorbis(0)), 0);
});
test('sounds are uploaded, listed and served only for this page and only inside sounds/', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'tideframe-sounds-'));
  const screens = path.join(root, 'screens'), sounds = path.join(root, 'sounds');
  await mkdir(screens);
  const server = createServer({ screensDir: screens, token: 'secret-token' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const send = (name, body, { origin = base, token = 'secret-token', replace = false } = {}) => fetch(`${base}/upload-sound?name=${encodeURIComponent(name)}${replace ? '&replace=1' : ''}`,
    { method: 'POST', headers: { origin, 'x-tideframe-token': token, 'content-type': 'audio/ogg' }, body });
  try {
    assert.deepEqual(await (await fetch(`${base}/server-sounds`)).json(), { sounds: [] }, 'no folder yet: no sounds');
    const click = await send('clic', vorbis(1, 300));
    assert.equal(click.status, 200);
    assert.deepEqual(await click.json(), { ok: true, name: 'clic', id: 'tideframe:clic', bytes: 300, channels: 1 });
    assert.deepEqual(await readdir(sounds), ['clic.ogg'], 'no temporary files left');
    assert.equal((await send('clic', vorbis(1))).status, 409, 'an existing sound is not replaced silently');
    assert.equal((await send('clic', vorbis(2, 90), { replace: true })).status, 200);
    assert.equal((await send('musica', Buffer.from('not audio'.padEnd(100, '.')))).status, 415);
    assert.equal((await send('grande', Buffer.concat([vorbis(1), Buffer.alloc(2 * 1024 * 1024)]))).status, 413);
    for (const bad of ['../fuera', 'Clic', 'clic.ogg', 'a-b', '', 'x'.repeat(33)]) assert.equal((await send(bad, vorbis(1))).status, 400, bad);
    assert.equal((await send('otro', vorbis(1), { origin: 'http://evil.example' })).status, 403);
    assert.equal((await send('otro', vorbis(1), { token: 'wrong-token!' })).status, 403);
    await writeFile(path.join(sounds, 'roto.ogg'), 'not audio either, long enough to be read by the list...........');
    const listed = await (await fetch(`${base}/server-sounds`)).json();
    assert.deepEqual(listed, { sounds: [{ name: 'clic', id: 'tideframe:clic', bytes: 90, channels: 2 }] }, 'files the plugin would skip are not listed');
    const file = await fetch(`${base}/server-sounds/clic.ogg`);
    assert.equal(file.status, 200); assert.equal(file.headers.get('content-type'), 'audio/ogg');
    assert.deepEqual(Buffer.from(await file.arrayBuffer()), await readFile(path.join(sounds, 'clic.ogg')));
    for (const bad of ['/server-sounds/../screens/x.ogg', '/server-sounds/nada.ogg', '/server-sounds/Clic.ogg']) assert.equal((await fetch(`${base}${bad}`)).status, 404, bad);
    assert.deepEqual((await readdir(root)).sort(), ['screens', 'sounds'], 'nothing written outside sounds/');
  } finally { await new Promise(resolve => server.close(resolve)); }
  const plain = createServer();
  await new Promise(resolve => plain.listen(0, '127.0.0.1', resolve));
  try {
    const port = plain.address().port;
    assert.equal((await fetch(`http://127.0.0.1:${port}/upload-sound?name=x`, { method: 'POST', body: vorbis(1) })).status, 405, 'without a server folder there is no upload');
    assert.equal((await fetch(`http://127.0.0.1:${port}/server-sounds`)).status, 404);
  } finally { await new Promise(resolve => plain.close(resolve)); }
});
