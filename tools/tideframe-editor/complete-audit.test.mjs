import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, setElementLogic, mergeLogic, ELEMENT_PRESETS, serializeScreen, validateScreen, moveElement } from './model.mjs';
import { writeSetting, readSetting } from './advanced-settings.mjs';
import { readFileSync } from 'node:fs';

test('every current Java style and presentation property has an editor control', () => {
  const java = readFileSync(new URL('../../src/main/java/dev/linqfy/bigCasares/modules/tideframe/engine/core/Screen.java', import.meta.url), 'utf8');
  const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8');
  for (const type of ['Style', 'Presentation']) {
    const fields = java.match(new RegExp(`public record ${type}\\(([\\s\\S]*?)\\)`))[1].split(',').map(s => s.trim().split(/\s+/).at(-1));
    for (const field of fields) assert.ok(html.includes(type === 'Style' ? `data-style="${field}"` : `id="presentation-${field}"`), `${type}.${field} is missing from the editor`);
  }
});

test('presets replace imported camelCase aliases without duplicating event handlers', () => {
  const logic = mergeLogic({ onHover: [{ type: 'message', value: 'OLD' }], hidden: true }, ELEMENT_PRESETS['hover-sound']);
  assert.equal(Object.keys(logic).filter(k => /on.?hover/i.test(k)).length, 1);
  const screen = setElementLogic(createScreen(), 'start', logic);
  assert.equal(readSetting(screen, 'element', 'on-hover', 'start')[0].type, 'sound');
  assert.equal(logic.hidden, true);
});

test('new data lists begin with one safe row, and geometry stays valid when moved or resized', () => {
  const list = writeSetting(createScreen(), 'element', 'list.source', 'servers', 'close');
  assert.equal(readSetting(list, 'element', 'list.rows', 'close'), 1);
  assert.doesNotThrow(() => serializeScreen(list));
  assert.throws(() => writeSetting(list, 'element', 'list.rows', 16, 'close'), /bottom|screen|rows/i);
  let top = writeSetting(createScreen(), 'element', 'list.source', 'servers', 'heading');
  top = writeSetting(top, 'element', 'list.rows', 5, 'heading');
  assert.throws(() => moveElement(top, 'heading', 0, -.9), /bottom|screen|rows/i);
  const legacyList = setElementLogic(createScreen(), 'heading', { list: { source: 'servers', rows: 5 } });
  const bad = structuredClone(legacyList); bad.elements.find(e => e.id === 'heading').y = -.9;
  assert.throws(() => validateScreen(bad), /bottom|screen|rows/i);
});

test('lists cannot silently suppress a PNG or GIF animation', () => {
  const frames = writeSetting(createScreen(), 'element', 'frames.folder', 'fire', 'heading');
  assert.throws(() => writeSetting(frames, 'element', 'list.source', 'servers', 'heading'), /list|frames/i);
});

test('function parameter defaults are scalar values accepted by the server', () => {
  for (const value of [null, { bad: true }, ['x']]) assert.throws(() => writeSetting(createScreen(), 'screen', 'functions', {
    greet: { params: { name: value }, actions: [{ type: 'close' }] }
  }), /parameter|scalar/i);
  assert.doesNotThrow(() => writeSetting(createScreen(), 'screen', 'functions', { greet: { params: { name: 'Player', amount: 1, vip: true }, actions: [{ type: 'close' }] } }));
});
