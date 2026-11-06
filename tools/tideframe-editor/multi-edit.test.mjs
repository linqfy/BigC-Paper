import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createScreen, validateScreen } from './model.mjs';
import { editSelection, sharedChange, SHARED_FIELDS } from './multi-edit.mjs';

test('a shared change reaches every selected element and nothing else', () => {
  const screen = createScreen();
  const next = editSelection(screen, [1, 2], e => ({ style: { ...e.style, fillColor: '#ff0000' }, width: .5 }));
  assert.deepEqual(next.elements.map(e => e.style.fillColor === '#ff0000'), [false, true, true]);
  assert.deepEqual(next.elements.map(e => e.width), [screen.elements[0].width, .5, .5]);
  assert.deepEqual(next.elements.map(e => e.id), screen.elements.map(e => e.id), 'ids stay');
  assert.deepEqual(next.elements.map(e => e.text), screen.elements.map(e => e.text), 'texts stay');
  assert.notEqual(next.elements[1].style, next.elements[2].style, 'each element gets its own style object');
  assert.doesNotThrow(() => validateScreen(next));
  assert.equal(screen.elements[1].style.fillColor === '#ff0000', false, 'the original screen is untouched');
  assert.throws(() => editSelection(screen, [7], e => e), /Unknown selected row/);
});

test('only appearance, shape, size and layer are shared', () => {
  assert.equal(sharedChange({ style: 'fillColor' }), true);
  for (const field of ['shape', 'border', 'background', 'width', 'height', 'layer']) assert.equal(sharedChange({ field }), true, field);
  for (const field of ['id', 'text', 'x', 'y', 'kind', 'action']) assert.equal(sharedChange({ field }), false, field);
  assert.equal(SHARED_FIELDS.size, 6);
});

test('the inspector routes style and shared fields through the selection', () => {
  const app = readFileSync(new URL('./app.mjs', import.meta.url), 'utf8');
  assert.match(app, /changeSelection\(e => \(\{ style: \{ \.\.\.e\.style, \[styleField\]: value \} \}\), 'Edit appearance'\)/);
  assert.match(app, /if \(sharedChange\(\{ field \}\)\) changeSelection\(\(\) => change\); else changeActive\(change\);/);
});
