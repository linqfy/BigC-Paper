import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import * as editor from './serve.mjs';
import { createScreen, serializeScreen } from './model.mjs';

test('--server selects only the embedded BigCasares engine screens', () => {
  assert.equal(editor.resolveScreensDir(['--server', 'server with spaces'], {}),
    path.resolve('server with spaces', 'plugins', 'BigCasares', 'tideframe', 'engine', 'screens'));
});

test('explicit screens selection overrides server discovery and the environment', () => {
  assert.equal(editor.resolveScreensDir(['--server', 'server', '--screens', 'selected screens'], { TIDEFRAME_SCREENS: 'env screens' }),
    path.resolve('selected screens'));
});

test('--server overrides the environment while the explicit environment selection still works alone', () => {
  assert.equal(editor.resolveScreensDir(['--server', 'server'], { TIDEFRAME_SCREENS: 'env screens' }),
    path.resolve('server', 'plugins', 'BigCasares', 'tideframe', 'engine', 'screens'));
  assert.equal(editor.resolveScreensDir([], { TIDEFRAME_SCREENS: 'env screens' }), path.resolve('env screens'));
});

test('without a selected directory the editor remains disconnected', () => {
  assert.equal(editor.resolveScreensDir([], {}), null);
});

test('directory flags reject missing paths instead of selecting another flag as a folder', () => {
  for (const args of [['--server'], ['--screens'], ['--server', '--screens', 'chosen']])
    assert.throws(() => editor.resolveScreensDir(args, {}), /requires a path/);
});

test('--server keeps publishing, game bridge, images and sounds inside the embedded engine', async () => {
  const root = await mkdtemp(path.join(os.tmpdir(), 'bigc-editor-directory-'));
  let server;
  try {
    const engine = path.join(root, 'plugins', 'BigCasares', 'tideframe', 'engine');
    await mkdir(path.join(engine, 'images'), { recursive: true });
    await mkdir(path.join(engine, 'sounds'));
    const report = { ok: true, screens: ['menu'] };
    await writeFile(path.join(engine, 'editor-status.json'), JSON.stringify(report));
    const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLttAAAAABJRU5ErkJggg==', 'base64');
    await writeFile(path.join(engine, 'images', 'local.png'), image);
    server = editor.createServer({ screensDir: editor.resolveScreensDir(['--server', root], {}), token: 'native-module-token' });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = `http://127.0.0.1:${server.address().port}`;
    const headers = { origin: base, 'x-tideframe-token': 'native-module-token', 'content-type': 'application/json' };
    assert.deepEqual(await (await fetch(`${base}/server-status`)).json(), report);
    const screen = createScreen();
    const published = await fetch(`${base}/publish`, { method: 'POST', headers, body: serializeScreen(screen) });
    assert.equal(published.status, 200, await published.clone().text());
    assert.equal(await readFile(path.join(engine, 'screens', `${screen.id}.json`), 'utf8'), serializeScreen(screen));
    const opened = await fetch(`${base}/open-in-game`, { method: 'POST', headers, body: JSON.stringify({ screen: screen.id }) });
    assert.equal(opened.status, 200);
    const request = await opened.json();
    assert.deepEqual(JSON.parse(await readFile(path.join(engine, 'editor-open.json'), 'utf8')), { screen: screen.id, request: request.request });
    const preview = await fetch(`${base}/server-images/local.png`);
    assert.equal(preview.status, 200);
    assert.deepEqual(Buffer.from(await preview.arrayBuffer()), image);
    assert.deepEqual(await (await fetch(`${base}/server-sounds`)).json(), { sounds: [] });
    assert.deepEqual(await readdir(path.join(root, 'plugins')), ['BigCasares']);
  } finally {
    if (server?.listening) await new Promise(resolve => server.close(resolve));
    await rm(root, { recursive: true, force: true });
  }
});
