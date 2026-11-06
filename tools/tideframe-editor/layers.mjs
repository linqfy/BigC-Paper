// The layers panel (idea 37): elements listed from the top layer down, reordered by dragging, renamed with every
// reference following. Pure: no DOM; the page draws the list (app.mjs).
import { cloneScreen, orderedElements, validateScreen } from './model.mjs';

const ID = /^[a-z][a-z0-9_-]{0,31}$/;
// Actions whose "value" may name elements: "a, b" (show, hide, toggle) or one id (animation, pause, resume).
const LISTS_IN_VALUE = new Set(['show', 'hide', 'toggle', 'visibility']), ONE_IN_VALUE = new Set(['animation', 'animate', 'pause', 'resume']);

/** Elements from the top of the drawing to the bottom (what the game draws last comes first). */
export function layerOrder(screen) { return orderedElements(screen).map(entry => entry.element).reverse(); }

/**
 * {@code screen} with element {@code id} moved just above ({@code place} "above") or below {@code targetId} in the
 * drawing. When no layer number fits between its new neighbours, every element gets a new number in drawing order
 * (the order everyone sees stays the same, only the moved element changes place).
 */
export function moveLayer(screen, id, targetId, place = 'above') {
  if (id === targetId) return cloneScreen(screen);
  const order = layerOrder(screen).map(e => e.id);
  if (!order.includes(id) || !order.includes(targetId)) throw Error('Unknown element.');
  if (!['above', 'below'].includes(place)) throw Error('Place must be above or below.');
  const rest = order.filter(other => other !== id), at = rest.indexOf(targetId) + (place === 'below' ? 1 : 0);
  rest.splice(at, 0, id);
  const byId = new Map(screen.elements.map(e => [e.id, e])), next = cloneScreen(screen);
  // Top-down list: the neighbour drawn over the moved element is before it, the one under it after it.
  const over = rest[at - 1] === undefined ? null : byId.get(rest[at - 1]), under = rest[at + 1] === undefined ? null : byId.get(rest[at + 1]);
  const low = under ? under.layer + 1 : -128, high = over ? over.layer - 1 : 128;
  if (low <= high) {
    // A number strictly between the neighbours (just above the one under it at the top, just below the one over it at
    // the bottom): only the moved element changes.
    const layer = !over ? low : !under ? high : Math.round((low + high) / 2);
    next.elements = next.elements.map(e => e.id === id ? { ...e, layer } : e);
  } else {
    const count = rest.length, layerOf = new Map(rest.map((other, index) => [other, count - 1 - index - Math.floor(count / 2)]));
    next.elements = next.elements.map(e => ({ ...e, layer: layerOf.get(e.id) }));
  }
  return validateScreen(next);
}

/** {@code logic} (any shape) with element ids renamed by {@code map}, wherever page logic names an element. */
export function renameInLogic(logic, map) {
  if (Array.isArray(logic)) return logic.map(value => renameInLogic(value, map));
  if (!logic || typeof logic !== 'object') return logic;
  const out = {}, type = typeof logic.type === 'string' ? logic.type.toLowerCase() : null;
  for (const [key, value] of Object.entries(logic)) {
    if (['elements', 'show', 'hide'].includes(key) && Array.isArray(value)) out[key] = value.map(id => map.get(id) ?? id);
    else if (['elements', 'show', 'hide'].includes(key) && typeof value === 'string' && type) out[key] = value.split(',').map(id => map.get(id.trim()) ?? id.trim()).join(',');
    else if (['element', 'tooltip'].includes(key) && typeof value === 'string') out[key] = map.get(value) ?? value;
    else if (key === 'value' && typeof value === 'string' && LISTS_IN_VALUE.has(type) && !('elements' in logic) && !('element' in logic))
      out[key] = type === 'visibility' ? value : value.split(',').map(id => map.get(id.trim()) ?? id.trim()).join(',');
    else if (key === 'value' && typeof value === 'string' && ONE_IN_VALUE.has(type) && !('element' in logic)) out[key] = map.get(value.trim()) ?? value;
    else out[key] = renameInLogic(value, map);
  }
  return out;
}

/** {@code screen} with element {@code oldId} renamed {@code newId}, in the element and in every action, tooltip or list that names it. */
export function renameElement(screen, oldId, newId) {
  const name = String(newId ?? '').trim();
  if (!ID.test(name)) throw Error('Element IDs use a-z, 0-9, _ and -, start with a letter and have up to 32 characters.');
  if (!screen.elements.some(e => e.id === oldId)) throw Error('Unknown element.');
  if (name !== oldId && screen.elements.some(e => e.id === name)) throw Error(`Another element is already called ${name}.`);
  const map = new Map([[oldId, name]]), next = cloneScreen(screen);
  next.elements = next.elements.map(e => ({ ...e, ...(e.id === oldId ? { id: name } : {}), ...(e.logic ? { logic: renameInLogic(e.logic, map) } : {}) }));
  if (next.logic) next.logic = renameInLogic(next.logic, map);
  return validateScreen(next);
}
