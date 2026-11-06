import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, validateScreen, parseScreen, serializeScreen, addElement, moveElement, deleteElement, replaceElement, promoteScreen } from './model.mjs';

const element = (extra = {}) => ({ id: 'start', text: 'START', x: 0, y: 0, width: 0.8, height: 0.18, kind: 'button', action: 'message:Hello.', ...extra });
const screen = (extra = {}) => ({ version: 1, id: 'menu', title: 'TIDEFRAME', elements: [element()], ...extra });

test('new screen is valid and includes only the schema keys', () => {
  assert.deepEqual(Object.keys(validateScreen(createScreen())).sort(), ['elements', 'id', 'quality', 'title', 'version']);
});
test('JSON export round-trips centers, dimensions and literal text', () => {
  const value = screen({ elements: [element({ x: -0.7, y: 0.9, text: '<HELLO>', action: 'open:profile' })] });
  assert.deepEqual(parseScreen(serializeScreen(value)), value);
});
test('add then move and delete leaves the original screen untouched', () => {
  const original = screen();
  const added = addElement(original, 'label');
  assert.equal(added.elements.length, 2);
  assert.equal(original.elements.length, 1);
  assert.notEqual(added.elements[1].id, 'start');
  const moved = moveElement(added, added.elements[1].id, -1, 1);
  assert.equal(moved.elements[1].x, -1);
  assert.equal(moved.elements[1].y, 1);
  assert.equal(added.elements[1].x, 0);
  assert.deepEqual(deleteElement(moved, moved.elements[1].id), promoteScreen(original));
});
test('replace element validates and changes only the selected element', () => {
  const changed = replaceElement(screen(), 'start', { ...element(), text: 'READY' });
  assert.equal(changed.elements[0].text, 'READY');
  assert.throws(() => replaceElement(screen(), 'start', element({ width: 0 })), /width/i);
});
test('unknown screen and element fields and missing fields are rejected', () => {
  assert.throws(() => validateScreen(screen({ extra: 1 })), /keys/i);
  assert.throws(() => validateScreen(screen({ elements: [element({ extra: 1 })] })), /keys/i);
  const value = screen(); delete value.title;
  assert.throws(() => validateScreen(value), /keys/i);
});
test('screen and element IDs use the allowlisted lowercase syntax', () => {
  for (const id of ['', 'Upper', '../menu', 'a'.repeat(33), '__proto__']) {
    assert.throws(() => validateScreen(screen({ id })), /id/i);
    assert.throws(() => validateScreen(screen({ elements: [element({ id })] })), /id/i);
  }
});
test('duplicate element IDs are rejected', () => assert.throws(() => validateScreen(screen({ elements: [element(), element()] })), /duplicate/i));
test('only versions one through four are accepted, including raw precision traps', () => {
  for (const version of [0, 5, '1', null]) assert.throws(() => validateScreen(screen({ version })), /version/i);
  assert.throws(() => parseScreen(JSON.stringify(screen()).replace('"version":1', '"version":1.0000000000000000001')), /version/i);
  assert.equal(parseScreen(JSON.stringify(screen()).replace('"version":1', '"version":1e0')).version, 1);
});

test('new screens use schema four with transparent fill and visible borders', () => {
  const value = createScreen(); assert.equal(value.version, 4);
  for (const e of value.elements) {
    assert.equal(e.shape, 'rectangle'); assert.equal(e.border, true); assert.equal(e.messageCondition, null);
    assert.equal(e.background, false); assert.equal(e.image, null);
  }
  assert.deepEqual(parseScreen(serializeScreen(value)), value);
});
test('legacy import is lossless and explicit promotion supplies appearance defaults', () => {
  const legacy = screen(); assert.deepEqual(parseScreen(serializeScreen(legacy)), legacy);
  const promoted = promoteScreen(legacy);
  assert.equal(promoted.version, 4); assert.equal(legacy.version, 1);
  assert.deepEqual(promoted.elements[0], { ...legacy.elements[0], shape: 'rectangle', border: true, messageCondition: null, background: false, image: null, style: { opacity: 1, fillColor: '#28384b', borderColor: '#ffffff', textColor: '#ffffff', shadow: false }, layer: 0, actionArea: null });
});

test('version two import promotes transparently while retaining its appearance and conditional message', () => {
  const legacy = { version: 2, id: 'menu', title: 'TIDEFRAME', elements: [{ ...element(), shape: 'ellipse', border: false,
    action: 'conditional-message', messageCondition: { permission: 'example.vip', ifTrue: 'YES', ifFalse: 'NO' } }] };
  const upgraded = promoteScreen(parseScreen(JSON.stringify(legacy)));
  assert.equal(upgraded.version, 4); assert.equal(upgraded.elements[0].background, false); assert.equal(upgraded.elements[0].image, null);
  assert.equal(upgraded.elements[0].shape, 'ellipse'); assert.deepEqual(upgraded.elements[0].messageCondition, legacy.elements[0].messageCondition);
});

test('version three requires exact background and image keys and rejects non-PNG image references', () => {
  const value = { version: 3, id: 'menu', title: 'TIDEFRAME', elements: [{ ...element(), shape: 'rectangle', border: true, messageCondition: null, background: false, image: null }] };
  for (const change of [{ background: 'false' }, { image: '' }, { image: 'https://example.com/a.png' }, { image: 'data:image/png;base64,AAAA' }, { image: '../a.png' }])
    assert.throws(() => validateScreen({ ...value, elements: [{ ...value.elements[0], ...change }] }), /background|image|PNG|base64/i);
  for (const field of ['background', 'image']) {
    const missing = structuredClone(value); delete missing.elements[0][field]; assert.throws(() => validateScreen(missing), /keys/i);
  }
  assert.throws(() => parseScreen(JSON.stringify(value).replace('"version":3', '"version":3.0000000000000000001')), /version/i);
  assert.equal(parseScreen(JSON.stringify(value).replace('"version":3', '"version":3e0')).version, 3);
});
test('version two appearance keys are required and shapes and borders are strict', () => {
  const value = { version: 2, id: 'menu', title: 'TIDEFRAME', elements: [{ ...element(), shape: 'rectangle', border: true, messageCondition: null }] };
  for (const shape of ['rectangle', 'rounded', 'ellipse']) validateScreen({ ...value, elements: [{ ...value.elements[0], shape }] });
  assert.throws(() => validateScreen({ ...value, elements: [{ ...value.elements[0], shape: 'triangle' }] }), /shape/i);
  assert.throws(() => validateScreen({ ...value, elements: [{ ...value.elements[0], border: 'true' }] }), /border/i);
  const missing = structuredClone(value); delete missing.elements[0].border;
  assert.throws(() => validateScreen(missing), /keys/i);
  assert.throws(() => parseScreen(JSON.stringify(value).replace('"version":2', '"version":2.0000000000000000001')), /version/i);
});
test('conditional messages require a button, strict permission and literal ASCII branches', () => {
  const value = promoteScreen(screen());
  const e = { ...value.elements[0], action: 'conditional-message', messageCondition: { permission: 'tideframe.vip', ifTrue: 'Welcome, VIP.', ifFalse: 'Welcome.' } };
  const withElement = change => ({ ...value, elements: [{ ...e, ...change }] });
  validateScreen(withElement({}));
  assert.throws(() => validateScreen(withElement({ kind: 'label' })), /conditional/i);
  assert.throws(() => validateScreen(withElement({ messageCondition: null })), /condition/i);
  assert.throws(() => validateScreen(withElement({ action: '' })), /condition/i);
  for (const change of [{ permission: 'Upper.Case' }, { permission: 'p'.repeat(129) }, { ifTrue: '\n' }, { ifFalse: 'A'.repeat(101) }, { extra: true }])
    assert.throws(() => validateScreen(withElement({ messageCondition: { ...e.messageCondition, ...change } })));
  const raw = JSON.stringify(withElement({}));
  assert.throws(() => parseScreen(raw.replace('"permission":"tideframe.vip"', '"permission":"tideframe.vip","permission":"other"')), /duplicate/i);
});
test('centers and sizes must be finite numbers in the documented range', () => {
  for (const [key, values] of Object.entries({ x: [-1.01, 1.01, NaN, '0'], y: [Infinity, -1.1], width: [0, -1, 2.1], height: [0, 3] }))
    for (const value of values) assert.throws(() => validateScreen(screen({ elements: [element({ [key]: value })] })), new RegExp(key));
  validateScreen(screen({ elements: [element({ x: -1, y: 1, width: 2, height: 2 })] }));
});
test('title and text accept only printable ASCII with exact limits', () => {
  for (const title of ['', 'a'.repeat(41), 'Hello\n', 'Caf\u00e9']) assert.throws(() => validateScreen(screen({ title })), /title/i);
  for (const text of ['a'.repeat(65), '\t', '\u007f', '\ud800']) assert.throws(() => validateScreen(screen({ elements: [element({ text })] })), /text/i);
  validateScreen(screen({ title: 'a'.repeat(40), elements: [element({ text: '' })] }));
});
test('element kind is exactly label or button', () => {
  for (const kind of ['', 'input', null]) assert.throws(() => validateScreen(screen({ elements: [element({ kind })] })), /kind/i);
});
test('actions accept only close, open:id and printable message payload up to 100', () => {
  for (const action of ['', 'close', 'open:profile', 'message:', `message:${'A'.repeat(100)}`]) validateScreen(screen({ elements: [element({ action })] }));
  for (const action of ['console:op', 'command:', 'open:../x', 'message:\n', `message:${'A'.repeat(101)}`, 0])
    assert.throws(() => validateScreen(screen({ elements: [element({ action })] })), /action/i);
});
test('legacy 24 element import cap is retained and promotion permits more labels', () => {
  const full = screen({ elements: Array.from({ length: 24 }, (_, i) => element({ id: `e${i}`, kind: 'label', action: '' })) });
  validateScreen(full);
  assert.equal(addElement(full, 'label').elements.length, 25);
  assert.throws(() => validateScreen({ ...full, elements: [...full.elements, element({ id: 'overflow' })] }), /24/i);
});
test('raw duplicate root, nested and escaped keys are rejected', () => {
  const json = JSON.stringify(screen());
  assert.throws(() => parseScreen(json.replace('"version":1', '"version":1,"version":1')), /duplicate/i);
  assert.throws(() => parseScreen(json.replace('"x":0', '"x":0,"\\u0078":0')), /duplicate/i);
});
test('malformed JSON, trailing content and excessive nesting are rejected', () => {
  for (const raw of ['null', '[]', '{}', '{"version":01}', JSON.stringify(screen()) + ' true', '['.repeat(30) + '0' + ']'.repeat(30)])
    assert.throws(() => parseScreen(raw));
});
test('128 MiB UTF-8 input limit permits photo layouts and rejects oversized input before parse', () => {
  assert.equal(parseScreen(' '.repeat(2097152) + JSON.stringify(screen())).id, 'menu');
  assert.throws(() => parseScreen(' '.repeat(134217729)), /128 MiB/i);
  assert.throws(() => parseScreen('é'.repeat(67108865)), /128 MiB/i);
});
test('moving or replacing an unknown element is rejected', () => {
  assert.throws(() => moveElement(screen(), 'missing', 0, 0), /unknown/i);
  assert.throws(() => replaceElement(screen(), 'missing', element()), /unknown/i);
});
