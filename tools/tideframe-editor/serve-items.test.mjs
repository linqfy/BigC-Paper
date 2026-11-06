// Publishing refuses a page that names an item the plugin's table lacks (the plugin would refuse the whole page).
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createServer } from './serve.mjs';
import { createScreen, upgradeLogic, serializeScreen } from './model.mjs';

function pageWith(item) {
  const screen = upgradeLogic(createScreen());
  screen.id = `items_${item}`;
  screen.elements = [...screen.elements, { ...screen.elements[0], id: 'icon', text: '', kind: 'label', x: 0, y: -.4, width: .125, height: .2222, item }];
  return serializeScreen(screen);
}

test('the editor finds the native module icon table without an explicit override', async () => {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const response = await fetch(`${base}/item-icons.mjs`);
    assert.equal(response.status, 200);
    const source = await response.text();
    const icons = JSON.parse(source.slice(source.indexOf('(') + 1, source.lastIndexOf(')')));
    assert.ok(icons.diamond);
    assert.ok(icons.diamond_sword);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

test('a page naming an item the game does not have is not published', async () => {
  const folder = mkdtempSync(path.join(tmpdir(), 'tf-items-')), table = path.join(folder, 'item-icons.tsv');
  writeFileSync(table, 'diamond\titem/diamond\t16\t16\t16\t16\t\n');
  const screens = path.join(folder, 'screens');
  const server = createServer({ screensDir: screens, token: 'token-for-test', iconTable: table });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address(), origin = `http://127.0.0.1:${port}`;
  const send = body => fetch(`${origin}/publish`, { method: 'POST', headers: { origin, 'content-type': 'application/json', 'x-tideframe-token': 'token-for-test' }, body });
  try {
    const refused = await send(pageWith('nonexistent_item'));
    assert.equal(refused.status, 422);
    assert.match((await refused.json()).error, /no item "nonexistent_item"/);
    assert.ok(!existsSync(path.join(screens, 'items_nonexistent_item.json')), 'nothing written');
    const published = await send(pageWith('diamond'));
    assert.equal(published.status, 200, await published.clone().text());
  } finally { server.close(); }
});
