import { promoteScreen, upgradeAppearance, upgradePresentation, upgradeTextStyle, upgradeGradient, validateScreen, parseScreen, serializeScreen, cloneScreen, cloneElement, placeElements, validateScreenBudget, upgradeLogic } from './model.mjs';
import { validateScreenImages } from './images.mjs';

const round = n => Math.round(n * 1000000) / 1000000;
const bound = (n, min, max) => Math.min(max, Math.max(min, n));
// One monotonic gate coordinates all asynchronous image/import/paste publication.
// Invalidated completions are silent, including stale failures and UI status.
export function createPublicationController() {
  let generation = 0;
  return {
    invalidate() { generation++; },
    async run(prepare, { apply, report, pending = 'Checking images...', success = () => 'Ready.', failure = 'Publication rejected' }) {
      const request = ++generation;
      report(pending);
      try {
        const result = await prepare();
        if (request !== generation) return false;
        if (apply(result) === false) return false;
        if (request === generation) report(success(result));
        return true;
      } catch (error) {
        if (request === generation) report(`${failure}: ${error.message}`, true);
        return false;
      }
    }
  };
}
// Draft selection uses row identities, independent of temporarily invalid/duplicate editable IDs.
export function selectionIds(value, indices) {
  return [...new Set(indices)].map(index => {
    if (!Number.isInteger(index) || !value.elements[index]) throw Error('Unknown selected row.');
    return value.elements[index].id;
  });
}
export function editElementDraft(value, index, change) {
  if (!Number.isInteger(index) || !value.elements[index]) throw Error('Unknown active row.');
  return { ...value, elements: value.elements.map((e, i) => i === index ? { ...e, ...cloneElement(change) } : e) };
}
export function copyEventPayload(event, raw, editable = false) {
  if (editable || !event.clipboardData?.setData) return false;
  try { event.clipboardData.setData('text/plain', raw); event.preventDefault(); return true; }
  catch { return false; }
}
export function pasteEventPayload(event, { editable = false, internal = '', preferInternal = false } = {}) {
  if (editable) return null;
  if (preferInternal && internal) { event.preventDefault(); return { raw: internal, system: false }; }
  if (!event.clipboardData?.getData) return null;
  const raw = event.clipboardData.getData('text/plain'); event.preventDefault();
  return { raw, system: true };
}
function finite(...values) { if (!values.every(Number.isFinite)) throw Error('Gesture values must be finite numbers.'); }
function selectedElements(value, ids) {
  validateScreen(value);
  const wanted = new Set(ids);
  for (const id of wanted) if (!value.elements.some(e => e.id === id)) throw Error(`Unknown selected element: ${id}.`);
  return value.elements.filter(e => wanted.has(e.id));
}
export function selectIds(current, id, toggle = false, preserveGroup = false) {
  const unique = [...new Set(current)];
  if (toggle) return unique.includes(id) ? unique.filter(item => item !== id) : [...unique, id];
  return preserveGroup && unique.includes(id) ? unique : [id];
}
export function capsLockState(event) {
  try { return event?.getModifierState?.('CapsLock') === true; } catch { return false; }
}
export function guardPointerGesture(drag, capsLock) {
  return drag?.mode === 'resize' && !capsLock ? null : drag;
}
export function pointerIntent({ button, shiftKey = false, capsLock = false }) {
  if (button !== 0) return null;
  return shiftKey ? 'select' : capsLock ? 'resize' : 'move';
}
export function shortcutIntent({ key = '', ctrlKey = false, metaKey = false, altKey = false, editable = false }) {
  if (editable || altKey) return null;
  if (ctrlKey || metaKey) {
    if (key.toLowerCase() === 'c') return 'copy';
    if (key.toLowerCase() === 'v') return 'paste';
  }
  return key === 'Delete' && !ctrlKey && !metaKey ? 'delete' : null;
}
export function moveSelection(value, ids, dx, dy) {
  finite(dx, dy);
  const elements = selectedElements(value, ids), wanted = new Set(ids), next = cloneScreen(value);
  if (!elements.length) return next;
  const centers = elements.flatMap(e => e.actionArea ? [e, e.actionArea] : [e]);
  dx = bound(dx, Math.max(...centers.map(e => -1 - e.x)), Math.min(...centers.map(e => 1 - e.x)));
  dy = bound(dy, Math.max(...centers.map(e => -1 - e.y)), Math.min(...centers.map(e => 1 - e.y)));
  next.elements = next.elements.map(e => wanted.has(e.id) ? { ...e, x: dx === 0 ? e.x : round(e.x + dx), y: dy === 0 ? e.y : round(e.y + dy), ...(e.actionArea ? { actionArea: { ...e.actionArea, x: dx === 0 ? e.actionArea.x : round(e.actionArea.x + dx), y: dy === 0 ? e.actionArea.y : round(e.actionArea.y + dy) } } : {}) } : e);
  return validateScreen(next);
}
export function resizeSelection(value, ids, dw, dh) {
  finite(dw, dh);
  const elements = selectedElements(value, ids), wanted = new Set(ids), next = cloneScreen(value);
  if (!elements.length) return next;
  // Preserve imported sub-0.01 sizes; normal mouse edits stop at 0.01. Zero is always a legal delta.
  dw = bound(dw, Math.max(...elements.map(e => Math.min(.01, e.width) - e.width)), Math.min(...elements.map(e => 2 - e.width)));
  dh = bound(dh, Math.max(...elements.map(e => Math.min(.01, e.height) - e.height)), Math.min(...elements.map(e => 2 - e.height)));
  const positive = n => round(n) > 0 ? round(n) : n;
  next.elements = next.elements.map(e => wanted.has(e.id) ? { ...e, width: dw === 0 ? e.width : positive(e.width + dw), height: dh === 0 ? e.height : positive(e.height + dh) } : e);
  return validateScreen(next);
}
export function applyPointerGesture(value, ids, mode, start, current, rect) {
  finite(start.x, start.y, current.x, current.y, rect.width, rect.height);
  if (rect.width <= 0 || rect.height <= 0) throw Error('Preview dimensions must be positive.');
  if (mode === 'move') return moveSelection(value, ids, (current.x - start.x) / rect.width * 2, -(current.y - start.y) / rect.height * 2);
  if (mode === 'resize') return resizeSelection(value, ids, (current.x - start.x) / rect.width * 4, (current.y - start.y) / rect.height * 4);
  throw Error('Unknown pointer gesture.');
}
export function deleteSelection(value, ids) {
  selectedElements(value, ids);
  const wanted = new Set(ids), next = cloneScreen(value);
  next.elements = next.elements.filter(e => !wanted.has(e.id));
  return validateScreen(next);
}
export function encodeClipboard(value, ids) {
  const upgraded = promoteScreen(value), elements = selectedElements(upgraded, ids);
  if (!elements.length) throw Error('Select at least one element to copy.');
  return serializeScreen({ version: upgraded.version, id: 'tideframe_clipboard', title: 'TIDEFRAME CLIPBOARD', quality: { ...upgraded.quality }, ...(upgraded.version>=6?{presentation:{...upgraded.presentation}}:{}), elements });
}
export function pasteClipboard(value, raw) {
  let next = promoteScreen(value), source = promoteScreen(parseScreen(raw));
  if (source.id !== 'tideframe_clipboard' || source.title !== 'TIDEFRAME CLIPBOARD' || !source.elements.length)
    throw Error('Clipboard does not contain Tideframe elements.');
  if (next.version === 9 || source.version === 9) {
    next = upgradeLogic(next); source = upgradeLogic(source);
  } else if (next.version === 8 || source.version === 8) {
    next = upgradeGradient(next); source = upgradeGradient(source);
  } else if (next.version === 7 || source.version === 7) {
    next = upgradeTextStyle(next); source = upgradeTextStyle(source);
  } else if (next.version === 6 || source.version === 6) {
    next = upgradePresentation(next); source = upgradePresentation(source);
  } else if (next.version === 5 || source.version === 5) {
    next = upgradeAppearance(next); source = upgradeAppearance(source);
  }
  if (next.elements.length + source.elements.length > 128) throw Error('Paste would exceed the 128 element limit.');
  const used = new Set(next.elements.map(e => e.id)), ids = [];
  const relocated = placeElements(next, source.elements);
  for (const original of relocated) {
    let counter = 1, id;
    do { const suffix = `_copy${counter++}`; id = original.id.slice(0, 32 - suffix.length) + suffix; } while (used.has(id));
    used.add(id); ids.push(id); next.elements.push({ ...cloneElement(original), id });
  }
  // Bound the complete resulting document, not only each source PNG and payload.
  validateScreenBudget(next);
  return { screen: next, ids };
}
export async function pasteClipboardForPublication(value, raw, decode) {
  const result = pasteClipboard(value, raw);
  await validateScreenImages(result.screen, decode);
  return result;
}
export async function copyClipboard(adapter, raw) {
  try { if (!adapter?.writeText) throw Error('Unavailable'); await adapter.writeText(raw); return { raw, system: true }; }
  catch { return { raw, system: false }; }
}
export async function readClipboard(adapter, internal, preferInternal = false) {
  if (preferInternal && internal) return { raw: internal, system: false };
  try { if (!adapter?.readText) throw Error('Unavailable'); return { raw: await adapter.readText(), system: true }; }
  catch {
    if (internal) return { raw: internal, system: false };
    throw Error('Clipboard access is unavailable. Copy elements here before using the internal fallback.');
  }
}
