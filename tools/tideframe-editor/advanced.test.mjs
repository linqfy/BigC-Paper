import test from 'node:test';
import assert from 'node:assert/strict';
import * as model from './model.mjs';
import * as interaction from './interaction.mjs';
import * as images from './images.mjs';
const empty = () => ({ ...model.createScreen(), elements: [] });
const element = (id, change = {}) => ({ id, text: '', x: 0, y: 0, width: .4, height: .2, kind: 'button', action: 'close', shape: 'rectangle', border: true, messageCondition: null, background: false, image: null, style: { opacity: 1, fillColor: '#28384b', borderColor: '#ffffff', textColor: '#ffffff', shadow: false }, layer: 0, actionArea: null, ...change });
test('v4 new screens default to low and legacy imports retain custom 1024x576', () => {
  const fresh = model.createScreen(); assert.equal(fresh.version, 4); assert.deepEqual(fresh.quality, { preset: 'low', width: 512, height: 288 });
  const old = { version: 1, id: 'old', title: 'OLD', elements: [{ id: 'b', text: 'B', x: 0, y: 0, width: .4, height: .2, kind: 'button', action: 'close' }] };
  const migrated = model.promoteScreen(old); assert.equal(migrated.version, 4); assert.deepEqual(migrated.quality, { preset: 'custom', width: 1024, height: 576 });
  assert.deepEqual(migrated.elements[0].style, element('x').style); assert.equal(old.version, 1);
});
test('quality presets and custom bounds are strict, including exact raw schema number', () => {
  for (const quality of [{ preset: 'medium', width: 1920, height: 1080 }, { preset: 'high', width: 2560, height: 1440 }, { preset: 'custom', width: 3840, height: 2160 }, { preset: 'custom', width: 256, height: 144 }]) model.validateScreen({ ...empty(), quality });
  for (const quality of [{ preset: 'high', width: 1920, height: 1080 }, { preset: 'custom', width: 3839, height: 2160 }, { preset: 'custom', width: 4096, height: 2304 }, { preset: 'legacy', width: 1024, height: 576 }]) assert.throws(() => model.validateScreen({ ...empty(), quality }), /quality/i);
  const raw = model.serializeScreen(empty()); assert.throws(() => model.parseScreen(raw.replace('"version": 4', '"version": 4.00000000000000001')), /version/i);
});
test('128 styled elements and decorative layers serialize without actions', () => {
  const value = { ...empty(), elements: Array.from({ length: 128 }, (_, i) => element(`d${i}`, { kind: 'decoration', action: '', border: false, layer: i - 64, style: { ...element('x').style, opacity: .35, shadow: true } })) };
  assert.deepEqual(model.parseScreen(model.serializeScreen(value)), value);
  assert.throws(() => model.validateScreen({ ...value, elements: [...value.elements, element('overflow')] }), /128/i);
  assert.throws(() => model.validateScreen({ ...empty(), elements: [element('d', { kind: 'decoration' })] }), /decoration/i);
  for (const change of [{ opacity: NaN }, { opacity: 1.1 }, { textColor: '#fff' }, { shadow: 'true' }]) assert.throws(() => model.validateScreen({ ...empty(), elements: [element('b', { style: { ...element('x').style, ...change } })] }), /style|opacity|Color|shadow/i);
});
test('only button effective areas collide, touching edges is valid, independent areas override visuals', () => {
  const first = element('a', { width: 1.4 }), second = element('b', { x: .4, width: 1.4 });
  assert.throws(() => model.validateScreen({ ...empty(), elements: [first, second] }), /overlap/i);
  const area = (x) => ({ x, y: 0, width: .4, height: .2 });
  const independent = { ...empty(), elements: [{ ...first, actionArea: area(-.2) }, { ...second, actionArea: area(.2) }] };
  model.validateScreen(independent);
  assert.deepEqual(model.effectiveActionArea(independent.elements[0]), area(-.2));
  model.validateScreen({ ...empty(), elements: [first, element('decor', { kind: 'decoration', action: '' }), element('label', { kind: 'label' })] });
});
test('stable display layers do not change original row identities', () => {
  const elements = [element('a', { layer: 2 }), element('b', { layer: -1 }), element('c', { layer: 2 })];
  assert.deepEqual(model.orderedElements({ ...empty(), elements }).map(item => [item.element.id, item.index]), [['b', 1], ['a', 0], ['c', 2]]);
});
test('adding buttons and pasting groups finds free action space and keeps group offsets', () => {
  let value = { ...empty(), elements: [element('a', { x: -.3 }), element('b', { x: .3 })] };
  const added = model.addElement(value, 'button'); assert.equal(added.elements.length, 3); model.validateScreen(added);
  const pasted = interaction.pasteClipboard(value, interaction.encodeClipboard(value, ['a', 'b']));
  assert.equal(pasted.ids.length, 2); assert.ok(Math.abs(pasted.screen.elements[3].x - pasted.screen.elements[2].x - .6) < 1e-12); model.validateScreen(pasted.screen);
  const full = { ...empty(), elements: [element('full', { width: 2, height: 2 })] }, before = JSON.stringify(full);
  assert.throws(() => interaction.pasteClipboard(full, interaction.encodeClipboard(full, ['full'])), /free|room|overlap/i); assert.equal(JSON.stringify(full), before);
});
test('group movement carries independent action centers and preserves immutable source', () => {
  const source = { ...empty(), elements: [element('a', { actionArea: { x: -.5, y: .5, width: .2, height: .2 } })] };
  const moved = interaction.moveSelection(source, ['a'], .1, -.2); assert.deepEqual(moved.elements[0].actionArea, { x: -.4, y: .3, width: .2, height: .2 }); assert.equal(source.elements[0].actionArea.x, -.5);
});
test('photo profile limits are 64MiB source / 40MiB PNG / 4K pixels and fit without enlargement', () => {
  assert.equal(images.MAX_SOURCE_BYTES, 67108864); assert.equal(images.MAX_PNG_BYTES, 41943040); assert.equal(images.MAX_PNG_SIDE, 3840); assert.equal(images.MAX_PNG_PIXELS, 8294400);
  assert.deepEqual(images.fitImageSize(7680, 4320, { width: 2560, height: 1440 }), { width: 2560, height: 1440 });
  assert.deepEqual(images.fitImageSize(3840, 2160, { width: 3840, height: 2160 }), { width: 3840, height: 2160 });
  assert.deepEqual(images.fitImageSize(3840, 3840, { width: 3840, height: 2160 }), { width: 2880, height: 2880 });
  assert.deepEqual(images.fitImageSize(64, 32, { width: 512, height: 288 }), { width: 64, height: 32 });
});

test('raw precision traps cannot turn fractional quality or layers into integers', () => {
  const screen = { ...empty(), quality: { preset: 'custom', width: 3840, height: 2160 }, elements: [element('b', { layer: 128 })] }, raw = JSON.stringify(screen);
  for (const changed of [raw.replace('"width":3840', '"width":3839.9999999999999999999'), raw.replace('"height":2160', '"height":2160.0000000000000000001'), raw.replace('"layer":128', '"layer":127.9999999999999999999')]) assert.throws(() => model.parseScreen(changed), /integer/i);
  assert.equal(model.parseScreen(raw.replace('"width":3840', '"width":3.84e3')).quality.width, 3840);
});

test('high precision paste does not round dimensions into an occupied action area', () => {
  const source = { ...empty(), elements: [element('a', { x: -.2999999, width: .20000010000000001, height: .20000010000000001 })] };
  const pasted = interaction.pasteClipboard(source, interaction.encodeClipboard(source, ['a']));
  model.validateScreen(pasted.screen); assert.equal(pasted.screen.elements[1].width, source.elements[0].width);
  assert.equal(pasted.screen.elements[1].action, 'close');
});

test('draft budget validation matches actual export bytes without creating photo copies', () => {
  assert.equal(typeof model.validateScreenBudget, 'function');
  const screen = model.createScreen(); assert.equal(model.validateScreenBudget(screen), screen);
  assert.equal(model.screenByteLength(screen), new TextEncoder().encode(model.serializeScreen(screen)).length);
  const clone = model.cloneScreen(screen); clone.elements[0].style.fillColor = '#000000'; assert.equal(screen.elements[0].style.fillColor, '#28384b');
});
