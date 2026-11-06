// Editing several elements at once from the inspector (idea 19): appearance, shape, size and layer go to every
// selected element; ids, texts, positions, kinds and actions stay per element.
import { cloneElement } from './model.mjs';

/** Element fields the inspector applies to the whole selection (besides every style field). */
export const SHARED_FIELDS = Object.freeze(new Set(['shape', 'border', 'background', 'width', 'height', 'layer']));

/** True when a change of {@code field} (or of a style field) goes to the whole selection. */
export function sharedChange({ field = null, style = null } = {}) { return Boolean(style) || SHARED_FIELDS.has(field); }

/** {@code screen} with {@code build(element)} merged into each element at {@code indices}. */
export function editSelection(screen, indices, build) {
  const wanted = new Set(indices);
  if (!wanted.size || [...wanted].some(index => !Number.isInteger(index) || !screen.elements[index])) throw Error('Unknown selected row.');
  return { ...screen, elements: screen.elements.map((e, i) => wanted.has(i) ? { ...e, ...cloneElement(build(e)) } : e) };
}
