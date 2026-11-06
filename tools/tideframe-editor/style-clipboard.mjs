// Copy and paste an element's look (idea 32): colours, border, shape, shadow, text style, effects and blurs. Never the
// text, position, size, image, logic, layer or the 9-slice border (that belongs to its own picture). Pure: no DOM.
import { cloneScreen, upgradeLogic, validateScreen } from './model.mjs';

const OWN_PICTURE = new Set(['slice', 'sliceScale']);

/** The look of {@code element}, ready to paste on others. */
export function copyStyle(element) {
  if (!element) throw Error('Select an element to copy its style.');
  const style = {};
  for (const [key, value] of Object.entries(element.style ?? {})) if (!OWN_PICTURE.has(key)) style[key] = value;
  return Object.freeze({ from: element.id, style: Object.freeze(style), background: element.background, border: element.border, shape: element.shape });
}

/** {@code screen} with {@code copied} applied to the elements {@code ids}; a page on an older schema is upgraded when the look needs it. */
export function pasteStyle(screen, ids, copied) {
  if (!copied) throw Error('Copy a style first.');
  const selected = new Set(ids);
  if (!selected.size) throw Error('Select at least one visible, unlocked element.');
  const sample = screen.elements.find(e => selected.has(e.id));
  // Fields the page's schema does not know yet (blurs, text case...) upgrade it, as adding them by hand would.
  const needsUpgrade = sample && Object.keys(copied.style).some(key => !(key in (sample.style ?? {})));
  const next = needsUpgrade && screen.version < 9 ? upgradeLogic(screen) : cloneScreen(screen);
  next.elements = next.elements.map(e => selected.has(e.id)
    ? { ...e, background: copied.background, border: copied.border, shape: copied.shape, style: { ...e.style, ...copied.style } } : e);
  return validateScreen(next);
}
