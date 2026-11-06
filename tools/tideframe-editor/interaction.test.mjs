import test from 'node:test';
import assert from 'node:assert/strict';
import { promoteScreen, validateScreen } from './model.mjs';
import { selectIds, selectionIds, editElementDraft, moveSelection, resizeSelection, deleteSelection, pointerIntent, shortcutIntent, applyPointerGesture, encodeClipboard, pasteClipboard, copyClipboard, readClipboard, copyEventPayload, pasteEventPayload } from './interaction.mjs';
import { upgradeAppearance } from './model.mjs';

const element = (id, x = 0, y = 0) => ({ id, text: id.toUpperCase(), x, y, width: .4, height: .2, kind: 'button', action: '', shape: 'rectangle', border: true, messageCondition: null });
const screen = () => ({ version: 2, id: 'menu', title: 'TIDEFRAME', elements: [element('first', -.3), element('second', .3), element('third', 0, -.5)] });
test('Shift toggles multi-selection; dragging a selected member keeps the group', () => {
  assert.deepEqual(selectIds(['first'], 'second', true), ['first', 'second']);
  assert.deepEqual(selectIds(['first', 'second'], 'first', true), ['second']);
  assert.deepEqual(selectIds(['first', 'second'], 'first', false, true), ['first', 'second']);
  assert.deepEqual(selectIds(['first', 'second'], 'first'), ['first']);
});
test('selected row identities survive a temporary duplicate ID then a valid rename', () => {
  const original = screen(), indices = [0, 1];
  let draft = editElementDraft(original, 1, { id: 'first' });
  assert.deepEqual(selectionIds(draft, indices), ['first', 'first']);
  draft = editElementDraft(draft, 1, { id: 'renamed' }); validateScreen(draft);
  assert.deepEqual(selectionIds(draft, indices), ['first', 'renamed']);
  const pasted = pasteClipboard(draft, encodeClipboard(draft, selectionIds(draft, indices)));
  assert.equal(pasted.ids.length, 2); assert.equal(original.elements[1].id, 'second');
});
test('a single selected row does not gain a collided row during ID editing', () => {
  let draft = editElementDraft(screen(), 1, { id: 'first' });
  draft = editElementDraft(draft, 1, { id: 'renamed' });
  assert.deepEqual(selectionIds(draft, [1]), ['renamed']);
});
test('only Caps Lock enables mouse resize and Shift selection retains priority', () => {
  assert.equal(pointerIntent({ button: 0 }), 'move');
  assert.equal(pointerIntent({ button: 0, ctrlKey: true }), 'move');
  assert.equal(pointerIntent({ button: 0, resizeMode: true }), 'move');
  assert.equal(pointerIntent({ button: 0, capsLock: true }), 'resize');
  assert.equal(pointerIntent({ button: 0, shiftKey: true, capsLock: true, ctrlKey: true }), 'select');
  assert.equal(pointerIntent({ button: 2 }), null);
});
test('Ctrl clipboard shortcuts and Delete preserve native editing inside inputs', () => {
  assert.equal(shortcutIntent({ key: 'c', ctrlKey: true }), 'copy');
  assert.equal(shortcutIntent({ key: 'V', ctrlKey: true }), 'paste');
  assert.equal(shortcutIntent({ key: 'Delete' }), 'delete');
  for (const key of ['c', 'v', 'Delete']) assert.equal(shortcutIntent({ key, ctrlKey: true, editable: true }), null);
  assert.equal(shortcutIntent({ key: 'c' }), null);
  assert.equal(shortcutIntent({ key: 'v', ctrlKey: true, altKey: true }), null);
});
test('group movement preserves spacing and clamps the entire group at the plane boundary', () => {
  const original = screen();
  const moved = moveSelection(original, ['first', 'second'], 1, -.1);
  assert.equal(moved.elements[0].x, .4); assert.equal(moved.elements[1].x, 1);
  assert.equal(moved.elements[0].y, -.1); assert.deepEqual(moved.elements[2], original.elements[2]);
  assert.equal(original.elements[0].x, -.3);
});
test('group resize fixes centers and clamps dimensions atomically', () => {
  const original = screen();
  const resized = resizeSelection(original, ['first', 'second'], 4, -.5);
  for (const e of resized.elements.slice(0, 2)) { assert.equal(e.width, 2); assert.equal(e.height, .01); }
  assert.equal(resized.elements[0].x, -.3); assert.equal(resized.elements[1].x, .3);
  assert.deepEqual(resized.elements[2], original.elements[2]); validateScreen(resized);
});
test('zero gestures preserve valid high-precision centers and tiny imported dimensions', () => {
  const original = screen(); original.elements[0].x = .333333333333; original.elements[0].width = 1e-10;
  assert.deepEqual(moveSelection(original, ['first'], 0, 0), original);
  assert.deepEqual(resizeSelection(original, ['first'], 0, 0), original);
});
test('pointer movement converts screen pixels to normalized coordinates and center-fixed size', () => {
  const base = screen(), rectangle = { width: 100, height: 200 };
  const moved = applyPointerGesture(base, ['first'], 'move', { x: 20, y: 20 }, { x: 30, y: 40 }, rectangle);
  assert.equal(moved.elements[0].x, -.1); assert.equal(moved.elements[0].y, -.2);
  const resized = applyPointerGesture(base, ['first'], 'resize', { x: 20, y: 20 }, { x: 30, y: 40 }, rectangle);
  assert.equal(resized.elements[0].width, .8); assert.equal(resized.elements[0].height, .6);
  assert.equal(resized.elements[0].x, -.3); assert.equal(resized.elements[0].y, 0);
});
test('bad group coordinates or unknown IDs reject the whole edit without mutation', () => {
  const original = screen(), before = JSON.stringify(original);
  assert.throws(() => moveSelection(original, ['first', 'missing'], .1, 0), /unknown/i);
  assert.throws(() => resizeSelection(original, ['first'], NaN, 0), /finite/i);
  assert.throws(() => applyPointerGesture(original, ['first'], 'move', { x: 0, y: 0 }, { x: 1, y: 1 }, { width: 0, height: 10 }));
  assert.equal(JSON.stringify(original), before);
});
test('group deletion removes only selected elements', () => {
  const original = screen(), next = deleteSelection(original, ['first', 'second']);
  assert.deepEqual(next.elements.map(e => e.id), ['third']); assert.equal(original.elements.length, 3);
});
test('copy and paste deep-copy all selected appearance and condition fields with unique IDs and offset', () => {
  const original = screen();
  original.elements[0] = { ...original.elements[0], shape: 'ellipse', border: false, action: 'conditional-message', messageCondition: { permission: 'example.vip', ifTrue: 'YES', ifFalse: 'NO' } };
  const raw = encodeClipboard(original, ['first', 'second']);
  const pasted = pasteClipboard(original, raw);
  assert.equal(pasted.screen.elements.length, 5); assert.equal(pasted.ids.length, 2);
  assert.deepEqual(pasted.screen.elements.slice(3).map(e => e.id), ['first_copy1', 'second_copy1']);
  assert.equal(pasted.screen.elements[3].x, -.26); assert.equal(pasted.screen.elements[3].y, -.2);
  validateScreen(pasted.screen);
  assert.equal(pasted.screen.elements[3].shape, 'ellipse'); assert.equal(pasted.screen.elements[3].border, false);
  pasted.screen.elements[3].messageCondition.ifTrue = 'EDITED'; assert.equal(original.elements[0].messageCondition.ifTrue, 'YES');
  const again = pasteClipboard(pasted.screen, raw); assert.equal(again.screen.elements[5].id, 'first_copy2');
});
test('paste preserves group spacing when offset meets a boundary and long IDs stay valid', () => {
  const original = screen(); original.elements[0].id = 'a'.repeat(32); original.elements[0].x = 1;
  const pasted = pasteClipboard(original, encodeClipboard(original, [original.elements[0].id, 'second']));
  validateScreen(pasted.screen); assert.ok(pasted.ids[0].length <= 32);
  assert.equal(pasted.screen.elements[3].x, 1); assert.equal(pasted.screen.elements[4].x, .3);
});
test('oversized or foreign clipboard and duplicate raw JSON keys are rejected', () => {
  const original = screen(), raw = encodeClipboard(original, ['first']);
  assert.throws(() => pasteClipboard(original, ' '.repeat(134217729)), /128 MiB/i);
  assert.throws(() => pasteClipboard(original, JSON.stringify(original)), /clipboard/i);
  assert.throws(() => pasteClipboard(original, raw.replace('"version": 4', '"version": 4,"version":4')), /duplicate/i);
  assert.throws(() => encodeClipboard(original, []), /select/i);
});
test('paste beyond 128 elements fails atomically', () => {
  const original = promoteScreen(screen()), raw = encodeClipboard(original, ['first', 'second']);
  const template = { ...original.elements[0], kind: 'label', action: '', messageCondition: null };
  original.elements = Array.from({ length: 127 }, (_, i) => ({ ...template, id: `e${i}` }));
  const before = JSON.stringify(original);
  assert.throws(() => pasteClipboard(original, raw), /128/i); assert.equal(JSON.stringify(original), before);
});
test('system clipboard write/read uses user-triggered adapter and reports permission fallback', async () => {
  let stored = ''; const adapter = { writeText: async raw => { stored = raw; }, readText: async () => stored };
  assert.deepEqual(await copyClipboard(adapter, 'own payload'), { raw: 'own payload', system: true });
  assert.deepEqual(await readClipboard(adapter, ''), { raw: 'own payload', system: true });
  const denied = { writeText: async () => { throw Error('denied'); }, readText: async () => { throw Error('denied'); } };
  assert.deepEqual(await copyClipboard(denied, 'local'), { raw: 'local', system: false });
  assert.deepEqual(await readClipboard(denied, 'local'), { raw: 'local', system: false });
  await assert.rejects(readClipboard(denied, ''), /clipboard/i);
});
test('announced internal copy wins over a readable stale system clipboard after write denial', async () => {
  let readCount = 0;
  const adapter = { writeText: async () => { throw Error('denied'); }, readText: async () => { readCount++; return 'stale system data'; } };
  const copy = await copyClipboard(adapter, 'new internal data');
  assert.deepEqual(await readClipboard(adapter, copy.raw, !copy.system), { raw: 'new internal data', system: false });
  assert.equal(readCount, 0);
});
test('native browser clipboard events carry editor data and preserve editable targets', () => {
  const writes = [], event = { clipboardData: { setData: (...args) => writes.push(args), getData: () => 'system data' }, preventDefault() { this.prevented = true; } };
  assert.equal(copyEventPayload(event, 'own data', true), false); assert.equal(event.prevented, undefined); assert.equal(writes.length, 0);
  assert.equal(copyEventPayload(event, 'own data', false), true); assert.equal(event.prevented, true); assert.deepEqual(writes, [['text/plain', 'own data']]);
  event.prevented = false;
  assert.equal(pasteEventPayload(event, { editable: true }), null); assert.equal(event.prevented, false);
  assert.deepEqual(pasteEventPayload(event, {}), { raw: 'system data', system: true }); assert.equal(event.prevented, true);
  assert.deepEqual(pasteEventPayload(event, { internal: 'new internal', preferInternal: true }), { raw: 'new internal', system: false });
});
test('clipboard preserves schema5 appearance and promotes an older destination atomically', () => {
  const original=upgradeAppearance(screen());
  original.elements[0].style.rotation=17;
  original.elements[0].style.hoverColor='#abcdef';
  original.elements[0].text='FIRST\nSECOND';
  const raw=encodeClipboard(original,['first']);
  assert.equal(JSON.parse(raw).version,5);
  const older=promoteScreen(screen()), before=JSON.stringify(older);
  const pasted=pasteClipboard(older,raw);
  assert.equal(pasted.screen.version,5);
  assert.equal(pasted.screen.elements.at(-1).style.rotation,17);
  assert.equal(pasted.screen.elements.at(-1).text,'FIRST\nSECOND');
  validateScreen(pasted.screen);
  assert.equal(JSON.stringify(older),before);
});
test('older clipboard elements receive full defaults when pasted into schema5', () => {
  const older=screen(), target=upgradeAppearance(screen());
  target.elements[0].style.radius=.2;
  const pasted=pasteClipboard(target,encodeClipboard(older,['second']));
  assert.equal(pasted.screen.version,5);
  assert.equal(pasted.screen.elements.at(-1).style.radius,.08);
  assert.equal(pasted.screen.elements.at(-1).style.rotation,0);
  assert.equal(pasted.screen.elements[0].style.radius,.2);
  validateScreen(pasted.screen);
});
