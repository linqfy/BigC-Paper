// Original Tideframe authoring operations; visual positions use normalized -1..1 coordinates.
// These functions produce ordinary schema-v4 elements, not runtime containers or scrolling UI.
import { cloneScreen, cloneElement, validateScreen, promoteScreen, placeElements, validateScreenBudget } from './model.mjs';
import { encodeClipboard, deleteSelection, moveSelection } from './interaction.mjs';

const round = n => Math.round(n * 1e9) / 1e9;
function selected(value, ids) {
  validateScreen(value);
  if (!Array.isArray(ids)) throw Error('Selection must be an array of element IDs.');
  const wanted = new Set(ids);
  for (const id of wanted) if (!value.elements.some(e => e.id === id)) throw Error(`Unknown selected element: ${id}.`);
  return value.elements.filter(e => wanted.has(e.id));
}
function choice(value, allowed, label) {
  if (!allowed.includes(value)) throw Error(`Invalid ${label}; choose ${allowed.join(', ')}.`);
}
function finite(value, label, min = -Infinity, max = Infinity) {
  if (!Number.isFinite(value) || value < min || value > max) throw Error(`${label} must be finite and from ${min} to ${max}.`);
}
function bounds(elements) {
  return { left: Math.min(...elements.map(e => e.x - e.width / 2)), right: Math.max(...elements.map(e => e.x + e.width / 2)),
    top: Math.max(...elements.map(e => e.y + e.height / 2)), bottom: Math.min(...elements.map(e => e.y - e.height / 2)) };
}
function translate(e, x, y) {
  const dx = x - e.x, dy = y - e.y;
  // Positions and untouched axes retain their input precision. Quantization here
  // can turn valid touching button edges into overlapping action areas.
  return { ...e, x, y, ...(e.actionArea ? { actionArea: { ...e.actionArea, x: dx === 0 ? e.actionArea.x : e.actionArea.x + dx, y: dy === 0 ? e.actionArea.y : e.actionArea.y + dy } } : {}) };
}
function reposition(value, positions) {
  const next = cloneScreen(value);
  next.elements = next.elements.map(e => positions.has(e.id) ? translate(e, positions.get(e.id).x, positions.get(e.id).y) : e);
  return validateScreen(next);
}

export function selectSet(value, ids, mode = 'all') {
  selected(value, ids); choice(mode, ['all', 'none', 'invert'], 'selection mode');
  const wanted = new Set(ids);
  return mode === 'none' ? [] : value.elements.filter(e => mode === 'all' || !wanted.has(e.id)).map(e => e.id);
}
export function cutSelection(value, ids) {
  selected(value, ids);
  const clipboard = encodeClipboard(value, ids);
  return { screen: deleteSelection(value, ids), clipboard, ids: [] };
}
export function duplicateSelection(value, ids) {
  const next = promoteScreen(value), originals = selected(next, ids);
  if (!originals.length) throw Error('Select at least one element to duplicate.');
  if (next.elements.length + originals.length > 128) throw Error('Duplicate would exceed the 128 element limit.');
  // Cloning individual objects shares immutable PNG strings; no image JSON encode/decode round trip.
  const relocated = placeElements(next, originals.map(cloneElement));
  const used = new Set(next.elements.map(e => e.id)), newIds = [];
  for (const original of relocated) {
    let count = 1, id;
    do { const suffix = `_copy${count++}`; id = original.id.slice(0, 32 - suffix.length) + suffix; } while (used.has(id));
    used.add(id); newIds.push(id); next.elements.push({ ...original, id });
  }
  return { screen: validateScreenBudget(next), ids: newIds };
}
export function shiftLayers(value, ids, delta) {
  finite(delta, 'Layer delta', -256, 256);
  if (!Number.isInteger(delta)) throw Error('Layer delta must be an integer.');
  const next = promoteScreen(value), elements = selected(next, ids), wanted = new Set(ids);
  if (elements.some(e => e.layer + delta < -128 || e.layer + delta > 128)) throw Error('Layer change would exceed the -128..128 layer range.');
  next.elements = next.elements.map(e => wanted.has(e.id) ? { ...e, layer: e.layer + delta } : e);
  return validateScreen(next);
}
export function alignSelection(value, ids, alignment, { target = 'selection' } = {}) {
  choice(alignment, ['left', 'center', 'right', 'top', 'middle', 'bottom'], 'alignment');
  choice(target, ['selection', 'canvas'], 'alignment target');
  const elements = selected(value, ids);
  if (!elements.length || (elements.length === 1 && target === 'selection')) return cloneScreen(value);
  const b = target === 'canvas' ? { left: -1, right: 1, top: 1, bottom: -1 } : bounds(elements), positions = new Map();
  for (const e of elements) {
    let { x, y } = e;
    if (alignment === 'left') x = b.left + e.width / 2;
    if (alignment === 'center') x = (b.left + b.right) / 2;
    if (alignment === 'right') x = b.right - e.width / 2;
    if (alignment === 'top') y = b.top - e.height / 2;
    if (alignment === 'middle') y = (b.top + b.bottom) / 2;
    if (alignment === 'bottom') y = b.bottom + e.height / 2;
    positions.set(e.id, { x, y });
  }
  return reposition(value, positions);
}
export function distributeSelection(value, ids, axis = 'horizontal') {
  choice(axis, ['horizontal', 'vertical'], 'distribution axis');
  const elements = selected(value, ids);
  if (elements.length < 3) return cloneScreen(value);
  const horizontal = axis === 'horizontal', dimension = horizontal ? 'width' : 'height', b = bounds(elements);
  const ordered = elements.map((e, index) => ({ e, index })).sort((a, b) => (horizontal ? a.e.x - b.e.x : b.e.y - a.e.y) || a.index - b.index);
  const extent = horizontal ? b.right - b.left : b.top - b.bottom;
  const gap = (extent - elements.reduce((sum, e) => sum + e[dimension], 0)) / (elements.length - 1);
  let edge = horizontal ? b.left : b.top;
  const positions = new Map();
  for (const { e } of ordered) {
    positions.set(e.id, horizontal ? { x: edge + e.width / 2, y: e.y } : { x: e.x, y: edge - e.height / 2 });
    edge += (horizontal ? 1 : -1) * (e[dimension] + gap);
  }
  return reposition(value, positions);
}

function anchors(b, axis) {
  return axis === 'x' ? [{ name: 'left', value: b.left }, { name: 'center', value: (b.left + b.right) / 2 }, { name: 'right', value: b.right }]
    : [{ name: 'top', value: b.top }, { name: 'middle', value: (b.top + b.bottom) / 2 }, { name: 'bottom', value: b.bottom }];
}
export function snapSelection(value, ids, dx, dy, { grid = .05, gridEnabled = true, objects = true, canvas = true, threshold = .025 } = {}) {
  finite(dx, 'Horizontal movement'); finite(dy, 'Vertical movement');
  finite(grid, 'Grid size', Number.EPSILON, 2); finite(threshold, 'Snap threshold', 0, 2);
  for (const [name, enabled] of Object.entries({ gridEnabled, objects, canvas })) if (typeof enabled !== 'boolean') throw Error(`${name} must be boolean.`);
  const elements = selected(value, ids), wanted = new Set(ids);
  if (!elements.length) return { screen: cloneScreen(value), dx: 0, dy: 0, guides: [], snapped: false };
  const b = bounds(elements), offsets = { x: dx, y: dy }, guides = [];
  const targets = objects ? value.elements.filter(e => !wanted.has(e.id)).map(e => ({ bounds: bounds([e]), source: 'object', id: e.id })) : [];
  if (canvas) targets.push({ bounds: { left: -1, right: 1, top: 1, bottom: -1 }, source: 'canvas', id: null });
  // Zero threshold explicitly disables snapping, useful for temporarily free movement.
  if (threshold > 0) for (const axis of ['x', 'y']) {
    const candidates = [], moving = anchors(b, axis);
    for (const target of targets) for (const from of moving) for (const to of anchors(target.bounds, axis)) {
      const correction = to.value - (from.value + offsets[axis]);
      if (Math.abs(correction) <= threshold) candidates.push({ correction, guide: { axis, position: to.value, source: target.source, targetId: target.id, moving: from.name, target: to.name, distance: Math.abs(correction) }, priority: target.source === 'object' ? 0 : 1 });
    }
    if (gridEnabled) {
      const from = moving[1], destination = Math.round((from.value + offsets[axis]) / grid) * grid, correction = destination - (from.value + offsets[axis]);
      if (Math.abs(correction) <= threshold) candidates.push({ correction, guide: { axis, position: round(destination), source: 'grid', targetId: null, moving: from.name, target: from.name, distance: Math.abs(correction) }, priority: 2 });
    }
    candidates.sort((a, b) => Math.abs(a.correction) - Math.abs(b.correction) || a.priority - b.priority);
    if (candidates.length) { offsets[axis] += candidates[0].correction; guides.push(candidates[0].guide); }
  }
  const screen = moveSelection(value, ids, offsets.x, offsets.y), first = byId(screen, elements[0].id);
  const actual = { x: round(first.x - elements[0].x), y: round(first.y - elements[0].y) };
  // Clamping at a center bound may make a proposed guide unreachable; never draw a false guide.
  const reachable = guides.filter(g => Math.abs(actual[g.axis] - offsets[g.axis]) <= 1e-6);
  return { screen, dx: actual.x, dy: actual.y, guides: reachable, snapped: reachable.length > 0 };
}
function byId(value, id) { return value.elements.find(e => e.id === id); }

export function layoutSelection(value, ids, { axis = 'row', gap = .04, padding = .05, target = 'canvas', direction = 'forward', wrap = true,
  maxPerLine = 128, cellWidth = null, cellHeight = null, sort = 'file', descending = false, corner = 'top-left', align = 'center' } = {}) {
  choice(axis, ['row', 'column'], 'layout axis'); choice(target, ['canvas', 'selection'], 'layout target');
  choice(direction, ['forward', 'reverse'], 'layout direction'); choice(sort, ['file', 'id', 'name', 'layer'], 'layout sort');
  choice(corner, ['top-left', 'top-right', 'bottom-left', 'bottom-right'], 'layout corner'); choice(align, ['start', 'center', 'end'], 'layout alignment');
  finite(gap, 'Layout gap', 0, 2); finite(padding, 'Layout padding', 0, 1);
  if (!Number.isInteger(maxPerLine) || maxPerLine < 1 || maxPerLine > 128) throw Error('Layout maxPerLine must be an integer from 1 to 128.');
  if (typeof wrap !== 'boolean' || typeof descending !== 'boolean') throw Error('Layout wrap and descending must be boolean.');
  if (cellWidth !== null) finite(cellWidth, 'Layout cell width', Number.EPSILON, 2);
  if (cellHeight !== null) finite(cellHeight, 'Layout cell height', Number.EPSILON, 2);
  const elements = selected(value, ids);
  if (!elements.length) return cloneScreen(value);
  const b = target === 'canvas' ? { left: -1, right: 1, top: 1, bottom: -1 } : bounds(elements);
  const cw = cellWidth ?? Math.max(...elements.map(e => e.width)), ch = cellHeight ?? Math.max(...elements.map(e => e.height));
  if (elements.some(e => e.width > cw + 1e-9 || e.height > ch + 1e-9)) throw Error('Layout cell must fit every selected visual element.');
  const width = b.right - b.left - 2 * padding, height = b.top - b.bottom - 2 * padding;
  const horizontal = axis === 'row', primarySize = horizontal ? cw : ch, crossSize = horizontal ? ch : cw;
  const availablePrimary = horizontal ? width : height, availableCross = horizontal ? height : width;
  const capacity = Math.floor((availablePrimary + gap + 1e-9) / (primarySize + gap));
  const perLine = wrap ? Math.min(maxPerLine, capacity) : elements.length;
  const lines = perLine > 0 ? Math.ceil(elements.length / perLine) : Infinity;
  if (perLine < 1 || perLine * primarySize + (perLine - 1) * gap > availablePrimary + 1e-9 || lines * crossSize + (lines - 1) * gap > availableCross + 1e-9)
    throw Error('Selected elements do not fit within these layout bounds. Reduce cell sizes, gap or padding.');
  const compare = (a, b) => sort === 'id' ? a.e.id.localeCompare(b.e.id, 'en') : sort === 'name' ? a.e.text.localeCompare(b.e.text, 'en') : sort === 'layer' ? (a.e.layer ?? 0) - (b.e.layer ?? 0) : a.index - b.index;
  const ordered = elements.map((e, index) => ({ e, index })).sort((a, b) => (descending ? -1 : 1) * compare(a, b) || a.index - b.index);
  if (direction === 'reverse') ordered.reverse();
  const fromRight = corner.endsWith('right'), fromBottom = corner.startsWith('bottom'), positions = new Map();
  for (let i = 0; i < ordered.length; i++) {
    const e = ordered[i].e, primary = i % perLine, cross = Math.floor(i / perLine);
    const col = horizontal ? primary : cross, row = horizontal ? cross : primary;
    const cellX = fromRight ? b.right - padding - cw / 2 - col * (cw + gap) : b.left + padding + cw / 2 + col * (cw + gap);
    const cellY = fromBottom ? b.bottom + padding + ch / 2 + row * (ch + gap) : b.top - padding - ch / 2 - row * (ch + gap);
    let x = cellX, y = cellY;
    if (align !== 'center') {
      const sign = align === 'start' ? 1 : -1;
      if (horizontal) y += sign * (fromBottom ? -1 : 1) * (ch - e.height) / 2;
      else x += sign * (fromRight ? 1 : -1) * (cw - e.width) / 2;
    }
    positions.set(e.id, { x, y });
  }
  return reposition(value, positions);
}
