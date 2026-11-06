import test from 'node:test';
import assert from 'node:assert/strict';
import * as authoring from './authoring.mjs';
import { DEFAULT_STYLE, QUALITY_PRESETS, validateScreen } from './model.mjs';

const e = (id, x = 0, y = 0, extra = {}) => ({ id, text: id.toUpperCase(), x, y, width: .2, height: .1,
  kind: 'label', action: '', shape: 'rectangle', border: true, messageCondition: null, background: false,
  image: null, style: { ...DEFAULT_STYLE }, layer: 0, actionArea: null, ...extra });
const s = (...elements) => validateScreen({ version: 4, id: 'menu', title: 'MENU', quality: { ...QUALITY_PRESETS.low }, elements });
const close = (a, b) => assert.ok(Math.abs(a - b) < 1e-9, `${a} is not approximately ${b}`);
const byId = (screen, id) => screen.elements.find(e => e.id === id);
const button = (id, x, y, extra = {}) => e(id, x, y, { kind: 'button', action: 'close', ...extra });

test('authoring exports each planned operation', () => {
  for (const name of ['selectSet', 'cutSelection', 'duplicateSelection', 'shiftLayers', 'alignSelection', 'distributeSelection', 'snapSelection', 'layoutSelection'])
    assert.equal(typeof authoring[name], 'function', `${name} must exist`);
});

test('all, none and inverse selection use file order and reject unknown IDs', () => {
  const value = s(e('a'), e('b'), e('c'));
  assert.deepEqual(authoring.selectSet(value, ['b'], 'all'), ['a', 'b', 'c']);
  assert.deepEqual(authoring.selectSet(value, ['b'], 'none'), []);
  assert.deepEqual(authoring.selectSet(value, ['b', 'b'], 'invert'), ['a', 'c']);
  assert.throws(() => authoring.selectSet(value, ['missing'], 'invert'), /unknown/i);
  assert.throws(() => authoring.selectSet(value, [], 'maybe'), /selection mode/i);
});

test('cut produces a Tideframe clipboard and leaves the original document untouched', () => {
  const value = s(e('a', -.5), e('b', .5));
  const result = authoring.cutSelection(value, ['a']);
  assert.deepEqual(result.screen.elements.map(e => e.id), ['b']);
  assert.deepEqual(result.ids, []);
  const clipboard = JSON.parse(result.clipboard);
  assert.equal(clipboard.id, 'tideframe_clipboard');
  assert.equal(clipboard.elements[0].id, 'a');
  assert.equal(value.elements.length, 2);
  assert.throws(() => authoring.cutSelection(value, []), /select at least/i);
});

test('duplicate selects only copies, gives collision-free IDs, and finds free button space', () => {
  const value = s(button('a', -.5, 0, { actionArea: { x: -.45, y: .02, width: .25, height: .15 } }), e('a_copy1', .6));
  const result = authoring.duplicateSelection(value, ['a']);
  assert.deepEqual(result.ids, ['a_copy2']);
  const copy = byId(result.screen, result.ids[0]), original = byId(value, 'a');
  close(copy.actionArea.x - copy.x, original.actionArea.x - original.x);
  close(copy.actionArea.y - copy.y, original.actionArea.y - original.y);
  assert.equal(value.elements.length, 2);
  validateScreen(result.screen);
  assert.throws(() => authoring.duplicateSelection(value, []), /select at least/i);
});

test('duplicate preserves PNG strings without a JSON or structured clone of the image payload', () => {
  // Original one-pixel RGBA PNG fixture, decoded and validated by the shared schema.
  const image = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPgUbLwAwABWQC1RssqBwAAAABJRU5ErkJggg==';
  const value = s(e('a', 0, 0, { image }));
  const parse = JSON.parse, deepClone = globalThis.structuredClone;
  let result;
  try {
    JSON.parse = () => { throw Error('Image-bearing duplicate must not JSON parse its document.'); };
    globalThis.structuredClone = () => { throw Error('Image-bearing duplicate must use shallow object clones.'); };
    result = authoring.duplicateSelection(value, ['a']);
  } finally { JSON.parse = parse; globalThis.structuredClone = deepClone; }
  assert.equal(result.screen.elements[1].image, image);
  assert.notEqual(result.screen.elements[1].style, value.elements[0].style);
});

test('layer changes are atomic at limits and preserve array order and relative layer offsets', () => {
  const value = s(e('a', 0, 0, { layer: 127 }), e('b', 0, 0, { layer: 126 }), e('c', 0, 0, { layer: -128 }));
  const raised = authoring.shiftLayers(value, ['a', 'b'], 1);
  assert.deepEqual(raised.elements.map(e => [e.id, e.layer]), [['a', 128], ['b', 127], ['c', -128]]);
  assert.throws(() => authoring.shiftLayers(value, ['a', 'b'], 2), /layer/i);
  assert.throws(() => authoring.shiftLayers(value, ['c'], -1), /layer/i);
  assert.throws(() => authoring.shiftLayers(value, ['a'], .5), /integer/i);
  assert.deepEqual(value.elements.map(e => e.layer), [127, 126, -128]);
});

test('all six selection alignments use visual bounds and move independent action areas equally', () => {
  const value = s(e('a', -.5, .4, { width: .4, height: .2, actionArea: { x: -.4, y: .45, width: .1, height: .1 } }), e('b', .6, -.3, { width: .2, height: .4 }));
  const expected = { left: [-.5, -.6], center: [0, 0], right: [.5, .6], top: [.4, .3], middle: [0, 0], bottom: [-.4, -.3] };
  for (const [alignment, positions] of Object.entries(expected)) {
    const result = authoring.alignSelection(value, ['a', 'b'], alignment);
    const axis = ['left', 'center', 'right'].includes(alignment) ? 'x' : 'y';
    result.elements.forEach((item, index) => close(item[axis], positions[index]));
    close(result.elements[0].actionArea.x - result.elements[0].x, .1);
    close(result.elements[0].actionArea.y - result.elements[0].y, .05);
  }
  assert.deepEqual(authoring.alignSelection(value, [], 'left'), value);
  assert.deepEqual(authoring.alignSelection(value, ['a'], 'center'), value);
});

test('canvas alignments anchor to -1/+1 bounds and do not mutate shared appearance', () => {
  const value = s(e('a', .3, .1, { width: .4, height: .2 }));
  const expected = { left: -.8, center: 0, right: .8, top: .9, middle: 0, bottom: -.9 };
  for (const [alignment, position] of Object.entries(expected)) {
    const result = authoring.alignSelection(value, ['a'], alignment, { target: 'canvas' });
    close(result.elements[0][['left', 'center', 'right'].includes(alignment) ? 'x' : 'y'], position);
    assert.notEqual(result.elements[0].style, value.elements[0].style);
  }
  assert.throws(() => authoring.alignSelection(value, ['a'], 'invalid'), /alignment/i);
});

test('alignment rejects action-area overlaps atomically and rejects offsets outside the valid centers', () => {
  const value = s(button('a', -.5, 0), button('b', .5, 0));
  assert.throws(() => authoring.alignSelection(value, ['a', 'b'], 'center'), /overlap/i);
  assert.equal(value.elements[0].x, -.5);
  const offset = s(button('a', 0, 0, { actionArea: { x: -.9, y: 0, width: .1, height: .1 } }));
  assert.throws(() => authoring.alignSelection(offset, ['a'], 'left', { target: 'canvas' }), /action area x/i);
});

test('distribution preserves outer visual edges while equalizing gaps with unequal sizes', () => {
  const value = s(e('last', .7, -.7, { width: .4, height: .4 }), e('first', -.7, .7, { width: .2, height: .2 }),
    e('middle', .3, .4, { width: .3, height: .3, actionArea: { x: .35, y: .45, width: .1, height: .1 } }));
  const horizontal = authoring.distributeSelection(value, ['last', 'first', 'middle'], 'horizontal');
  close(byId(horizontal, 'middle').x, -.05);
  close(byId(horizontal, 'last').x, .7);
  close(byId(horizontal, 'first').x, -.7);
  close(byId(horizontal, 'middle').actionArea.x - byId(horizontal, 'middle').x, .05);
  const vertical = authoring.distributeSelection(value, ['last', 'first', 'middle'], 'vertical');
  close(byId(vertical, 'middle').y, .05);
  close(byId(vertical, 'middle').actionArea.y - byId(vertical, 'middle').y, .05);
  assert.deepEqual(authoring.distributeSelection(value, ['first', 'last']), value);
});

test('grid snapping rounds group center, returns visible guide coordinates and preserves action offsets', () => {
  const value = s(button('a', -.4, 0, { actionArea: { x: -.35, y: .03, width: .1, height: .1 } }));
  const result = authoring.snapSelection(value, ['a'], .123, .076, { grid: .1, objects: false, canvas: false, threshold: .03 });
  close(result.screen.elements[0].x, -.3); close(result.screen.elements[0].y, .1);
  close(result.screen.elements[0].actionArea.x - result.screen.elements[0].x, .05);
  assert.equal(result.guides.length, 2);
  assert.ok(result.guides.every(g => g.source === 'grid' && g.distance <= .03));
  close(result.dx, .1); close(result.dy, .1); assert.equal(result.snapped, true);
  const unchanged = authoring.snapSelection(value, ['a'], .123, .076, { gridEnabled: false, objects: false, canvas: false });
  close(unchanged.screen.elements[0].x, -.277); assert.equal(unchanged.snapped, false);
});

test('object snapping matches nearest group edge/center and ignores selected objects', () => {
  const value = s(e('a', -.4), e('target', .4, .4, { width: .4 }));
  const result = authoring.snapSelection(value, ['a'], .49, 0, { gridEnabled: false, canvas: false, threshold: .02 });
  close(byId(result.screen, 'a').x, .1);
  assert.equal(result.guides.find(g => g.axis === 'x').targetId, 'target');
  close(result.guides.find(g => g.axis === 'x').position, .2);
  assert.equal(result.guides.find(g => g.axis === 'x').moving, 'right');
  assert.equal(result.guides.find(g => g.axis === 'x').source, 'object');
  assert.equal(authoring.snapSelection(value, ['a', 'target'], 0, 0, { gridEnabled: false, canvas: false }).guides.length, 0);
});

test('snap threshold is bounded, invalid settings reject, and button overlap remains forbidden', () => {
  const value = s(button('a', -.4, 0), button('b', .4, 0));
  assert.equal(authoring.snapSelection(value, ['a'], .1, .1, { threshold: 0, objects: false, canvas: false }).snapped, false);
  for (const options of [{ grid: 0 }, { grid: NaN }, { threshold: -1 }, { threshold: Infinity }])
    assert.throws(() => authoring.snapSelection(value, ['a'], 0, 0, options), /grid|threshold/i);
  assert.throws(() => authoring.snapSelection(value, ['a'], .8, 0), /overlap/i);
});

test('row layout applies padding, gap and wrap with static positions in file order', () => {
  const value = s(e('c'), e('a'), e('b'));
  const result = authoring.layoutSelection(value, ['a', 'b', 'c'], { axis: 'row', target: 'canvas', padding: .1, gap: .1, maxPerLine: 2, cellWidth: .4, cellHeight: .2 });
  close(byId(result, 'c').x, -.7); close(byId(result, 'c').y, .8);
  close(byId(result, 'a').x, -.2); close(byId(result, 'a').y, .8);
  close(byId(result, 'b').x, -.7); close(byId(result, 'b').y, .5);
  assert.deepEqual(result.elements.map(e => e.id), ['c', 'a', 'b']);
  assert.equal(value.elements[0].x, 0);
});

test('column layout reverses sorted IDs and starts at the bottom-right corner', () => {
  const value = s(e('c'), e('a'), e('b'));
  const result = authoring.layoutSelection(value, ['a', 'b', 'c'], { axis: 'column', sort: 'id', descending: true,
    corner: 'bottom-right', direction: 'reverse', padding: .1, gap: .1, maxPerLine: 2, cellWidth: .4, cellHeight: .2 });
  // Descending c,b,a reverses to a,b,c; bottom-right advances upwards, then left.
  close(byId(result, 'a').x, .7); close(byId(result, 'a').y, -.8);
  close(byId(result, 'b').x, .7); close(byId(result, 'b').y, -.5);
  close(byId(result, 'c').x, .2); close(byId(result, 'c').y, -.8);
});

test('layout sorts by name and layer stably without changing paint order', () => {
  const value = s(e('a', 0, 0, { text: 'ZED', layer: 2 }), e('b', 0, 0, { text: 'ALPHA', layer: 1 }), e('c', 0, 0, { text: 'ALPHA', layer: 1 }));
  for (const sort of ['name', 'layer']) {
    const result = authoring.layoutSelection(value, ['a', 'b', 'c'], { sort, padding: .1, gap: .1 });
    assert.ok(byId(result, 'b').x < byId(result, 'c').x && byId(result, 'c').x < byId(result, 'a').x);
    assert.deepEqual(result.elements.map(e => e.id), ['a', 'b', 'c']);
  }
});

test('layout cross-axis start/center/end aligns visuals inside custom cells and moves action areas', () => {
  const value = s(button('a', 0, 0, { actionArea: { x: .05, y: .03, width: .1, height: .08 } }));
  const expected = { start: .85, center: .7, end: .55 };
  for (const [align, y] of Object.entries(expected)) {
    const result = authoring.layoutSelection(value, ['a'], { align, padding: .1, cellWidth: .4, cellHeight: .4 });
    close(result.elements[0].y, y);
    close(result.elements[0].actionArea.x - result.elements[0].x, .05);
    close(result.elements[0].actionArea.y - result.elements[0].y, .03);
  }
});

test('layout wraps automatically by available bounds and rejects clipping and nonsensical settings', () => {
  const value = s(...Array.from({ length: 4 }, (_, i) => e(`e${i}`)));
  const wrapped = authoring.layoutSelection(value, value.elements.map(e => e.id), { cellWidth: .8, cellHeight: .2, gap: .1, padding: .1 });
  close(wrapped.elements[0].y, wrapped.elements[1].y);
  close(wrapped.elements[2].y, wrapped.elements[0].y - .3);
  assert.throws(() => authoring.layoutSelection(value, value.elements.map(e => e.id), { wrap: false, cellWidth: .8 }), /fit|bounds/i);
  assert.throws(() => authoring.layoutSelection(value, value.elements.map(e => e.id), { cellWidth: .1 }), /cell/i);
  for (const options of [{ gap: -1 }, { padding: Infinity }, { maxPerLine: 0 }, { direction: 'sideways' }, { sort: 'bad' }, { axis: 'diagonal' }, { align: 'bad' }, { corner: 'center' }])
    assert.throws(() => authoring.layoutSelection(value, [], options));
  assert.deepEqual(authoring.layoutSelection(value, []), value);
});

test('selection-bound layout respects its original frame and never publishes overlapping button actions', () => {
  const value = s(e('a', -.6, .5, { width: .4, height: .4 }), e('b', .6, -.5, { width: .4, height: .4 }));
  const result = authoring.layoutSelection(value, ['a', 'b'], { target: 'selection', padding: .1, gap: .1 });
  close(byId(result, 'a').x, -.5); close(byId(result, 'a').y, .4);
  close(byId(result, 'b').x, 0); close(byId(result, 'b').y, .4);
  const actions = s(button('a', -.5, 0, { actionArea: { x: -.5, y: 0, width: .7, height: .4 } }), button('b', .5, 0, { actionArea: { x: .5, y: 0, width: .7, height: .4 } }));
  assert.throws(() => authoring.layoutSelection(actions, ['a', 'b'], { padding: .1, gap: 0 }), /overlap/i);
  assert.equal(actions.elements[0].x, -.5);
});

test('a single selection-bound alignment is an exact no-op even for sub-nanounit imported coordinates', () => {
  const value = s(e('a', .123456789123, -.234567891234, { width: .345678912345, height: .456789123456 }));
  for (const alignment of ['left', 'center', 'right', 'top', 'middle', 'bottom'])
    assert.deepEqual(authoring.alignSelection(value, ['a'], alignment), value);
});

test('duplicate enforces the 128 element budget and truncates long copied IDs safely', () => {
  const longId = 'a'.repeat(32), value = s(e(longId));
  const result = authoring.duplicateSelection(value, [longId]);
  assert.equal(result.ids[0].length, 32); assert.ok(result.ids[0].endsWith('_copy1'));
  const full = s(...Array.from({ length: 128 }, (_, i) => e(`e${i}`)));
  assert.throws(() => authoring.duplicateSelection(full, ['e0']), /128/i);
  assert.equal(full.elements.length, 128);
});

test('guides are not reported when a bound prevents the proposed snapped destination', () => {
  const value = s(e('a', .9, 0, { actionArea: { x: 1, y: 0, width: .1, height: .1 } }));
  const result = authoring.snapSelection(value, ['a'], .25, .033, { grid: .1, objects: false, canvas: false, threshold: .1 });
  close(result.dx, 0); assert.equal(result.guides.some(g => g.axis === 'x'), false);
  close(result.screen.elements[0].actionArea.x, 1);
});

test('canvas snap includes canvas source and center coordinates', () => {
  const value = s(e('a', -.2, .3));
  const result = authoring.snapSelection(value, ['a'], .19, -.29, { gridEnabled: false, objects: false, threshold: .02 });
  close(result.screen.elements[0].x, 0); close(result.screen.elements[0].y, 0);
  assert.equal(result.guides.length, 2);
  assert.ok(result.guides.every(g => g.source === 'canvas' && g.position === 0 && g.targetId === null));
});

test('every operation rejects unknown selection IDs before publication', () => {
  const value = s(e('a'));
  for (const run of [() => authoring.cutSelection(value, ['missing']), () => authoring.duplicateSelection(value, ['missing']),
    () => authoring.shiftLayers(value, ['missing'], 1), () => authoring.alignSelection(value, ['missing'], 'left'),
    () => authoring.distributeSelection(value, ['missing']), () => authoring.snapSelection(value, ['missing'], 0, 0),
    () => authoring.layoutSelection(value, ['missing'])]) assert.throws(run, /unknown/i);
  assert.deepEqual(authoring.snapSelection(value, [], 0, 0), { screen: value, dx: 0, dy: 0, guides: [], snapped: false });
});

test('single column cross-axis alignment follows the chosen starting corner', () => {
  const value = s(e('a'));
  const left = authoring.layoutSelection(value, ['a'], { axis: 'column', cellWidth: .4, cellHeight: .2, padding: .1, align: 'start' });
  close(left.elements[0].x, -.8);
  const right = authoring.layoutSelection(value, ['a'], { axis: 'column', cellWidth: .4, cellHeight: .2, padding: .1, corner: 'bottom-right', align: 'start' });
  close(right.elements[0].x, .8); close(right.elements[0].y, -.8);
});

test('alignment preserves untouched axes and exact touching button edges at high precision', () => {
  const height=.2000000001;
  const value=s(button('a',-.6,0,{height}),button('b',.6,height,{height}));
  const aligned=authoring.alignSelection(value,['a','b'],'left');
  assert.equal(aligned.elements[0].x,-.6); assert.equal(aligned.elements[1].x,-.6); assert.equal(aligned.elements[0].y,0); assert.equal(aligned.elements[1].y,height);
  validateScreen(aligned); assert.equal(value.elements[1].x,.6);
});
test('alignment never quantizes unchanged independent action area coordinates', () => {
  const value=s(e('a',-.6,.123456789123,{actionArea:{x:-.5,y:.234567891234,width:.1,height:.1}}),e('b',.6,.123456789123));
  const aligned=authoring.alignSelection(value,['a','b'],'left'); assert.equal(aligned.elements[0].y,.123456789123); assert.equal(aligned.elements[0].actionArea.y,.234567891234);
  assert.equal(aligned.elements[0].x,-.6); assert.equal(aligned.elements[0].actionArea.x,-.5);
});
