import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createServer } from './serve.mjs';

test('all literal application IDs exist exactly once in the accessible editor document', async () => {
  const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
  const app = await readFile(new URL('./app.mjs', import.meta.url), 'utf8');
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
  assert.equal(new Set(ids).size, ids.length, 'Duplicate IDs break inspector and keyboard bindings.');
  for (const match of app.matchAll(/\$\('([^']+)'\)/g)) assert.ok(ids.includes(match[1]), `Missing application target #${match[1]}.`);
  for (const id of ['undo', 'redo', 'cut-selection', 'duplicate-selection', 'select-all', 'select-none', 'select-invert', 'add-page', 'duplicate-page', 'close-page', 'save-project', 'download-project', 'export-all', 'zoom-control', 'layout-selection'])
    assert.ok(ids.includes(id), `Required authoring control #${id} is absent.`);
  assert.match(html, /id="element-text"[^>]*maxlength="512"/);
});

test('loopback editor serves authoring and presentation modules with script MIME and retains the exact file allowlist', async () => {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    for (const file of ['authoring.mjs', 'workspace.mjs', 'presentation-tools.mjs']) {
      const response = await fetch(`${base}/${file}`); assert.equal(response.status, 200, `${file} must load from the application origin.`);
      assert.equal(response.headers.get('content-type'), 'text/javascript; charset=utf-8');
      assert.match(response.headers.get('content-security-policy'), /connect-src 'none'/);
    }
    for (const file of ['authoring.test.mjs', 'workspace.test.mjs', 'presentation-tools.test.mjs', '../LICENSE', '%2e%2e%2fLICENSE'])
      assert.equal((await fetch(`${base}/${file}`)).status, 404);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('presentation controls expose bounded manual game scale, passive overlay and original cursor choices', async () => {
  const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
  for (const id of ['presentation-mode', 'presentation-scale', 'presentation-cursor', 'presentation-help', 'presentation-summary', 'add-close-x', 'add-text-block', 'fill-canvas'])
    assert.match(html, new RegExp(`\\bid="${id}"`), `Required presentation control ${id} is missing.`);
  const select = id => html.match(new RegExp(`<select[^>]+id="${id}"[^>]*>([\\s\\S]*?)</select>`))?.[1] ?? '';
  assert.deepEqual([...select('presentation-mode').matchAll(/value="([^"]+)"/g)].map(m => m[1]), ['menu', 'overlay']);
  assert.deepEqual([...select('presentation-scale').matchAll(/value="([^"]+)"/g)].map(m => m[1]), ['1', '2', '3']);
  assert.deepEqual([...select('presentation-cursor').matchAll(/value="([^"]+)"/g)].map(m => m[1]), ['arrow', 'cross', 'block', 'text']);
  assert.match(html, /Escala manual del HUD/i);
  assert.match(html, /No detecta sola/i);
  assert.match(html, /id="style-brightness"[^>]*data-style="brightness"[^>]*min="0"[^>]*max="2"/);
  assert.match(html, /for="style-brightness"/);
});
