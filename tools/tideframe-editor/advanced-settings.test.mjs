import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, parseScreen, serializeScreen, setScreenLogic, setElementLogic } from './model.mjs';
import { SETTINGS, ACTION_RECIPES, readSetting, writeSetting, removeFeature, appendAction } from './advanced-settings.mjs';
import { createServer } from './serve.mjs';

test('editing one advanced setting preserves Claude logic, images and unrelated nested options', () => {
  const initial = setScreenLogic(createScreen(), { variables: { coins: '5' }, animation: { open: { effect: 'slide', from: 'right', distance: 80 }, close: { effect: 'fade' } } });
  const next = writeSetting(initial, 'screen', 'animation.close.effect', 'slide');
  assert.equal(initial.logic.animation.close.effect, 'fade');
  assert.deepEqual(next.logic.animation.open, initial.logic.animation.open);
  assert.deepEqual(next.logic.variables, { coins: '5' });
  assert.equal(next.logic.animation.close.effect, 'slide');
  assert.deepEqual(parseScreen(serializeScreen(next)), next);
  const removed = writeSetting(next, 'screen', 'animation.close.effect', undefined);
  assert.equal(removed.logic.animation.close, undefined);
  assert.ok(removed.logic.animation.open);
});

test('all screen and element features have advanced controls, without incompatible defaults', () => {
  const paths = scope => SETTINGS.filter(s => s.scope === scope).map(s => s.path.split('.')[0]);
  for (const key of ['behavior', 'variables', 'functions', 'keybinds', 'command', 'permission', 'open-on-join', 'on-open', 'on-close', 'animation']) assert.ok(paths('screen').includes(key), key);
  for (const key of ['actions', 'right-actions', 'hidden', 'visible-if', 'frames', 'list', 'draggable', 'on-drop', 'on-hover', 'on-leave', 'tooltip', 'keyframes']) assert.ok(paths('element').includes(key), key);
  let s = createScreen();
  s = writeSetting(s, 'screen', 'animation.close.effect', 'slide');
  s = writeSetting(s, 'screen', 'behavior.close-on-sneak', false);
  s = writeSetting(s, 'element', 'frames.folder', 'fire', 'heading');
  assert.equal(readSetting(s, 'element', 'frames.interval-ms', 'heading'), undefined, 'automatic GIF timing must stay omitted');
  assert.equal(readSetting(s, 'screen', 'behavior.close-on-sneak'), false);
  assert.equal(readSetting(createScreen(), 'screen', 'behavior.close-on-sneak'), undefined);
  assert.equal(s.version, 9);
});

test('controls reject server-incompatible ranges, conflicting animations and non-button events atomically', () => {
  const s = createScreen(), original = serializeScreen(s);
  for (const [path, value] of [['list.rows', 17], ['frames.interval-ms', 10], ['frames.folder', '../evil'], ['list.spacing', 0]])
    assert.throws(() => writeSetting(s, 'element', path, value, 'heading'));
  assert.throws(() => writeSetting(s, 'screen', 'command', 'op'), /reserved/i);
  assert.throws(() => writeSetting(s, 'screen', 'animation.open.distance', 257));
  assert.throws(() => writeSetting(s, 'element', 'actions', [{ type: 'close' }], 'heading'), /button/);
  const motion = setElementLogic(s, 'start', { keyframes: { steps: [{ at: 0 }, { at: 200, scale: 1.1 }] } });
  assert.throws(() => writeSetting(motion, 'element', 'frames.folder', 'fire', 'start'), /frames|keyframes/i);
  assert.equal(serializeScreen(s), original);
  assert.throws(() => writeSetting(s, 'element', 'hidden', true, 'missing'), /element/i);
});

test('all action recipes are independent editable templates and survive real JSON publication', () => {
  assert.ok(ACTION_RECIPES.length >= 24);
  for (const recipe of ACTION_RECIPES) {
    const actions = appendAction('', recipe.id);
    const initial = setScreenLogic(createScreen(), { 'on-open': actions });
    assert.deepEqual(parseScreen(serializeScreen(initial)).logic['on-open'], actions, recipe.id);
    const copy = appendAction(actions, recipe.id);
    assert.equal(copy.length, 2);
    assert.equal(actions.length, 1);
  }
  assert.throws(() => appendAction('{broken', 'close'));
  assert.throws(() => appendAction('{}', 'close'), /list/i);
});

test('editing imported camelCase keys updates the existing spelling without creating ambiguous aliases', () => {
  const initial = setScreenLogic(createScreen(), { openOnJoin: true, behavior: { closeOnSneak: true, keepOpen: true } });
  const next = writeSetting(initial, 'screen', 'behavior.close-on-sneak', false);
  assert.deepEqual(next.logic.behavior, { closeOnSneak: false, keepOpen: true });
  assert.equal(readSetting(next, 'screen', 'open-on-join'), true);
  const event = setElementLogic(next, 'start', { onHover: [{ type: 'sound', value: 'UI_BUTTON_CLICK 1 1' }] });
  const edited = writeSetting(event, 'element', 'on-hover', [{ type: 'message', value: 'Hi' }], 'start');
  assert.equal(edited.elements.find(e => e.id === 'start').logic['on-hover'], undefined);
  assert.equal(edited.elements.find(e => e.id === 'start').logic.onHover[0].type, 'message');
});

test('removing one optional feature permits switching animation type and preserves event actions', () => {
  let s = writeSetting(createScreen(), 'element', 'actions', [{ type: 'close' }], 'start');
  s = writeSetting(s, 'element', 'keyframes.trigger', 'loop', 'start');
  s = removeFeature(s, 'element', 'keyframes', 'start');
  s = writeSetting(s, 'element', 'frames.folder', 'fire', 'start');
  assert.deepEqual(s.elements.find(e => e.id === 'start').logic, { actions: [{ type: 'close' }], frames: { folder: 'fire' } });
  s = removeFeature(s, 'element', 'frames', 'start');
  assert.deepEqual(s.elements.find(e => e.id === 'start').logic, { actions: [{ type: 'close' }] });
  assert.throws(() => writeSetting(s, 'element', '__proto__.polluted', true, 'start'), /Unknown/);
});

test('structured data editors reject invalid server names, key bindings and incomplete functions', () => {
  const s = createScreen();
  for (const [path, value] of [['variables', { 'BAD NAME': '1' }], ['functions', { empty: { actions: [] } }],
    ['keybinds', [{ key: 'ESCAPE', actions: [{ type: 'close' }] }]]]) assert.throws(() => writeSetting(s, 'screen', path, value));
  assert.throws(() => writeSetting(s, 'element', 'list.filter', 'bad name', 'start'));
});

test('new advanced module is served on loopback, while tests and paths stay private', async () => {
  const server = createServer(); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    for (const name of ['advanced-settings.mjs', 'advanced-ui.mjs']) assert.equal((await fetch(`${base}/${name}`)).status, 200);
    assert.equal((await fetch(`${base}/advanced-settings.test.mjs`)).status, 404);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
