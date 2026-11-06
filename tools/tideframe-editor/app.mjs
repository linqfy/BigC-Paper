import { caseText, normalizeText, extendedText, upgradeLogic, mergeLogic, ELEMENT_PRESETS, SCREEN_PRESETS, createScreen, parseScreenForPublication, serializeScreen, addElement, MAX_BYTES, QUALITY_PRESETS, orderedElements, effectiveActionArea, cloneScreen, validateScreenBudget, upgradeAppearance, upgradePresentation, upgradeTextStyle, upgradeGradient, barSource, DEFAULT_PRESENTATION, setScreenLogic, setElementLogic, BLUR_FIELDS, SLICE_FIELDS, iconSize } from './model.mjs';
import { selectionIds, editElementDraft, moveSelection, deleteSelection, pointerIntent, capsLockState, guardPointerGesture, applyPointerGesture, encodeClipboard, pasteClipboardForPublication, createPublicationController, copyClipboard, readClipboard, copyEventPayload, pasteEventPayload } from './interaction.mjs';
import { loadImageFile, validatePngBase64, bytesToBase64 } from './images.mjs';
import { selectSet, cutSelection, duplicateSelection, shiftLayers, alignSelection, distributeSelection, snapSelection, layoutSelection } from './authoring.mjs';
import { createWorkspace, parseProjectForPublication, exportVisibleScreen, validateProject } from './workspace.mjs';
import { templateCatalog, addTemplate } from './templates.mjs';
import { THEMES, applyTheme } from './themes.mjs';
import { setPresentation, addCloseButton, addTextBlock, fillCanvas } from './presentation-tools.mjs';
import { mountAdvancedSettings } from './advanced-ui.mjs';
import { BLUR_PRESETS, applyBlurPreset, writeSetting } from './advanced-settings.mjs';
import { es, BLUR_TEXT, GROUP_TAB } from './i18n.mjs';
import { mountPlay } from './play-ui.mjs';
import { editSelection, sharedChange } from './multi-edit.mjs';
import { WINDOW_PRESETS, simulate } from './resolution.mjs';
import { renderExpressions, hasExpression } from './expressions.mjs';
import { runs as textRuns, hasTags } from './richtext.mjs';
import { fitRuns } from './textfit.mjs';
import { copyStyle, pasteStyle } from './style-clipboard.mjs';
import { layerOrder, moveLayer, renameElement } from './layers.mjs';
import { mapSvg } from './project-map.mjs';
import { ICONS, iconRgba, insertIcon } from './icon-library.mjs';
import { reviewProject } from './checker.mjs';
import { PAGE_TEMPLATES, pageFromTemplate } from './page-templates.mjs';

const $ = id => document.getElementById(id), publication = createPublicationController();
let storage = null; try { storage = window.localStorage; } catch { /* Save results report denied storage. */ }
const workspace = createWorkspace(createScreen(), { storage, historyLimit: 40 }), recovered = workspace.restore();
let screen = cloneScreen(workspace.activePage().screen), selectedIndices = screen.elements.length ? [0] : [], activeIndex = screen.elements.length ? 0 : -1;
let valid = true, drag = null, capsLock = false, advanced = false, internalClipboard = '', preferInternalClipboard = false, guides = [], pendingRenames = {}, copiedStyle = null;
const numberFields = new Set(['x', 'y', 'width', 'height', 'layer']);
const KIND_TEXT = { label: 'texto', button: 'botón', decoration: 'decoración' };
const styleNumbers = new Set(['opacity', 'brightness', 'rotation', 'radius', 'outlineWidth', 'wrapWidth', 'textScale', ...BLUR_FIELDS, ...SLICE_FIELDS]);
const textStyleFields = new Set(['textScale', 'textOutline', 'textShadow']);
const gradientFields = new Set(['gradientColor', 'gradientDirection', 'textGradient']);
const appearanceFields = new Set(['rotation', 'radius', 'outlineWidth', 'hoverColor', 'hoverEffect', 'textAlign', 'wrapWidth']);
const previewNodes = new Map(), pngInfo = new Map();
const rawLogicDrafts = new Set();
let cursorPoint = { x: 0, y: 0 };
const active = () => screen.elements[activeIndex], prefs = () => workspace.project.preferences, page = () => workspace.activePage();
const selectedIds = () => selectionIds(screen, selectedIndices.filter(index => screen.elements[index]));
const editableTarget = target => Boolean(target instanceof Element && target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])'));
const defaultCondition = () => ({ permission: 'tideframe.vip', ifTrue: 'Access granted.', ifFalse: 'Access denied.' });
const advancedSettings = mountAdvancedSettings({
  screenRoot: $('advanced-screen-settings'), elementRoot: $('advanced-element-settings'),
  getState: () => ({ screen, elementId: active()?.id, kind: active()?.kind, locked: active() ? isLocked(active().id) : false, editable: valid && !drag }),
  apply: next => { if (drag || !check() || rawLogicDrafts.size) throw Error('Finish or revert the current JSON edit first.'); publication.invalidate(); applyScreen(next, 'Edit advanced settings', selectedIds(), true); },
  report: (message, error) => status(message, error),
  onDraftChange: () => { publication.invalidate(); updateDraftNotice(); },
  place: (scope, group) => scope === 'element' && GROUP_TAB[group] ? $(`element-groups-${GROUP_TAB[group]}`) : null
});
function hasPendingDrafts() { return rawLogicDrafts.size > 0 || advancedSettings.hasPendingDrafts(); }
function updateDraftNotice() {
  const count = rawLogicDrafts.size + advancedSettings.pendingDraftCount();
  $('advanced-draft-state').hidden = count === 0;
  $('advanced-draft-state').textContent = es(`${count} unapplied advanced draft${count === 1 ? '' : 's'}. Apply or revert before changing selection or exporting.`);
  $('export').disabled = $('export-all').disabled = !valid || count > 0;
}
function requireNoDrafts() {
  if (!hasPendingDrafts()) return true;
  status('Apply or revert the advanced drafts first. Your text has been kept.', true); return false;
}
const clamp = value => Math.min(1, Math.max(-1, value));
function status(message, error = false) { $('status').textContent = es(message); $('status').dataset.error = String(error); }
function autoSave() { const result = workspace.save(); $('autosave-state').textContent = es(result.ok && !valid ? 'Valid project saved; invalid draft excluded.' : result.message); $('autosave-state').dataset.error = String(!result.ok); return result; }
function imageInfo(image) { if (!pngInfo.has(image)) { const png = validatePngBase64(image); pngInfo.set(image, { width: png.width, height: png.height, bytes: png.bytes.length }); } return pngInfo.get(image); }
function selectionFromIds(ids) { selectedIndices = ids.map(id => screen.elements.findIndex(e => e.id === id)).filter(index => index >= 0); activeIndex = selectedIndices.at(-1) ?? -1; }
function expandedIds(ids) {
  const wanted = new Set(ids); for (const group of page().groups) if (group.members.some(id => wanted.has(id))) for (const id of group.members) wanted.add(id);
  return screen.elements.filter(e => wanted.has(e.id)).map(e => e.id);
}
function mutableIds() { const excluded = new Set([...page().hiddenIds, ...page().lockedIds]); return expandedIds(selectedIds()).filter(id => !excluded.has(id)); }
function isLocked(id) { return page().lockedIds.includes(id); }
function hydrate(ids = selectedIds()) {
  screen = cloneScreen(page().screen); pendingRenames = {}; guides = []; selectionFromIds(ids);
  if (!selectedIndices.length && screen.elements.length) { selectedIndices = [0]; activeIndex = 0; } render();
}
function updateHistory() {
  $('undo').disabled = !workspace.canUndo && valid; $('redo').disabled = !workspace.canRedo || !valid;
  $('history-state').textContent = es(`${workspace.undoCount} undo / ${workspace.redoCount} redo${valid ? '' : ' / invalid draft not saved'}`);
}
function check(report = false) {
  const previouslyValid = valid;
  try {
    validateScreenBudget(screen);
    const ids = new Set(screen.elements.map(e => e.id));
    const retained = list => [...new Set(list.map(id => Object.hasOwn(pendingRenames, id) ? pendingRenames[id] : id).filter(id => ids.has(id)))];
    const candidate = { ...page(), screen, hiddenIds: retained(page().hiddenIds), lockedIds: retained(page().lockedIds), groups: page().groups.map(g => ({ ...g, members: retained(g.members) })).filter(g => g.members.length) };
    validateProject({ ...workspace.project, activePageId: screen.id, pages: workspace.project.pages.map(entry => entry === page() ? candidate : entry) });
    valid = true; if (report) status(`Valid screen. ${selectedIndices.length} selected.`);
    else if (!previouslyValid) status('Listo: el borrador volvió a ser válido y se guarda.'); // The old error would stay on screen otherwise.
  } catch (error) { valid = false; status(`Draft not saved: ${error.message}`, true); }
  $('export').disabled = $('export-all').disabled = !valid || hasPendingDrafts();
  $('add-label').disabled = $('add-button').disabled = $('add-decoration').disabled = $('insert-template').disabled = !valid || screen.elements.length >= 128;
  $('add-text-block').disabled = !valid || screen.elements.length >= 128;
  const closeExists = screen.elements.some(e => e.kind === 'button' && e.action === 'close' && e.text === 'X');
  $('add-close-x').disabled = !valid || screen.presentation?.mode === 'overlay' || screen.elements.length >= 128 && !closeExists;
  $('fill-canvas').disabled = !valid || !active() || !mutableIds().includes(active().id);
  $('delete-element').disabled = $('cut-selection').disabled = $('duplicate-selection').disabled = !valid || !mutableIds().length;
  $('copy-selection').disabled = !valid || !selectedIndices.length; $('paste-selection').disabled = !valid;
  $('copy-style').disabled = !valid || !active(); $('paste-style').disabled = !valid || !copiedStyle || !mutableIds().length;
  if (previouslyValid !== valid) advancedSettings.sync();
  updateHistory(); return valid;
}
function commitDraft(label = 'Edit screen') {
  if (!check()) return false;
  try { const changed = workspace.commitScreen(screen, label, { idRenames: pendingRenames }); screen = cloneScreen(page().screen); pendingRenames = {}; if (changed) autoSave(); updateHistory(); renderPages(); return true; }
  catch (error) { valid = false; status(`Draft not saved: ${error.message}`, true); updateHistory(); return false; }
}
function applyScreen(next, label, ids = selectedIds(), allowAdvancedDrafts = false) {
  if (!allowAdvancedDrafts && !requireNoDrafts()) throw Error('Apply or revert the advanced drafts first.');
  validateScreenBudget(next); workspace.commitScreen(next, label); screen = cloneScreen(page().screen); pendingRenames = {}; selectionFromIds(ids); autoSave(); render();
}
function runScreen(label, action) {
  if (drag || !check() || !requireNoDrafts()) return; publication.invalidate();
  try { const ids = mutableIds(); if (!ids.length) throw Error('Select at least one visible, unlocked element.'); const result = action(ids); applyScreen(result.screen ?? result, label, result.ids ?? selectedIds()); status(`${label}. Button action areas remain separate.`); }
  catch (error) { status(`${label} rejected: ${error.message}`, true); }
}
function runWorkspace(label, action) {
  if (drag || !check() || !requireNoDrafts()) return; publication.invalidate();
  try { action(); hydrate(); autoSave(); status(`${label}.`); } catch (error) { status(`${label} rejected: ${error.message}`, true); }
}
function renderPages() {
  $('page-count').textContent = `${workspace.project.pages.length} / 16`;
  $('pages').replaceChildren(...workspace.project.pages.map(entry => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'page-row'; button.textContent = `${entry.screen.id} / ${entry.screen.title}`; button.setAttribute('aria-pressed', String(entry === page()));
    button.addEventListener('click', () => runWorkspace('Page selected', () => workspace.setActivePage(entry.screen.id))); return button;
  }));
  $('add-page').disabled = $('duplicate-page').disabled = $('add-page-template').disabled = workspace.project.pages.length >= 16;
}
// The layers panel (idea 37): top layer first; drag a row to change its layer, double click (or F2) to rename it,
// the eye hides it in the editor and the padlock locks it. The game draws the list from the bottom up.
const LAYER_ICONS = {
  eye: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M1 8s2.6-4.5 7-4.5S15 8 15 8s-2.6 4.5-7 4.5S1 8 1 8z" fill="none" stroke="currentColor" stroke-width="1.4"/><circle cx="8" cy="8" r="2.1" fill="currentColor"/></svg>',
  eyeOff: '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="M1 8s2.6-4.5 7-4.5S15 8 15 8s-2.6 4.5-7 4.5S1 8 1 8z" fill="none" stroke="currentColor" stroke-width="1.4" opacity=".55"/><path d="M2.5 13.5l11-11" stroke="currentColor" stroke-width="1.6"/></svg>',
  lock: '<svg viewBox="0 0 16 16" aria-hidden="true"><rect x="3" y="7" width="10" height="7.5" rx="1.3" fill="currentColor"/><path d="M5.2 7V5.2a2.8 2.8 0 0 1 5.6 0V7" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>',
  unlock: '<svg viewBox="0 0 16 16" aria-hidden="true"><rect x="3" y="7" width="10" height="7.5" rx="1.3" fill="none" stroke="currentColor" stroke-width="1.3"/><path d="M5.2 7V5.2a2.8 2.8 0 0 1 5.4-1" fill="none" stroke="currentColor" stroke-width="1.3"/></svg>',
  grip: '<svg viewBox="0 0 8 16" aria-hidden="true"><g fill="currentColor"><circle cx="2" cy="4" r="1.1"/><circle cx="6" cy="4" r="1.1"/><circle cx="2" cy="8" r="1.1"/><circle cx="6" cy="8" r="1.1"/><circle cx="2" cy="12" r="1.1"/><circle cx="6" cy="12" r="1.1"/></g></svg>'
};
let layerDrag = null;
function layerButton(className, icon, label, pressed, onClick) {
  const button = document.createElement('button'); button.type = 'button'; button.className = `layer-toggle ${className}`; button.innerHTML = icon;
  button.setAttribute('aria-label', label); button.title = label; button.setAttribute('aria-pressed', String(pressed));
  button.addEventListener('click', event => { event.stopPropagation(); onClick(); }); return button;
}
function startRename(row, element) {
  if (row.querySelector('input')) return;
  const input = document.createElement('input'); input.className = 'layer-rename'; input.value = element.id; input.maxLength = 32; input.spellcheck = false;
  input.setAttribute('aria-label', `Nuevo ID para ${element.id}`);
  const name = row.querySelector('.element-row span'); name.hidden = true; name.after(input); input.focus(); input.select();
  let done = false;
  const finish = save => { if (done) return; done = true; const value = input.value.trim(); input.remove(); name.hidden = false; if (save && value && value !== element.id) renameFromLayers(element.id, value); };
  input.addEventListener('keydown', event => { event.stopPropagation(); if (event.key === 'Enter') { event.preventDefault(); finish(true); } else if (event.key === 'Escape') { event.preventDefault(); finish(false); } });
  input.addEventListener('blur', () => finish(true));
  input.addEventListener('click', event => event.stopPropagation());
}
function renameFromLayers(oldId, newId) {
  if (drag || !check() || !requireNoDrafts()) return; publication.invalidate();
  try {
    const next = renameElement(screen, oldId, newId), ids = selectedIds().map(id => id === oldId ? newId : id);
    workspace.commitScreen(next, 'Rename element', { idRenames: { [oldId]: newId } }); screen = cloneScreen(page().screen); pendingRenames = {};
    selectionFromIds(ids); autoSave(); render(); status(`${oldId} is now ${newId}; the actions and tooltips that name it follow.`);
  } catch (error) { status(`Rename rejected: ${error.message}`, true); }
}
function moveFromLayers(id, targetId, place) {
  if (drag || !check() || !requireNoDrafts()) return; publication.invalidate();
  try { applyScreen(moveLayer(screen, id, targetId, place), 'Move layer', selectedIds()); status(`${id} moved ${place === 'above' ? 'above' : 'below'} ${targetId}.`); }
  catch (error) { status(`Layer move rejected: ${error.message}`, true); }
}
function renderList() {
  $('element-count').value = `${screen.elements.length} / 128`; $('selection-count').value = `${selectedIndices.length} elegido${selectedIndices.length === 1 ? '' : 's'}`;
  $('elements').replaceChildren(...layerOrder(screen).map(element => {
    const index = screen.elements.indexOf(element), locked = isLocked(element.id), hidden = page().hiddenIds.includes(element.id);
    const row = document.createElement('div'); row.className = 'layer-row'; row.dataset.id = element.id; row.draggable = !locked;
    const grip = document.createElement('span'); grip.className = 'layer-grip'; grip.innerHTML = LAYER_ICONS.grip; grip.title = locked ? 'Bloqueado: no se mueve' : 'Arrastrá para cambiar la capa';
    const button = document.createElement('button'); button.type = 'button'; button.className = 'element-row'; button.setAttribute('aria-pressed', String(selectedIndices.includes(index))); button.dataset.active = String(index === activeIndex);
    button.dataset.locked = String(locked); button.dataset.hidden = String(hidden);
    const name = document.createElement('span'); name.textContent = element.id || '(falta el ID)'; const kind = document.createElement('small');
    kind.textContent = `${KIND_TEXT[element.kind] ?? element.kind} · capa ${element.layer ?? 0}${page().groups.some(g => g.members.includes(element.id)) ? ' · grupo' : ''}`;
    button.append(name, kind); button.title = 'Doble clic o F2 para renombrar';
    button.addEventListener('click', event => select(index, event.shiftKey));
    button.addEventListener('dblclick', event => { event.preventDefault(); if (!locked) startRename(row, element); });
    button.addEventListener('keydown', event => { if (event.key === 'F2' && !locked) { event.preventDefault(); startRename(row, element); } });
    const eye = layerButton('layer-eye', hidden ? LAYER_ICONS.eyeOff : LAYER_ICONS.eye, hidden ? `Mostrar ${element.id} en el editor` : `Ocultar ${element.id} en el editor`, hidden,
      () => runWorkspace(hidden ? 'Element shown' : 'Element hidden', () => workspace.setHidden([element.id], !hidden)));
    const lock = layerButton('layer-lock', locked ? LAYER_ICONS.lock : LAYER_ICONS.unlock, locked ? `Desbloquear ${element.id}` : `Bloquear ${element.id}`, locked,
      () => runWorkspace(locked ? 'Element unlocked' : 'Element locked', () => workspace.setLocked([element.id], !locked)));
    row.append(grip, button, eye, lock);
    row.addEventListener('dragstart', event => { layerDrag = element.id; row.dataset.dragging = 'true'; event.dataTransfer.effectAllowed = 'move'; event.dataTransfer.setData('text/plain', element.id); });
    row.addEventListener('dragend', () => { layerDrag = null; for (const other of document.querySelectorAll('.layer-row')) { delete other.dataset.drop; delete other.dataset.dragging; } });
    row.addEventListener('dragover', event => {
      if (!layerDrag || layerDrag === element.id) return;
      event.preventDefault(); event.dataTransfer.dropEffect = 'move';
      const bounds = row.getBoundingClientRect(); row.dataset.drop = event.clientY < bounds.top + bounds.height / 2 ? 'above' : 'below';
    });
    row.addEventListener('dragleave', () => { delete row.dataset.drop; });
    row.addEventListener('drop', event => {
      event.preventDefault(); const place = row.dataset.drop ?? 'above', moving = layerDrag; delete row.dataset.drop;
      if (moving && moving !== element.id) moveFromLayers(moving, element.id, place);
    });
    return row;
  }));
}
function renderPreferences() {
  const p = prefs(); for (const input of document.querySelectorAll('[data-pref]')) { const value = p[input.dataset.pref]; if (input.type === 'checkbox') input.checked = value; else input.value = value; }
  $('zoom-control').value = p.zoom * 100; $('zoom-value').value = `${Math.round(p.zoom * 100)}%`; $('primary-color').value = p.primaryColor; $('secondary-color').value = p.secondaryColor;
  $('pages-panel').hidden = !p.showPages; $('tools-panel').dataset.collapsed = String(!p.showTools); $('add-tools').hidden = !p.showTools; $('status-panel').hidden = !p.showFooter;
  $('stage').dataset.grid = String(p.gridVisible); $('stage').style.setProperty('--grid-step', `${p.snapStep * 50}%`); $('stage').style.width = `${p.zoom * 100}%`;
  applySimulation(false);
}
/**
 * "Ver como": the canvas placed and cropped like on a player's screen (resolution.mjs: the game's GUI scale, the pack's
 * fixed sizes, the auto-fit rule). Off (Lienzo completo), the stage shows the whole authored canvas at the editor zoom.
 */
function applySimulation(draw = true) {
  const preset = WINDOW_PRESETS.find(entry => entry.id === $('sim-window').value), frame = $('screen-frame');
  const nodes = [$('stage'), frame.querySelector('.play-stage')].filter(Boolean);
  $('sim-gui').disabled = $('sim-size').disabled = !preset;
  if (!preset) {
    frame.dataset.sim = 'false'; frame.style.removeProperty('--sim-ratio');
    for (const node of nodes) { node.style.position = node.style.left = node.style.top = ''; if (node !== $('stage')) node.style.width = ''; }
    $('sim-info').value = 'Mostrando el lienzo completo, sin recortes.';
  } else {
    const size = $('sim-size').value, autofit = size === 'autofit';
    const view = simulate({ width: preset.width, height: preset.height, guiScale: Number($('sim-gui').value), mode: autofit ? 'autofit' : 'fixed', quarters: Number(size) || 8 });
    frame.dataset.sim = 'true'; frame.style.setProperty('--sim-ratio', `${view.screen.width} / ${view.screen.height}`);
    const percent = (value, total) => `${value / total * 100}%`;
    for (const node of nodes) Object.assign(node.style, { position: 'absolute', left: percent(view.canvas.x, view.screen.width), top: percent(view.canvas.y, view.screen.height), width: percent(view.canvas.width, view.screen.width) });
    const { left, right, top, bottom } = view.cut, cut = left + right + top + bottom > 0;
    const sides = [[left, 'a la izquierda'], [right, 'a la derecha'], [top, 'arriba'], [bottom, 'abajo']].filter(([value]) => value > 0).map(([value, where]) => `${value} px ${where}`);
    const cutText = sides.length > 1 ? `${sides.slice(0, -1).join(', ')} y ${sides.at(-1)}` : sides[0];
    const sizeText = autofit ? `ajuste automático (escala ${view.factor.toFixed(2)})` : `menú de ${$('sim-size').selectedOptions[0].textContent}`;
    const spare = view.canvas.width < view.screen.width - .5 || view.canvas.height < view.screen.height - .5;
    $('sim-info').value = `Pantalla de ${view.screen.width} × ${view.screen.height} unidades de GUI (escala ${view.gui}${$('sim-gui').value === '0' ? ', automática' : ''}), ${sizeText}. `
      + (cut ? `Del lienzo se cortan ${cutText}. ` : 'El lienzo entra entero. ')
      + (spare ? 'Alrededor sobra pantalla: ahí se ve el mundo. ' : '')
      + (view.safeVisible ? 'La zona segura entra completa.' : 'Atención: la zona segura no entra; textos o botones cerca del borde quedan afuera.');
  }
  $('sim-info').dataset.warn = String(Boolean(preset) && !$('sim-info').value.includes('zona segura entra'));
  if (draw) renderPreview();
}
function renderPresentation() {
  const presentation = screen.presentation ?? DEFAULT_PRESENTATION, overlay = presentation.mode === 'overlay';
  $('presentation-mode').value = presentation.mode; $('presentation-scale').value = presentation.scale; $('presentation-cursor').value = presentation.cursor;
  $('presentation-cursor').disabled = $('presentation-cursorSize').disabled = $('presentation-cursorSpeed').disabled = overlay;
  $('presentation-stack').checked = presentation.stack === true; $('presentation-stack').disabled = !overlay;
  if (document.activeElement !== $('presentation-cursorSize')) $('presentation-cursorSize').value = presentation.cursorSize ?? '';
  if (document.activeElement !== $('presentation-cursorSpeed')) $('presentation-cursorSpeed').value = presentation.cursorSpeed ?? 1;
  $('presentation-summary').textContent = es(`${overlay ? 'Passive overlay' : 'Interactive menu'} / ${presentation.scale}x game HUD${overlay ? ' / cursor disabled' : ` / ${presentation.cursor} cursor`}${presentation.stack ? ' / stacks with other HUD pages' : ''}`);
  $('presentation-help').textContent = es(overlay
    ? 'Persistent passive HUD: player movement and camera stay free. Cursor and button actions are disabled in game; existing buttons remain editable here. Close with /tideframe close.'
    : 'Interactive menu: player movement and camera lock until the menu closes. Add a close X for a visible close target.');
  $('preview-mode').textContent = es(`${overlay ? 'PASSIVE OVERLAY' : 'MENU'} / AUTHORED BOUNDS`);
  $('stage').dataset.mode = presentation.mode;
}
// Live tokens preview with sample values, then the element's text case (capitals unless "As written"), so layouts
// can be judged at real size with the game font's own glyphs.
const PREVIEW_SAMPLES = { player: 'Steve', health: '18', max_health: '20', food: '17', level: '12', xp: '45%', balance: '1250', online: '7', max_players: '50', ping: '42',
  world: 'World', x: '120', y: '64', z: '-35', time: '12:30', balance_short: '1.25M', key: 'W', key_word: 'Space', feedback: 'Perfect!', combo: 'Combo x3', score: '1240', best_combo: '7',
  hits: '9', misses: '1', boss_hp: '62', boss_max: '100', boss_name: 'Boss name', lives: '4/5' };
/** The page's declared variables by bare name, with their initial values: what a player sees first. */
function pageVariables() {
  const out = {};
  for (const [name, value] of Object.entries(screen.logic?.variables ?? {})) out[name.replace(/^[*$]/, '')] = String(value);
  return out;
}
function previewText(text, textCase = 'upper') {
  if (text.includes('\n') || !/\{[a-z_][a-z0-9_]{0,23}\}|%[A-Za-z0-9_:.-]{1,64}%/.test(text) && !hasExpression(text)) return caseText(text, textCase);
  // As in the game: the player's built-in values win over page variables with the same name.
  const values = { ...pageVariables(), ...PREVIEW_SAMPLES };
  const tokens = part => part.replace(/\{([a-z_][a-z0-9_]{0,23})\}/g, (token, name) => values[name] ?? token).replace(/%[A-Za-z0-9_:.-]{1,64}%/g, '42');
  // Calculated text ({coins * 2}) previews with the same values.
  // A schema 1-8 page has no calculated tokens: "{1+1}" stays text, as in the plugin (Screen.legacyText).
  if (screen.version < 9) return caseText(tokens(text), textCase);
  return caseText(renderExpressions(text, tokens, name => name.startsWith('%') ? '42' : values[name.replace(/^\$/, '')] ?? null), textCase);
}
// Item icons (element "item"): the table comes with the plugin; the editor server reads each texture from the
// player's own client jar for the preview. Without either the field still works and the game draws the icon.
let itemIcons = null;
import('./item-icons.mjs').then(module => {
  itemIcons = module.ITEM_ICONS;
  $('item-list').replaceChildren(...Object.keys(itemIcons).map(item => { const option = document.createElement('option'); option.value = item; return option; }));
  renderPreview(); renderInspector();
}).catch(() => { itemIcons = null; });
/** A preview node for {@code element} (its image, hover image and label); styled by paintNode. Shared with Probar. */
function createNode(element) {
  const button = document.createElement('button'); button.type = 'button'; button.className = 'preview-element';
  if (element.image !== null) {
    const placeholder = document.createElement('span'); placeholder.className = 'image-placeholder'; placeholder.textContent = 'PNG no disponible';
    try { imageInfo(element.image); const image = document.createElement('img'); image.className = 'element-image'; image.alt = ''; image.draggable = false; image.addEventListener('error', () => { image.remove(); button.prepend(placeholder); }, { once: true }); image.src = `data:image/png;base64,${element.image}`; button.append(image); } catch { button.append(placeholder); }
  }
  // The hover image shows while the mouse is over the preview, like the cursor in the game.
  if (element.hoverImage) { const swap = document.createElement('img'); swap.className = 'element-hover-image'; swap.alt = ''; swap.draggable = false; swap.src = `data:image/png;base64,${element.hoverImage}`; button.append(swap); button.dataset.hoverImage = 'true'; }
  const label = document.createElement('span'); label.className = 'element-label'; button.append(label); return { button, label, image: element.image, hoverImage: element.hoverImage };
}
/** Styles a preview node like the game draws {@code element} at {@code logicalScale} px per logical pixel. Shared with Probar. */
/**
 * The label as the game draws it: on schema 9 pages, words in their own colour (idea 28), each run previewed on its own,
 * and a line wider than its box drawn smaller or cut with "…" (textfit.mjs). {@code shown}: Probar's own runs and page
 * version (live values already filled in). Returns the text size in quarters (4 = as authored).
 */
function paintLabel(label, element, shown) {
  const style = element.style, version = shown?.version ?? screen.version;
  const runs = shown?.runs ?? (version < 9 || !hasTags(element.text) ? [{ text: previewText(element.text, style.textCase), color: null }]
    : textRuns(element.text).map(run => ({ text: previewText(run.text, style.textCase), color: run.color })));
  const fitted = fitRuns(element, runs, version);
  if (fitted.runs.length === 1 && !fitted.runs[0].color) label.textContent = fitted.runs[0].text;
  else label.replaceChildren(...fitted.runs.map(run => {
    const span = document.createElement('span'); span.textContent = run.text;
    if (run.color) { span.dataset.color = run.color; span.style.color = run.color; }
    return span;
  }));
  label.dataset.fit = String(fitted.size);
  return fitted.size;
}
function paintNode(cached, element, logicalScale, shown = null) {
  const { button, label } = cached, style = element.style;
  Object.assign(button.dataset, { kind: element.kind, shape: element.shape, border: String(element.border), background: String(element.background), hoverEffect: style.hoverEffect ?? 'none', textOnly: String(element.image === null && !element.background), customHover: String(Boolean(style.hoverColor)) });
  // As the game draws it: a picture keeps its square corners unless style.imageClip gives it the element's shape.
  button.dataset.imageFree = String(Boolean(element.image) && style.imageClip !== true && element.shape !== 'rectangle');
  const textScale = style.textScale ?? 1, bar = barSource(element);
  // Text wider than its box shrinks (3/4, 1/2): the font, the outline ring and the shadow all scale with it.
  const fit = bar ? 4 : paintLabel(label, element, shown);
  if (bar) label.textContent = '';
  label.style.textAlign = style.textAlign ?? 'center'; label.style.whiteSpace = style.wrapWidth > 0 ? 'pre-wrap' : 'pre'; label.style.maxWidth = style.wrapWidth > 0 ? `${style.wrapWidth * logicalScale}px` : '100%'; label.style.fontSize = `${7 * textScale * logicalScale * fit / 4}px`;
  // Same look as the game: outline ring and a quarter-brightness shadow offset by one font pixel.
  const unit = textScale * logicalScale * fit / 4, ring = style.textOutline ? Math.ceil(textScale / 2) * logicalScale * fit / 4 : 0, shadows = [];
  if (style.textOutline) for (const [dx, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1], [-1, -1], [1, -1], [-1, 1], [1, 1]]) shadows.push(`${dx * ring}px ${dy * ring}px 0 ${style.textOutline}`);
  if (style.textShadow) shadows.push(`${unit + ring}px ${unit + ring}px 0 color-mix(in srgb, ${style.textOutline ?? style.textColor} 25%, black)`);
  label.style.textShadow = style.textGradient ? '' : shadows.join(', ');
  // A coloured word is solid (no gradient) and, without an outline, casts its own colour at a quarter brightness.
  for (const span of label.querySelectorAll('span[data-color]')) span.style.textShadow = style.textShadow && !style.textOutline
    ? `${unit}px ${unit}px 0 color-mix(in srgb, ${span.dataset.color} 25%, black)` : style.textGradient ? 'none' : '';
  // Text gradient: the fill is clipped to the glyphs; outline and shadow move to drop-shadow filters so they stay visible.
  label.style.backgroundImage = style.textGradient ? `linear-gradient(${style.textColor}, ${style.textGradient})` : '';
  label.style.webkitBackgroundClip = label.style.backgroundClip = style.textGradient ? 'text' : '';
  label.style.color = style.textGradient ? 'transparent' : '';
  label.style.filter = style.textGradient ? shadows.map(value => `drop-shadow(${value})`).join(' ') : ''; label.style.overflow = shadows.length || textScale > 1 ? 'visible' : ''; label.style.lineHeight = textScale > 1 ? '1' : '';
  // Bars preview at 70% so the fill colour is visible while editing.
  button.dataset.bar = bar ?? ''; button.style.setProperty('--bar-fill', style.textColor); const notches = bar ? (/:(\d+)\}$/.exec(element.text)?.[1] ?? '10') : '0'; button.style.setProperty('--bar-cell', Number(notches) > 1 ? `${100 / (Number(notches) * .7)}%` : '200%'); button.title = bar ? `Barra en vivo ${bar} (se muestra al 70 %)` : '';
  button.style.opacity = Number.isFinite(style.opacity) ? style.opacity : 1; button.style.setProperty('--element-fill', style.fillColor); button.style.setProperty('--element-border', style.borderColor); button.style.setProperty('--element-text', style.textColor);
  button.style.setProperty('--hover-color', style.hoverColor ?? style.fillColor); button.style.setProperty('--rotation', `${style.rotation ?? 0}deg`); button.style.setProperty('--hover-slide', `${-2 * logicalScale}px`); button.style.setProperty('--outline-width', `${(style.outlineWidth ?? 1) * logicalScale}px`); button.style.setProperty('--glow', `${6 * logicalScale}px`);
  const gradient = style.gradientColor ? (style.gradientDirection === 'radial' ? `radial-gradient(circle, ${style.fillColor}, ${style.gradientColor})`
    : `linear-gradient(${{ vertical: '180deg', horizontal: '90deg', diagonal: '135deg' }[style.gradientDirection] ?? '180deg'}, ${style.fillColor}, ${style.gradientColor})`) : '';
  button.dataset.gradient = String(Boolean(gradient)); button.style.setProperty('--element-gradient', gradient || 'none'); button.style.setProperty('--glow-color', style.hoverColor ?? '#61dfd1');
  const brightness = Number.isFinite(style.brightness) ? Math.min(2, Math.max(0, style.brightness)) : 1;
  // Blurs match the game: CSS blur() takes the same standard deviation; a drop-shadow's radius is twice it.
  const blur = Number.isInteger(style.blur) ? style.blur : 0, backdrop = Number.isInteger(style.backdropBlur) ? style.backdropBlur : 0, shadowBlur = Number.isInteger(style.shadowBlur) ? style.shadowBlur : 0;
  const shadow = !style.shadow ? '' : shadowBlur > 0 ? ` drop-shadow(0 ${2 * logicalScale}px ${2 * shadowBlur * logicalScale}px #00000060)` : ' drop-shadow(3px 4px 2px #0009)';
  button.style.color = style.textColor; button.style.filter = `brightness(${brightness})${shadow}${blur > 0 ? ` blur(${blur * logicalScale}px)` : ''}`;
  button.style.backdropFilter = backdrop > 0 ? `blur(${backdrop * logicalScale}px)` : ''; button.dataset.backdrop = String(backdrop > 0); button.style.borderRadius = element.shape === 'ellipse' ? '50%' : element.shape === 'rounded' ? `${(style.radius ?? .08) * Math.min(element.width * 128, element.height * 72) * logicalScale}px` : '0'; button.style.zIndex = String((element.layer ?? 0) + 129);
  // 9-slice (style "slice"): the image fills the box with fixed corners, as the game bakes it (CSS border-image, nearest pixels).
  const slice = Number.isInteger(style.slice) ? style.slice : 0, density = Number.isInteger(style.sliceScale) ? style.sliceScale : 1;
  button.dataset.slice = String(slice > 0 && element.image !== null);
  for (const [kind, picture] of [['normal', element.image], ['hover', element.hoverImage]]) {
    let node = button.querySelector(`:scope > .element-slice.${kind}`);
    if (slice > 0 && element.image !== null && picture) {
      if (!node) { node = document.createElement('span'); node.className = `element-slice ${kind}`; node.setAttribute('aria-hidden', 'true'); button.prepend(node); }
      const edge = `${slice / density * logicalScale}px`;
      node.style.borderWidth = edge; node.style.borderImage = `url("data:image/png;base64,${picture}") ${slice} fill / ${edge} stretch`;
    } else node?.remove();
  }
  // Item icon: the client texture at the size the game draws it (16 to 64 logical px), first frame, over the element.
  let itemNode = button.querySelector(':scope > .element-item');
  if (itemNode && itemNode.dataset.item !== element.item) { itemNode.remove(); itemNode = null; }
  if (element.item && !itemNode) {
    const missing = () => { const box = document.createElement('span'); box.className = 'element-item missing'; box.dataset.item = element.item; box.textContent = element.item; box.title = 'La vista previa necesita el editor abierto con serve.mjs y Minecraft 26.2 instalado; en el juego el ícono se ve igual.'; box.style.width = box.style.height = node.style.width; return box; };
    const node = document.createElement('img'); node.className = 'element-item'; node.alt = ''; node.draggable = false; node.dataset.item = element.item;
    node.addEventListener('error', () => node.replaceWith(missing()), { once: true }); node.src = `/item-texture/${element.item}`;
    button.append(node); itemNode = node;
  }
  if (itemNode) itemNode.style.width = itemNode.style.height = `${iconSize(element) * logicalScale}px`;
  button.style.left = `${(clamp(Number.isFinite(element.x) ? element.x : 0) + 1) * 50}%`; button.style.top = `${(1 - clamp(Number.isFinite(element.y) ? element.y : 0)) * 50}%`;
  button.style.width = `${Number.isFinite(element.width) && element.width > 0 ? Math.min(2, element.width) * 50 : 40}%`; button.style.height = `${Number.isFinite(element.height) && element.height > 0 ? Math.min(2, element.height) * 50 : 9}%`;
}
function renderPreview(focus = false) {
  const used = new Set(screen.elements.map(e => e.image).filter(Boolean)); for (const key of pngInfo.keys()) if (!used.has(key)) pngInfo.delete(key);
  const nodes = [], hidden = new Set(page().hiddenIds), logicalScale = $('stage').getBoundingClientRect().width / 256;
  for (const { element, index } of orderedElements(screen)) {
    if (hidden.has(element.id)) continue; let cached = previewNodes.get(index);
    if (!cached || cached.image !== element.image || cached.hoverImage !== element.hoverImage) { cached = createNode(element); previewNodes.set(index, cached); }
    const { button } = cached; paintNode(cached, element, logicalScale);
    Object.assign(button.dataset, { index, locked: String(isLocked(element.id)) });
    button.setAttribute('aria-label', `Editar ${element.id || 'elemento'}: ${element.text}`); button.setAttribute('aria-pressed', String(selectedIndices.includes(index)));
    nodes.push(button);
    // A data list shows all its rows, filled from the page's variable (Probar runs them; here they are only drawn).
    const list = element.logic?.list;
    if (list) {
      const rows = Math.max(1, Math.min(16, Number(list.rows ?? 5))), spacing = Number(list.spacing ?? 16);
      const data = String(pageVariables()[list.source] ?? '').split(',').map(item => item.trim()).filter(Boolean);
      const rowText = row => element.text.replaceAll('{item}', data[row] ?? `fila ${row + 1}`).replaceAll('{index}', String(row + 1));
      paintLabel(cached.label, rowText(0), element.style);
      for (let row = 1; row < rows; row++) {
        const ghost = button.cloneNode(true); delete ghost.dataset.index;
        ghost.removeAttribute('aria-label'); ghost.removeAttribute('aria-pressed'); ghost.setAttribute('aria-hidden', 'true'); ghost.tabIndex = -1; ghost.classList.add('preview-ghost');
        ghost.style.top = `${(1 - clamp(element.y - row * spacing / 72)) * 50}%`;
        paintLabel(ghost.querySelector('.element-label'), rowText(row), element.style);
        nodes.push(ghost);
      }
    }
    if (advanced && element.kind === 'button') {
      const area = effectiveActionArea(element), overlay = document.createElement('span'); overlay.className = 'action-area-overlay'; overlay.dataset.active = String(selectedIndices.includes(index)); overlay.setAttribute('aria-hidden', 'true');
      overlay.style.left = `${(area.x + 1) * 50}%`; overlay.style.top = `${(1 - area.y) * 50}%`; overlay.style.width = `${area.width * 50}%`; overlay.style.height = `${area.height * 50}%`; nodes.push(overlay);
    }
  }
  for (const index of previewNodes.keys()) if (index >= screen.elements.length) previewNodes.delete(index);
  if (prefs().smartGuides) for (const guide of guides) { const line = document.createElement('span'); line.className = `snap-guide axis-${guide.axis}`; line.dataset.source = guide.source; line.setAttribute('aria-hidden', 'true'); line.style[guide.axis === 'x' ? 'left' : 'top'] = `${(guide.axis === 'x' ? guide.position + 1 : 1 - guide.position) * 50}%`; nodes.push(line); }
  if (active()?.item || document.activeElement === $('element-item')) {
    const zone = document.createElement('span'); zone.className = 'icon-zone-guide'; zone.setAttribute('aria-hidden', 'true');
    zone.textContent = 'Acá el juego no puede dibujar íconos de ítems'; nodes.push(zone);
  }
  // Full-screen safe area: menus fill the screen edge to edge, so a few pixels of every edge can fall off; keep text and buttons inside.
  { const safe = document.createElement('span'); safe.className = 'safe-area-guide'; safe.setAttribute('aria-hidden', 'true'); safe.title = 'Zona segura: dejá adentro los textos y los botones'; nodes.push(safe); }
  if (prefs().showHotbar) { const hotbar = document.createElement('div'); hotbar.className = 'hotbar-guide'; hotbar.setAttribute('aria-label', 'Guía aproximada de la barra de objetos'); for (let i = 0; i < 9; i++) hotbar.append(document.createElement('span')); nodes.push(hotbar); }
  if (prefs().showHand) { const hand = document.createElement('div'); hand.className = 'hand-guide'; hand.setAttribute('aria-label', 'Guía aproximada de la mano'); nodes.push(hand); }
  const presentation = screen.presentation ?? DEFAULT_PRESENTATION;
  if (presentation.mode === 'menu') {
    const cursor = document.createElement('span'); cursor.className = 'game-cursor-preview'; cursor.dataset.kind = presentation.cursor; cursor.setAttribute('aria-hidden', 'true');
    cursor.style.left = `${(cursorPoint.x + 1) * 50}%`; cursor.style.top = `${(1 - cursorPoint.y) * 50}%`; nodes.push(cursor);
  }
  $('stage').replaceChildren(...nodes); if (focus && active()) $('stage').querySelector(`[data-index="${activeIndex}"]`)?.focus({ preventScroll: true });
}
function renderInspector() {
  const element = active(), locked = element ? isLocked(element.id) : false; $('empty-selection').hidden = Boolean(element); $('inspector-form').hidden = !element; $('selected-kind').textContent = (KIND_TEXT[element?.kind] ?? 'ninguno').toUpperCase();
  const shared = element && !locked ? sharedTargets().length : 0;
  $('selection-help').textContent = !element ? 'Elegí un elemento para verlo acá.' : shared > 1
    ? `${shared} elementos a la vez: apariencia, forma, tamaño y capa van a todos; ID, texto, posición y acciones son de ${element.id}.`
    : `${selectedIndices.length} elegido${selectedIndices.length === 1 ? '' : 's'}. El inspector edita ${element.id || 'el elemento activo'}${locked ? ' (bloqueado)' : ''}.`; if (!element) { advancedSettings.sync(); return; }
  for (const input of $('inspector-form').querySelectorAll('input,select,textarea,button:not([data-tab])')) input.disabled = locked;
  for (const input of $('inspector-form').querySelectorAll('[data-field]')) { const value = element[input.dataset.field]; if (input.type === 'checkbox') input.checked = value; else input.value = Number.isNaN(value) ? '' : value; input.removeAttribute('aria-invalid'); }
  const defaults = { brightness: 1, rotation: 0, radius: .08, outlineWidth: 1, hoverColor: '#53dbc1', hoverEffect: 'none', textAlign: 'center', wrapWidth: 0, textScale: 1, textCase: 'upper', textOutline: '#000000', textShadow: false, gradientColor: '#0b1620', gradientDirection: 'vertical', textGradient: '#f2a03a', blur: 0, backdropBlur: 0, shadowBlur: 0, slice: 0, sliceScale: 1 };
  for (const input of $('inspector-form').querySelectorAll('[data-style]')) { const value = element.style[input.dataset.style] ?? defaults[input.dataset.style]; if (input.type === 'checkbox') input.checked = value; else input.value = Number.isNaN(value) ? '' : value; }
  $('hover-color-enabled').checked = Boolean(element.style.hoverColor); $('style-hoverColor').disabled = locked || !element.style.hoverColor;
  $('gradient-enabled').checked = Boolean(element.style.gradientColor); $('style-gradientColor').disabled = $('style-gradientDirection').disabled = locked || !element.style.gradientColor;
  $('text-gradient-enabled').checked = Boolean(element.style.textGradient); $('style-textGradient').disabled = locked || !element.style.textGradient;
  $('text-outline-enabled').checked = Boolean(element.style.textOutline); $('style-textOutline').disabled = locked || !element.style.textOutline; $('style-textScale').value = String(element.style.textScale ?? 1);
  const custom = element.kind === 'button' && element.actionArea !== null; $('custom-action-area').checked = custom; $('custom-action-area').disabled = locked || element.kind !== 'button'; $('action-area-fields').hidden = !custom;
  const area = effectiveActionArea(element); for (const input of $('action-area-fields').querySelectorAll('[data-area]')) { input.value = Number.isNaN(area[input.dataset.area]) ? '' : area[input.dataset.area]; input.disabled = locked || !custom; }
  $('element-action').disabled = locked || element.kind === 'decoration'; $('remove-image').disabled = locked || element.image === null;
  $('load-hover-image').disabled = locked || element.kind !== 'button'; $('remove-hover-image').disabled = locked || !element.hoverImage;
  if (document.activeElement !== $('element-item')) $('element-item').value = element.item ?? '';
  { const icon = element.item ? itemIcons?.[element.item] : null;
    $('item-info').textContent = !element.item ? 'Sin ícono. Dibuja un ítem con la textura de tu propio juego, centrado en la caja (16, 32, 48 o 64 píxeles). Solo entra en la parte de abajo del menú.'
      : itemIcons && !icon ? `El juego no tiene un ítem ${element.item}.`
      : `Ícono de ${iconSize(element)} píxeles, dibujado con la textura de tu juego.${icon?.block ? ' Es un bloque: el juego lo muestra en 3D y acá va una cara plana.' : ''}${icon?.tinted ? ' El juego le pone un color que acá no se ve.' : ''}${icon && icon.rows > 1 ? ' Es animado: se ve su primer cuadro.' : ''} Para agrandarlo, agrandá la caja.`; }
  try { const swap = element.hoverImage ? validatePngBase64(element.hoverImage) : null; $('hover-image-info').textContent = swap ? `Imagen de hover: ${swap.width} x ${swap.height} píxeles. Se ve mientras el cursor está sobre el botón.` : element.kind === 'button' ? 'Sin imagen de hover.' : 'Solo los botones pueden tener imagen de hover.'; } catch { $('hover-image-info').textContent = 'La imagen de hover no está disponible. Quitala o cargá una válida.'; }
  try { const png = element.image === null ? null : imageInfo(element.image); $('image-info').textContent = png ? `PNG: ${png.width} x ${png.height} píxeles, ${png.bytes} bytes, con transparencia.` : 'Sin imagen. Cualquier elemento puede mostrar un PNG.'; } catch { $('image-info').textContent = 'El PNG no está disponible. Quitalo o cargá un PNG fijo válido.'; }
  const conditional = element.action === 'conditional-message'; $('conditional-message').checked = conditional; $('conditional-message').disabled = locked || element.kind !== 'button'; $('element-action').readOnly = conditional && element.kind === 'button'; $('condition-fields').hidden = !conditional;
  for (const input of $('condition-fields').querySelectorAll('[data-condition]')) { input.value = element.messageCondition?.[input.dataset.condition] ?? ''; input.disabled = locked || !conditional; input.removeAttribute('aria-invalid'); }
  if (!rawLogicDrafts.has('element-logic') && document.activeElement !== $('element-logic')) { $('element-logic').value = element.logic ? JSON.stringify(element.logic, null, 2) : ''; $('element-logic').removeAttribute('aria-invalid'); }
  const tooltip = $('element-tooltip'), others = screen.elements.filter(e => e.id !== element.id).map(e => e.id);
  tooltip.replaceChildren(new Option('Ninguno', ''), ...others.map(id => new Option(id, id)));
  tooltip.value = others.includes(element.logic?.tooltip) ? element.logic.tooltip : ''; tooltip.disabled = locked || element.kind !== 'button';
  advancedSettings.sync();
}
/** Schema 9 logic boxes: parsed and applied on change (focus leaves the box). */
function applyLogic(box, apply, label) {
  if (advancedSettings.hasPendingDrafts()) { status('Apply or revert the structured advanced drafts before replacing JSON.', true); return; }
  const raw = box.value.trim();
  let logic = null;
  try { logic = raw === '' ? null : JSON.parse(raw); } catch (error) { box.setAttribute('aria-invalid', 'true'); status(`Logic is not valid JSON: ${error.message}`, true); return; }
  try { publication.invalidate(); applyScreen(apply(logic), label, selectedIds(), true); rawLogicDrafts.delete(box.id); updateDraftNotice(); box.removeAttribute('aria-invalid'); status(logic === null ? 'Logic removed.' : 'Logic applied (schema 9).'); render(); }
  catch (error) { box.setAttribute('aria-invalid', 'true'); status(error.message, true); }
}
function render() {
  $('screen-id').value = screen.id; $('screen-title').value = screen.title; $('schema-version').textContent = `V${screen.version}`; $('quality-preset').value = screen.quality.preset; $('quality-width').value = screen.quality.width; $('quality-dimensions').value = `${screen.quality.width} x ${screen.quality.height}`; $('custom-quality').hidden = screen.quality.preset !== 'custom';
  if (!rawLogicDrafts.has('screen-logic') && document.activeElement !== $('screen-logic')) { $('screen-logic').value = screen.logic ? JSON.stringify(screen.logic, null, 2) : ''; $('screen-logic').removeAttribute('aria-invalid'); }
  renderPages(); renderPreferences(); renderPresentation(); renderList(); renderPreview(); renderInspector(); check();
}
function select(index, toggle = false, preserveGroup = false) {
  if (!screen.elements[index] || !requireNoDrafts()) return; publication.invalidate(); const group = expandedIds([screen.elements[index].id]), current = new Set(selectedIds());
  if (toggle) { const remove = group.every(id => current.has(id)); for (const id of group) if (remove) current.delete(id); else current.add(id); }
  else if (!preserveGroup || !current.has(screen.elements[index].id)) { current.clear(); for (const id of group) current.add(id); }
  selectionFromIds([...current]); if (selectedIndices.includes(index)) activeIndex = index; renderList(); renderPreview(); renderInspector(); check();
}
function changeActive(change, label = 'Edit element') {
  if (!active() || isLocked(active().id)) return;
  if (Object.hasOwn(change, 'id') && !requireNoDrafts()) { $('element-id').value = active().id; return; }
  if (Object.hasOwn(change, 'id')) { const before = page().screen.elements[activeIndex]?.id; if (before) pendingRenames[before] = change.id; }
  screen = editElementDraft(screen, activeIndex, change); commitDraft(label); renderList(); renderPreview();
}
/** Unlocked, visible elements a shared inspector change reaches: the selection (groups included), else the active one. */
function sharedTargets() {
  const ids = new Set(mutableIds()), indices = screen.elements.map((e, i) => ids.has(e.id) ? i : -1).filter(i => i >= 0);
  return indices.includes(activeIndex) ? indices : [activeIndex];
}
/** Appearance, shape, size and layer: every selected element at once (one undo step); otherwise just the active one. */
function changeSelection(build, label = 'Edit element') {
  if (!active() || isLocked(active().id)) return;
  const targets = sharedTargets();
  if (targets.length < 2) { changeActive(build(active()), label); return; }
  screen = editSelection(screen, targets, build); commitDraft(label); renderList(); renderPreview();
}
function finishDrag(focus = false, cancel = false) {
  const ended = drag; drag = null; guides = []; if (!ended) return;
  if (cancel) screen = ended.base; else commitDraft(ended.mode === 'resize' ? 'Resize selection' : 'Move selection');
  if ($('stage').hasPointerCapture(ended.pointer)) $('stage').releasePointerCapture(ended.pointer); renderList(); renderInspector(); renderPreview(focus); check();
}
function updateCapsLock(event) {
  capsLock = capsLockState(event); $('caps-lock-state').textContent = `Bloq Mayús: ${capsLock ? 'SÍ' : 'NO'}`; $('caps-lock-state').dataset.on = String(capsLock);
  if (drag && !guardPointerGesture(drag, capsLock)) { finishDrag(); status('Caps Lock is OFF. Resize ended; turn it ON and start a new drag to resize again.'); }
}
async function copySelected() {
  if (!check() || !selectedIndices.length) return;
  try { internalClipboard = encodeClipboard(screen, selectedIds()); const result = await copyClipboard(navigator.clipboard, internalClipboard); preferInternalClipboard = !result.system; status(result.system ? 'Selection copied to the clipboard.' : 'System clipboard unavailable. Selection copied internally.'); } catch (error) { status(`Copy rejected: ${error.message}`, true); }
}
async function cutSelected(event = null) {
  if (drag || !check()) return;
  try {
    const base = screen, ids = mutableIds(); if (!ids.length) throw Error('Select a visible, unlocked element.'); const result = cutSelection(base, ids); internalClipboard = result.clipboard;
    await publication.run(async () => event ? copyEventPayload(event, result.clipboard) : (await copyClipboard(navigator.clipboard, result.clipboard)).system, {
      pending: 'Copying the cut selection...', failure: 'Cut rejected', report: status,
      apply(system) { if (screen !== base) return false; preferInternalClipboard = !system; applyScreen(result.screen, 'Cut selection', []); },
      success: system => `Cut ${ids.length} elements${system ? '' : ' to the internal clipboard'}.`
    });
  } catch (error) { status(`Cut rejected: ${error.message}`, true); }
}
function applyPaste(source) {
  const base = screen, pageId = workspace.project.activePageId;
  return publication.run(async () => { const payload = await source; return { ...await pasteClipboardForPublication(base, payload.raw), payload }; }, {
    pending: 'Checking clipboard JSON and PNG images...', failure: 'Paste rejected', report: status,
    apply(result) { if (screen !== base || workspace.project.activePageId !== pageId) return false; applyScreen(result.screen, 'Paste elements', result.ids); },
    success: result => `Pasted ${result.ids.length} elements${result.payload.system ? '' : ' from the internal clipboard'}. Free action space and new IDs applied.`
  });
}
function pasteSelected() { if (!drag && check()) return applyPaste(readClipboard(navigator.clipboard, internalClipboard, preferInternalClipboard)); }
function download(raw, filename) { if (!requireNoDrafts()) throw Error('Apply or revert the advanced drafts before downloading.'); const url = URL.createObjectURL(new Blob([raw], { type: 'application/json' })), link = document.createElement('a'); link.href = url; link.download = filename; document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }

for (const kind of ['label', 'button', 'decoration']) $(`add-${kind}`).addEventListener('click', () => {
  if (!check() || drag || !requireNoDrafts()) return; publication.invalidate(); try { const next = addElement(screen, kind); applyScreen(next, `Add ${kind}`, [next.elements.at(-1).id]); $('element-text').focus(); status(`${KIND_TEXT[kind][0].toUpperCase()}${KIND_TEXT[kind].slice(1)} agregado en un lugar libre.`); } catch (error) { status(`Add rejected: ${error.message}`, true); }
});
for (const [id, key] of [['presentation-mode', 'mode'], ['presentation-scale', 'scale'], ['presentation-cursor', 'cursor']]) $(id).addEventListener('change', () => {
  if (drag || !check()) { renderPresentation(); return; } publication.invalidate();
  try { applyScreen(setPresentation(screen, { [key]: key === 'scale' ? Number($(id).value) : $(id).value }), 'Change game presentation'); status(key === 'scale' ? 'Manual game HUD scale saved. Calibrate physical coverage in Minecraft; raster quality and editor zoom are unchanged.' : 'Game presentation saved. Overlay mode keeps player movement and camera free.'); }
  catch (error) { renderPresentation(); status(`Presentation rejected: ${error.message}`, true); }
});
$('presentation-stack').addEventListener('change', () => {
  if (drag || !check()) { renderPresentation(); return; } publication.invalidate();
  const stack = $('presentation-stack').checked;
  try { applyScreen(setPresentation(screen, { stack }), 'Change game presentation'); status(stack ? 'HUD page stacking saved: it shows together with the HUD pages already on screen (schema 9).' : 'HUD page stacking removed: showing this page replaces the other HUD pages.'); }
  catch (error) { renderPresentation(); status(`Presentation rejected: ${error.message}`, true); }
});
for (const [id, key] of [['presentation-cursorSize', 'cursorSize'], ['presentation-cursorSpeed', 'cursorSpeed']]) $(id).addEventListener('change', () => {
  if (drag || !check()) { renderPresentation(); return; } publication.invalidate();
  const raw = $(id).value.trim(), value = raw === '' ? (key === 'cursorSize' ? null : 1) : Number(raw);
  try { applyScreen(setPresentation(screen, { [key]: value }), 'Change cursor'); status(key === 'cursorSize' ? 'Cursor size for this page saved (schema 9).' : 'Cursor speed for this page saved (schema 9).'); }
  catch (error) { renderPresentation(); status(`Cursor setting rejected: ${error.message}`, true); }
});
for (const [id, label, action] of [['add-close-x', 'Add close X', addCloseButton], ['add-text-block', 'Add text block', addTextBlock]]) $(id).addEventListener('click', () => {
  if (drag || !check()) return;
  if (id === 'add-close-x' && screen.presentation?.mode === 'overlay') { status('Passive overlays have no clickable targets. Use /tideframe close.', true); return; }
  publication.invalidate();
  try {
    const result = action(screen); applyScreen(result.screen, label, result.ids);
    const hidden = result.ids.some(value => page().hiddenIds.includes(value));
    status(hidden ? 'Existing close X selected, but hidden. Show all to include it in screen export.' : `${label} ready. Existing elements preserved.`);
    if (id === 'add-text-block') $('element-text').focus();
  } catch (error) { status(`${label} rejected: ${error.message}`, true); }
});
$('theme-choice').replaceChildren(...THEMES.map(theme => { const option = document.createElement('option'); option.value = theme.id; option.textContent = es(theme.name); return option; }));
$('apply-theme').addEventListener('click', () => {
  if (drag || !check()) return; publication.invalidate();
  try { applyScreen(applyTheme(screen, $('theme-choice').value), 'Apply theme', selectedIds()); status(`Theme applied to the whole screen. Undo restores the previous colors.`); }
  catch (error) { status(`Theme rejected: ${error.message}`, true); }
});
$('apply-theme-selection').addEventListener('click', () => runScreen('Apply theme to selection', ids => applyTheme(screen, $('theme-choice').value, ids)));
$('fill-canvas').addEventListener('click', () => {
  if (!active() || !mutableIds().includes(active().id)) { status('Select a visible, unlocked element to fill the canvas.', true); return; }
  runScreen('Fill complete authored canvas', () => fillCanvas(screen, active().id));
});
// Every setting is always shown, ordered in the inspector tabs; this toggle only draws the buttons' click areas.
$('advanced-toggle').addEventListener('click', () => { advanced = !advanced; $('advanced-toggle').setAttribute('aria-pressed', String(advanced)); $('advanced-toggle').textContent = `Áreas de clic: ${advanced ? 'visibles' : 'ocultas'}`; renderPreview(); });
for (const tab of document.querySelectorAll('[data-tab]')) tab.addEventListener('click', () => showTab(tab.dataset.tab));
function showTab(name) {
  for (const tab of document.querySelectorAll('[data-tab]')) { const on = tab.dataset.tab === name; tab.setAttribute('aria-selected', String(on)); tab.tabIndex = on ? 0 : -1; $(`panel-${tab.dataset.tab}`).hidden = !on; }
}
for (const preset of BLUR_PRESETS) {
  const button = document.createElement('button'); button.type = 'button'; button.className = 'secondary'; button.textContent = BLUR_TEXT[preset.id] ?? preset.label;
  button.addEventListener('click', () => runScreen(preset.label, ids => applyBlurPreset(screen, ids, preset.id)));
  $('blur-presets').append(button);
}
$('delete-element').addEventListener('click', () => runScreen('Delete selection', ids => deleteSelection(screen, ids)));
$('copy-selection').addEventListener('click', copySelected); $('paste-selection').addEventListener('click', pasteSelected); $('cut-selection').addEventListener('click', () => void cutSelected());
// Copy and paste style (idea 32): the look of the active element onto the whole selection. Kept across pages.
function copyStyleSelected() {
  if (!active() || drag) return;
  copiedStyle = copyStyle(active()); status(`Style of ${active().id} copied. Select other elements and paste it.`); check();
}
function pasteStyleSelected() { runScreen('Paste style', ids => pasteStyle(screen, ids, copiedStyle)); }
$('copy-style').addEventListener('click', copyStyleSelected); $('paste-style').addEventListener('click', pasteStyleSelected);
// The project map (idea 31): pages and the buttons that link them; a box takes you to its page.
function showMap() {
  const project = { pages: workspace.project.pages.map(entry => ({ screen: entry === page() ? screen : entry.screen })) };
  const { graph, svg } = mapSvg(project), lost = graph.pages.filter(p => !p.reached).length, plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
  $('map-view').innerHTML = svg;
  $('map-summary').textContent = `${plural(graph.pages.length, 'página', 'páginas')} y ${plural(graph.links.length, 'enlace', 'enlaces')}`
    + `${lost ? `; ${plural(lost, 'página a la que no se llega', 'páginas a las que no se llega')}` : ''}${graph.missing.length ? `; ${plural(graph.missing.length, 'enlace a una página que no existe', 'enlaces a páginas que no existen')}` : ''}.`;
  for (const node of $('map-view').querySelectorAll('[data-page]')) {
    const go = () => { $('map-dialog').close(); if (node.dataset.page !== page().screen.id) runWorkspace('Page selected', () => workspace.setActivePage(node.dataset.page)); };
    node.addEventListener('click', go);
    node.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); go(); } });
  }
  $('map-dialog').showModal();
}
$('project-map').addEventListener('click', () => { if (!drag && check()) showMap(); });
// Original icons (idea 39): pixel art drawn by the editor itself, inserted as a 32-pixel picture.
async function iconPng(id) {
  const { width, height, data } = iconRgba(id, 8), canvas = new OffscreenCanvas(width, height);
  canvas.getContext('2d').putImageData(new ImageData(data, width, height), 0, 0);
  return bytesToBase64(new Uint8Array(await (await canvas.convertToBlob({ type: 'image/png' })).arrayBuffer()));
}
for (const icon of ICONS) {
  const button = document.createElement('button'); button.type = 'button'; button.className = 'icon-pick'; button.title = icon.name; button.setAttribute('aria-label', `Insertar ícono: ${icon.name}`);
  const picture = document.createElement('img'); picture.alt = ''; button.append(picture); $('icon-grid').append(button);
  iconPng(icon.id).then(png => { picture.src = `data:image/png;base64,${png}`; button.dataset.png = png; }, () => { button.disabled = true; });
  button.addEventListener('click', async () => {
    if (!check() || drag || !requireNoDrafts()) return;
    if (screen.elements.length >= 128) { status('Template would exceed the 128 element limit.', true); return; }
    publication.invalidate();
    try { const result = insertIcon(screen, icon.id, button.dataset.png ?? await iconPng(icon.id)); applyScreen(result.screen, 'Insert icon', [result.id]); status(`Icon ${icon.name} inserted as ${result.id}. Move it and resize it in steps of 16 pixels.`); }
    catch (error) { status(`Icon rejected: ${error.message}`, true); }
  });
}
$('map-close').addEventListener('click', () => $('map-dialog').close());
document.addEventListener('keydown', event => {
  if (editableTarget(event.target) || play.active() || !(event.ctrlKey || event.metaKey) || !event.altKey) return;
  const key = event.key.toLowerCase();
  if (key === 'c') { event.preventDefault(); copyStyleSelected(); }
  else if (key === 'v') { event.preventDefault(); if (!event.repeat) pasteStyleSelected(); }
});
$('duplicate-selection').addEventListener('click', () => runScreen('Duplicate selection', ids => duplicateSelection(screen, ids)));
for (const [id, mode] of [['select-all', 'all'], ['select-none', 'none'], ['select-invert', 'invert']]) $(id).addEventListener('click', () => {
  if (!check() || drag || !requireNoDrafts()) return; publication.invalidate(); const excluded = new Set([...page().hiddenIds, ...page().lockedIds]); selectionFromIds(selectSet(screen, selectedIds(), mode).filter(id => !excluded.has(id))); renderList(); renderPreview(); renderInspector(); check();
});
$('hide-selection').addEventListener('click', () => runWorkspace('Selection hidden', () => workspace.setHidden(mutableIds(), true)));
$('show-all').addEventListener('click', () => runWorkspace('All elements visible', () => workspace.setHidden(page().hiddenIds, false)));
$('lock-selection').addEventListener('click', () => runWorkspace('Selection locked', () => workspace.setLocked(mutableIds(), true)));
$('unlock-all').addEventListener('click', () => runWorkspace('All elements unlocked', () => workspace.setLocked(page().lockedIds, false)));
$('group-selection').addEventListener('click', () => runWorkspace('Selection grouped', () => {
  const ids = mutableIds(); if (ids.length < 2) throw Error('Select at least two unlocked, visible elements.'); const wanted = new Set(ids), groups = page().groups.filter(g => !g.members.some(id => wanted.has(id))); let n = 1; while (groups.some(g => g.id === `group${n}`)) n++; workspace.setGroups([...groups, { id: `group${n}`, members: ids }]);
}));
$('ungroup-selection').addEventListener('click', () => runWorkspace('Selection ungrouped', () => { const ids = new Set(mutableIds()); workspace.setGroups(page().groups.filter(g => !g.members.some(id => ids.has(id)))); }));
$('layer-up').addEventListener('click', () => runScreen('Layer up', ids => shiftLayers(screen, ids, 1))); $('layer-down').addEventListener('click', () => runScreen('Layer down', ids => shiftLayers(screen, ids, -1)));
for (const button of document.querySelectorAll('[data-align]')) button.addEventListener('click', () => runScreen(`Align ${button.dataset.align}`, ids => alignSelection(screen, ids, button.dataset.align, { target: $('align-target').value })));
$('distribute-horizontal').addEventListener('click', () => runScreen('Distribute horizontally', ids => distributeSelection(screen, ids, 'horizontal'))); $('distribute-vertical').addEventListener('click', () => runScreen('Distribute vertically', ids => distributeSelection(screen, ids, 'vertical')));
$('layout-selection').addEventListener('click', () => runScreen('Arrange static list', ids => layoutSelection(screen, ids, { axis: $('layout-axis').value, target: $('layout-target').value, gap: Number($('layout-gap').value), padding: Number($('layout-padding').value), maxPerLine: Number($('layout-max').value), sort: $('layout-sort').value, direction: $('layout-direction').value, corner: $('layout-corner').value, align: $('layout-align').value, wrap: $('layout-wrap').checked, descending: $('layout-descending').checked, cellWidth: $('layout-cell-width').value === '' ? null : Number($('layout-cell-width').value), cellHeight: $('layout-cell-height').value === '' ? null : Number($('layout-cell-height').value) })));
function changePreferences(patch) {
  if (drag) return; try { workspace.setPreferences(patch); autoSave(); renderPreferences(); renderPreview(); updateHistory(); } catch (error) { status(`Preference rejected: ${error.message}`, true); renderPreferences(); }
}
for (const input of document.querySelectorAll('[data-pref]')) input.addEventListener('change', () => changePreferences({ [input.dataset.pref]: input.type === 'checkbox' ? input.checked : Number(input.value) }));
$('zoom-control').addEventListener('input', () => changePreferences({ zoom: Number($('zoom-control').value) / 100 })); $('zoom-reset').addEventListener('click', () => changePreferences({ zoom: 1 }));
$('primary-color').addEventListener('input', () => changePreferences({ primaryColor: $('primary-color').value })); $('secondary-color').addEventListener('input', () => changePreferences({ secondaryColor: $('secondary-color').value }));
$('swap-colors').addEventListener('click', () => changePreferences({ primaryColor: prefs().secondaryColor, secondaryColor: prefs().primaryColor }));
$('pick-selected-color').addEventListener('click', () => { if (!drag && active() && check()) { changePreferences({ primaryColor: active().style.fillColor }); status('Selected element fill copied to the primary palette.'); } });
for (const [id, key] of [['apply-primary', 'primaryColor'], ['apply-secondary', 'secondaryColor']]) $(id).addEventListener('click', () => runScreen('Apply fill color', ids => { const wanted = new Set(ids), next = cloneScreen(screen); next.elements = next.elements.map(e => wanted.has(e.id) ? { ...e, background: true, style: { ...e.style, fillColor: prefs()[key] } } : e); return next; }));
function updateQuality() {
  if (drag) finishDrag(); publication.invalidate(); const preset = $('quality-preset').value, width = Number($('quality-width').value); screen = { ...screen, quality: preset === 'custom' ? { preset, width, height: width * 9 / 16 } : { ...QUALITY_PRESETS[preset] } };
  $('custom-quality').hidden = preset !== 'custom'; $('quality-dimensions').value = `${screen.quality.width} x ${screen.quality.height}`; commitDraft('Change quality');
}
$('quality-preset').addEventListener('change', updateQuality); $('quality-width').addEventListener('input', updateQuality);
for (const scope of ['screen', 'element']) {
  const box = $(`${scope}-logic`);
  box.addEventListener('input', () => { rawLogicDrafts.add(box.id); publication.invalidate(); updateDraftNotice(); });
  $(`apply-${scope}-logic`).addEventListener('click', () => {
    if (scope === 'element' && (!active() || isLocked(active().id))) return;
    applyLogic(box, logic => scope === 'screen' ? setScreenLogic(screen, logic) : setElementLogic(screen, active().id, logic), `Edit ${scope} logic`);
  });
  $(`revert-${scope}-logic`).addEventListener('click', () => {
    rawLogicDrafts.delete(box.id); box.value = JSON.stringify((scope === 'screen' ? screen : active())?.logic ?? null, null, 2);
    if (box.value === 'null') box.value = ''; box.removeAttribute('aria-invalid'); updateDraftNotice(); status('JSON draft reverted.');
  });
}
for (const button of document.querySelectorAll('[data-element-preset]')) button.addEventListener('click', () => {
  if (!active() || isLocked(active().id) || drag || !check()) return;
  const id = active().id, patch = ELEMENT_PRESETS[button.dataset.elementPreset];
  try { publication.invalidate(); applyScreen(setElementLogic(screen, id, mergeLogic(active().logic, patch)), 'Logic preset'); status(`Preset applied to ${id} (schema 9). Edit the JSON below to fine-tune it.`); render(); }
  catch (error) { status(error.message, true); }
});
for (const button of document.querySelectorAll('[data-screen-preset]')) button.addEventListener('click', () => {
  if (drag || !check()) return;
  try { publication.invalidate(); applyScreen(setScreenLogic(screen, mergeLogic(screen.logic, SCREEN_PRESETS[button.dataset.screenPreset])), 'Animation preset'); status('Open/close animation applied (schema 9).'); render(); }
  catch (error) { status(error.message, true); }
});
$('element-tooltip').addEventListener('change', event => {
  if (!active() || isLocked(active().id) || drag || !check()) return;
  const id = active().id, logic = mergeLogic(active().logic, {});
  if (event.target.value) logic.tooltip = event.target.value; else delete logic.tooltip;
  try { publication.invalidate(); applyScreen(setElementLogic(screen, id, Object.keys(logic).length ? logic : null), 'Tooltip'); status(event.target.value ? `${event.target.value} now shows only while the cursor is on ${id}.` : 'Tooltip removed.'); render(); }
  catch (error) { status(error.message, true); renderInspector(); }
});
$('inspector-form').addEventListener('submit', event => event.preventDefault());
$('inspector-form').addEventListener('input', event => {
  if (event.target.closest('.advanced-settings')) return; // These controls commit atomically through their own validator.
  if (!active() || isLocked(active().id)) return; const { field, condition: conditionField, style: styleField, area: areaField } = event.target.dataset; publication.invalidate();
  if (styleField) {
    if ((SLICE_FIELDS.includes(styleField) || styleField === 'imageClip') && screen.version < 9) screen = upgradeLogic(screen);
    if ((styleField === 'textCase' || BLUR_FIELDS.includes(styleField)) && screen.version < 9) screen = upgradeLogic(screen);
    else if (gradientFields.has(styleField) && screen.version < 8) screen = upgradeGradient(screen);
    else if (textStyleFields.has(styleField) && screen.version < 7) screen = upgradeTextStyle(screen);
    else if (styleField === 'hoverEffect' && event.target.value === 'glow' && screen.version < 5) screen = upgradeAppearance(screen);
    else if (styleField === 'brightness' && screen.version < 6) screen = upgradePresentation(screen);
    else if (appearanceFields.has(styleField) && screen.version < 5) screen = upgradeAppearance(screen);
    const value = event.target.type === 'checkbox' ? event.target.checked : styleNumbers.has(styleField) ? (event.target.value === '' ? NaN : Number(event.target.value)) : event.target.value;
    changeSelection(e => ({ style: { ...e.style, [styleField]: value } }), 'Edit appearance');
  } else if (areaField) changeActive({ actionArea: { ...effectiveActionArea(active()), [areaField]: event.target.value === '' ? NaN : Number(event.target.value) } }, 'Edit action area');
  else if (conditionField) changeActive({ messageCondition: { ...(active().messageCondition ?? defaultCondition()), [conditionField]: event.target.value } }, 'Edit condition');
  else if (field) {
    const value = event.target.type === 'checkbox' ? event.target.checked : numberFields.has(field) ? (event.target.value === '' ? NaN : Number(event.target.value)) : event.target.value;
    const change = { [field]: field === 'text' ? normalizeText(value) : value };
    if (field === 'text' && extendedText(change.text) && screen.version < 9) screen = upgradeLogic(screen);
    else if (field === 'text' && (value.length > 64 || value.includes('\n')) && screen.version < 5) screen = upgradeAppearance(screen);
    if (field === 'kind' && value === 'decoration') Object.assign(change, { action: '', messageCondition: null, actionArea: null, border: false, background: false, text: '' });
    if (field === 'kind' && value === 'label' && active().action === 'conditional-message') Object.assign(change, { action: '', messageCondition: null });
    if (field === 'action') change.messageCondition = value === 'conditional-message' ? (active().messageCondition ?? defaultCondition()) : null;
    if (sharedChange({ field })) changeSelection(() => change); else changeActive(change);
    if (field === 'kind' || field === 'action') renderInspector();
  } else return;
  $('schema-version').textContent = `V${screen.version}`; event.target.setAttribute('aria-invalid', String(!valid));
});
$('hover-color-enabled').addEventListener('change', event => { if (!active() || isLocked(active().id)) return; publication.invalidate(); if (screen.version < 5) screen = upgradeAppearance(screen); changeSelection(e => ({ style: { ...e.style, hoverColor: event.target.checked ? $('style-hoverColor').value : null } }), 'Edit hover color'); renderInspector(); });
$('text-outline-enabled').addEventListener('change', event => { if (!active() || isLocked(active().id)) return; publication.invalidate(); if (screen.version < 7) screen = upgradeTextStyle(screen); changeSelection(e => ({ style: { ...e.style, textOutline: event.target.checked ? $('style-textOutline').value : null } }), 'Edit text outline'); renderInspector(); });
for (const [toggle, field, input] of [['gradient-enabled', 'gradientColor', 'style-gradientColor'], ['text-gradient-enabled', 'textGradient', 'style-textGradient']])
  $(toggle).addEventListener('change', event => { if (!active() || isLocked(active().id)) return; publication.invalidate(); if (screen.version < 8) screen = upgradeGradient(screen); changeSelection(e => ({ ...(field === 'gradientColor' && event.target.checked ? { background: true } : {}), style: { ...e.style, [field]: event.target.checked ? $(input).value : null } }), field === 'gradientColor' ? 'Edit gradient fill' : 'Edit text gradient'); renderInspector(); });
$('custom-action-area').addEventListener('change', event => { if (active()?.kind !== 'button' || isLocked(active().id)) return; publication.invalidate(); changeActive({ actionArea: event.target.checked ? effectiveActionArea(active()) : null }); renderInspector(); });
$('conditional-message').addEventListener('change', event => { if (active()?.kind !== 'button' || isLocked(active().id)) return; publication.invalidate(); changeActive(event.target.checked ? { action: 'conditional-message', messageCondition: active().messageCondition ?? defaultCondition() } : { action: '', messageCondition: null }); renderInspector(); });
$('load-image').addEventListener('click', () => { if (active() && !isLocked(active().id)) { publication.invalidate(); $('image-file').click(); } });
// The item icon field: a game item id (the list comes from the plugin's table); empty removes the icon.
$('element-item').addEventListener('change', () => {
  if (!active() || isLocked(active().id)) return;
  const value = $('element-item').value.trim().toLowerCase();
  if (value && (!/^[a-z0-9_]{1,64}$/.test(value) || itemIcons && !itemIcons[value])) { status(`El juego no tiene un ítem ${value}. Elegilo de la lista, por ejemplo diamond.`, true); $('element-item').value = active().item ?? ''; return; }
  if ((active().item ?? '') === value) return;
  publication.invalidate();
  if (value && screen.version < 9) screen = upgradeLogic(screen);
  const { item, ...rest } = active();
  screen = { ...screen, elements: screen.elements.map((e, i) => i === activeIndex ? (value ? { ...rest, item: value } : rest) : e) };
  commitDraft(value ? 'Set item icon' : 'Remove item icon'); renderList(); renderPreview(); renderInspector();
});
$('element-item').addEventListener('focus', () => renderPreview());
$('element-item').addEventListener('blur', () => renderPreview());
$('remove-image').addEventListener('click', () => { publication.invalidate(); changeActive({ image: null }, 'Remove image'); renderInspector(); });
$('load-hover-image').addEventListener('click', () => { if (active() && !isLocked(active().id) && active().kind === 'button') { publication.invalidate(); $('hover-image-file').click(); } });
$('remove-hover-image').addEventListener('click', () => { if (!active()) return; publication.invalidate(); const { hoverImage, ...rest } = active(); screen = { ...screen, elements: screen.elements.map((e, i) => i === activeIndex ? rest : e) }; commitDraft('Remove hover image'); render(); });
$('hover-image-file').addEventListener('change', async event => {
  const file = event.target.files?.[0]; if (!file || !active() || isLocked(active().id) || active().kind !== 'button' || !check()) return; const index = activeIndex, pageId = workspace.project.activePageId; event.target.value = '';
  if (screen.version < 9) screen = upgradeLogic(screen);
  const base = screen;
  await publication.run(() => loadImageFile(file, { quality: base.quality }), { pending: `Decoding hover image at up to ${base.quality.width} pixels...`, failure: 'Hover image rejected', report: status,
    apply(hoverImage) { if (screen !== base || activeIndex !== index || workspace.project.activePageId !== pageId || isLocked(active().id)) return false; validateScreenBudget(editElementDraft(screen, index, { hoverImage })); changeActive({ hoverImage }, 'Load hover image'); renderInspector(); },
    success: image => { const png = validatePngBase64(image); return `Loaded ${file.name} as the hover image (${png.width} x ${png.height} PNG).`; }
  });
});
$('image-file').addEventListener('change', async event => {
  const file = event.target.files?.[0]; if (!file || !active() || isLocked(active().id) || !check()) return; const index = activeIndex, base = screen, pageId = workspace.project.activePageId; event.target.value = '';
  await publication.run(() => loadImageFile(file, { quality: base.quality }), { pending: `Decoding photo at up to ${base.quality.width} pixels...`, failure: 'Image rejected', report: status,
    apply(image) { if (screen !== base || activeIndex !== index || workspace.project.activePageId !== pageId || isLocked(active().id)) return false; validateScreenBudget(editElementDraft(screen, index, { image })); changeActive({ image }, 'Load image'); renderInspector(); },
    success: image => { const png = validatePngBase64(image); return `Loaded ${file.name} as static ${png.width} x ${png.height} PNG. Clear Text for an image-only element.`; }
  });
});
for (const [input, field] of [['screen-id', 'id'], ['screen-title', 'title']]) $(input).addEventListener('input', event => {
  if (field === 'id' && !requireNoDrafts()) { event.target.value = screen.id; return; }
  publication.invalidate(); const value = field === 'title' ? normalizeText(event.target.value) : event.target.value;
  if (field === 'title' && extendedText(value) && screen.version < 9) screen = upgradeLogic(screen);
  screen = { ...screen, [field]: value }; commitDraft('Rename page'); event.target.setAttribute('aria-invalid', String(!valid));
});
$('stage').addEventListener('pointerdown', event => {
  if (!requireNoDrafts()) return;
  const target = event.target.closest('[data-index]'), mode = pointerIntent({ button: event.button, shiftKey: event.shiftKey, capsLock }); if (!target || !mode || !check()) return;
  const index = Number(target.dataset.index); select(index, mode === 'select', mode !== 'select'); if (mode === 'select') { event.preventDefault(); return; }
  const ids = mutableIds(); if (!ids.length || isLocked(screen.elements[index].id)) { status('This element is locked. Unlock it before editing.', true); return; }
  drag = { pointer: event.pointerId, mode, start: { x: event.clientX, y: event.clientY }, base: cloneScreen(screen), ids }; $('stage').setPointerCapture(event.pointerId); renderPreview(true); event.preventDefault();
});
$('stage').addEventListener('pointermove', event => {
  const bounds = $('stage').getBoundingClientRect();
  cursorPoint = { x: clamp((event.clientX - bounds.left) / Math.max(1, bounds.width) * 2 - 1), y: clamp(1 - (event.clientY - bounds.top) / Math.max(1, bounds.height) * 2) };
  const cursor = $('stage').querySelector('.game-cursor-preview');
  if (cursor) { cursor.style.left = `${(cursorPoint.x + 1) * 50}%`; cursor.style.top = `${(1 - cursorPoint.y) * 50}%`; }
  if (!drag || drag.pointer !== event.pointerId) return;
  try {
    const rect = $('stage').getBoundingClientRect();
    if (drag.mode === 'move' && (prefs().snapEnabled || prefs().snapObjects || prefs().smartGuides)) {
      const result = snapSelection(drag.base, drag.ids, (event.clientX - drag.start.x) / rect.width * 2, -(event.clientY - drag.start.y) / rect.height * 2, { grid: prefs().snapStep, gridEnabled: prefs().snapEnabled, objects: prefs().snapObjects, canvas: prefs().smartGuides, threshold: Math.min(.1, 12 / Math.max(1, rect.width)) }); screen = result.screen; guides = result.guides;
    } else { screen = applyPointerGesture(drag.base, drag.ids, drag.mode, drag.start, { x: event.clientX, y: event.clientY }, rect); guides = []; }
    renderInspector(); renderPreview(); check();
  } catch (error) { status(`Gesture rejected: ${error.message}`, true); }
});
$('stage').addEventListener('pointerup', event => { if (drag?.pointer === event.pointerId) finishDrag(true); });
$('stage').addEventListener('pointercancel', event => { if (drag?.pointer === event.pointerId) finishDrag(false, true); });
$('stage').addEventListener('lostpointercapture', event => { if (drag?.pointer === event.pointerId) finishDrag(); });
$('stage').addEventListener('keydown', event => {
  const target = event.target.closest('[data-index]'); if (!target || drag) return; const index = Number(target.dataset.index);
  if (event.key === ' ' || event.key === 'Enter') { event.preventDefault(); select(index, event.shiftKey); renderPreview(true); return; }
  const delta = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, 1], ArrowDown: [0, -1] }[event.key]; if (!delta) return;
  event.preventDefault(); select(index, false, true); const step = event.shiftKey ? .1 : .02; runScreen('Move selection', ids => moveSelection(screen, ids, delta[0] * step, delta[1] * step)); renderPreview(true);
});
for (const type of ['keydown', 'keyup', 'pointerdown', 'pointermove', 'pointerup']) document.addEventListener(type, updateCapsLock, { capture: true });
window.addEventListener('blur', () => { updateCapsLock(null); finishDrag(); });
function undo() {
  if (!requireNoDrafts()) return;
  if (drag) { finishDrag(false, true); return; } publication.invalidate();
  if (!valid) { hydrate(); status('Invalid draft discarded. Last valid edit restored.'); return; }
  if (workspace.undo()) { hydrate(); autoSave(); status('Undo applied.'); }
}
function redo() { if (!requireNoDrafts()) return; if (!drag && valid && workspace.redo()) { publication.invalidate(); hydrate(); autoSave(); status('Redo applied.'); } }
$('undo').addEventListener('click', undo); $('redo').addEventListener('click', redo);
document.addEventListener('keydown', event => {
  if (editableTarget(event.target) || event.altKey || play.active()) return; const command = event.ctrlKey || event.metaKey, key = event.key.toLowerCase();
  if (command && ['c', 'v', 'x'].includes(key)) return;
  if (command && key === 'z') { event.preventDefault(); if (!event.repeat) event.shiftKey ? redo() : undo(); }
  else if (command && key === 'y') { event.preventDefault(); if (!event.repeat) redo(); }
  else if (command && key === 'd') { event.preventDefault(); if (!event.repeat) $('duplicate-selection').click(); }
  else if (command && key === 'a') { event.preventDefault(); $('select-all').click(); }
  else if (!command && event.key === 'Delete') { event.preventDefault(); if (!event.repeat) $('delete-element').click(); }
  else if (event.key === 'Escape' && drag) { event.preventDefault(); finishDrag(false, true); }
});
// Probar never changes the project: the clipboard shortcuts stay off while it runs.
document.addEventListener('copy', event => {
  if (play.active()) return;
  if (editableTarget(event.target)) { preferInternalClipboard = false; return; } if (drag || !check() || !selectedIndices.length) return;
  try { const raw = encodeClipboard(screen, selectedIds()); if (copyEventPayload(event, raw)) { internalClipboard = raw; preferInternalClipboard = false; status('Selection copied to the clipboard.'); } else void copySelected(); } catch (error) { status(`Copy rejected: ${error.message}`, true); }
});
document.addEventListener('cut', event => { if (!play.active() && !editableTarget(event.target)) void cutSelected(event); });
document.addEventListener('paste', event => {
  if (play.active()) { if (!editableTarget(event.target)) event.preventDefault(); return; }
  if (editableTarget(event.target)) return; if (drag || !check()) { event.preventDefault(); return; }
  try { const source = pasteEventPayload(event, { internal: internalClipboard, preferInternal: preferInternalClipboard }); if (source) void applyPaste(source); else void pasteSelected(); } catch (error) { status(`Paste rejected: ${error.message}`, true); }
});
$('new-project').addEventListener('click', () => runWorkspace('New project', () => workspace.closeAll({ persistBackup: true })));
$('add-page').addEventListener('click', () => runWorkspace('Page added', () => workspace.addPage()));
// Página desde plantilla (idea 14): a whole page, with a free id when the template's is taken.
$('page-template').replaceChildren(...PAGE_TEMPLATES.map(entry => new Option(entry.name, entry.id)));
$('add-page-template').addEventListener('click', () => runWorkspace('Page added', () => workspace.addPage(pageFromTemplate($('page-template').value, workspace.project.pages.map(entry => entry.screen.id))))); $('duplicate-page').addEventListener('click', () => runWorkspace('Page duplicated', () => workspace.duplicatePage()));
$('close-page').addEventListener('click', () => runWorkspace('Page closed with local backup', () => workspace.closePage(undefined, { persistBackup: true })));
$('save-project').addEventListener('click', () => { if (!check() || drag || !requireNoDrafts()) return; const result = autoSave(); status(result.message, !result.ok); });
$('download-project').addEventListener('click', () => { if (!check() || drag) return; try { download(workspace.exportProject(), `${screen.id}.tideframe-project.json`); status('Project download requested. Preserve this file to keep all pages, groups, hidden elements and locks.'); } catch (error) { status(error.message, true); } });
$('save-as-project').addEventListener('click', async () => {
  if (!check() || drag || !requireNoDrafts()) return;
  try {
    const raw = workspace.exportProject(), filename = `${screen.id}.tideframe-project.json`;
    if (!window.showSaveFilePicker) { download(raw, filename); status('Project download requested. This browser does not offer a native Save As picker.'); return; }
    const handle = await window.showSaveFilePicker({ suggestedName: filename, types: [{ description: 'Tideframe project', accept: { 'application/json': ['.json'] } }] }); const writable = await handle.createWritable(); await writable.write(raw); await writable.close(); status(`Project saved to ${handle.name}.`);
  } catch (error) { if (error.name !== 'AbortError') status(`Save As failed: ${error.message}`, true); }
});
$('open-project').addEventListener('click', () => { if (check() && !drag) { publication.invalidate(); $('project-file').click(); } });
$('project-file').addEventListener('change', async event => {
  const file = event.target.files?.[0]; if (!file || !check() || drag) return; const base = screen; event.target.value = '';
  await publication.run(async () => { if (file.size > MAX_BYTES) throw Error('Project exceeds 128 MiB.'); return parseProjectForPublication(await file.text()); }, {
    pending: 'Checking project, every page and PNG pixels...', failure: 'Open rejected', report: status,
    apply(project) { if (screen !== base || !requireNoDrafts()) return false; workspace.loadProject(project, { persistBackup: true }); hydrate(); autoSave(); }, success: () => 'Project opened. Previous project backed up locally.'
  });
});
$('import').addEventListener('click', () => { if (check() && !drag) { publication.invalidate(); $('import-file').click(); } });
$('import-file').addEventListener('change', async event => {
  const file = event.target.files?.[0]; if (!file || !check() || drag) return; const base = screen; event.target.value = '';
  await publication.run(async () => { if (file.size > MAX_BYTES) throw Error('JSON exceeds 128 MiB.'); return parseScreenForPublication(await file.text()); }, {
    pending: 'Checking screen JSON and PNG pixels...', failure: 'Import rejected', report: status,
    apply(next) { if (screen !== base || !requireNoDrafts()) return false; workspace.addPage(next); hydrate([]); autoSave(); }, success: () => `Imported ${file.name} as a new page. Existing pages preserved.`
  });
});
// Publish appears only with a selected server/screens folder (it embeds a per-run token).
const publishToken = document.querySelector('meta[name="tideframe-publish"]')?.content ?? '';
$('publish').hidden = !publishToken;
$('open-in-game').hidden = !publishToken;
$('open-in-game').addEventListener('click', () => { if (publishToken && !drag) void openInGame(rememberedPlayer()); });
$('player-open').addEventListener('click', confirmPlayer);
$('player-name').addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); confirmPlayer(); } });
$('player-cancel').addEventListener('click', () => $('player-dialog').close());
for (const control of ['upload-files', 'upload-name', 'upload-send']) $(control).disabled = !publishToken;
if (!publishToken) $('upload-help').textContent = 'Para subir archivos, abrí el editor conectado al servidor (node tools/tideframe-editor/serve.mjs --server ...).';
// Revisar (idea 11): warnings about how the pages will look and behave in the game; Publish shows them first.
async function reviewFindings() {
  let frames = null;
  if (publishToken) try { const listed = await (await fetch('/server-images/')).json(); frames = new Set(listed.folders); } catch { frames = null; }
  return reviewProject({ pages: workspace.project.pages.map(entry => ({ screen: exportVisibleScreen(entry) })) }, { frames, icons: itemIcons });
}
function showReview(found, forPublish) {
  const warnings = found.filter(f => f.level === 'warning').length, notes = found.length - warnings;
  $('review-summary').value = found.length ? `${warnings} aviso${warnings === 1 ? '' : 's'} y ${notes} nota${notes === 1 ? '' : 's'}.` : 'No hay nada para revisar: todo se ve bien.';
  $('review-list').replaceChildren(...found.map(entry => {
    const item = document.createElement('li'), button = document.createElement('button');
    item.dataset.level = entry.level; button.type = 'button'; button.className = 'secondary';
    button.textContent = `${entry.level === 'warning' ? 'Aviso' : 'Nota'} · ${entry.page}${entry.element ? ` / ${entry.element}` : ''}: ${entry.message}`;
    button.addEventListener('click', () => {
      $('review-dialog').close();
      if (entry.page !== page().screen.id) runWorkspace('Page selected', () => workspace.setActivePage(entry.page));
      const index = screen.elements.findIndex(e => e.id === entry.element);
      if (index >= 0) { select(index); renderPreview(true); }
    });
    item.append(button); return item;
  }));
  $('review-publish').hidden = !forPublish;
  $('review-dialog').showModal();
}
// Custom sounds (idea 30): the server's sounds folder, listened to here and named tideframe:NAME in the game.
async function refreshSounds() {
  const list = $('sound-list');
  if (!publishToken) { list.replaceChildren(Object.assign(document.createElement('p'), { className: 'hint', textContent: 'Abrí el editor conectado al servidor para ver y subir sonidos.' })); return; }
  try {
    const { sounds } = await (await fetch('/server-sounds')).json();
    list.replaceChildren(...(sounds.length ? sounds.map(sound => {
      const row = document.createElement('div'); row.className = 'sound-row';
      const name = document.createElement('span'); name.textContent = sound.id; name.title = `${Math.round(sound.bytes / 1024)} KB, ${sound.channels === 1 ? 'mono' : 'estéreo'}`;
      const listen = document.createElement('button'); listen.type = 'button'; listen.className = 'secondary'; listen.textContent = 'Escuchar';
      listen.addEventListener('click', () => { void new Audio(`/server-sounds/${sound.name}.ogg`).play().catch(() => status('El navegador no pudo reproducir el sonido.', true)); });
      const copy = document.createElement('button'); copy.type = 'button'; copy.className = 'secondary'; copy.textContent = 'Copiar';
      copy.addEventListener('click', async () => { try { await navigator.clipboard.writeText(sound.id); status(`${sound.id} copiado: pegalo en una acción de sonido.`); } catch { status(sound.id); } });
      row.append(name, listen, copy); return row;
  }) : [Object.assign(document.createElement('p'), { className: 'hint', textContent: 'Todavía no hay sonidos en plugins/BigCasares/tideframe/engine/sounds.' })]));
  } catch { list.replaceChildren(Object.assign(document.createElement('p'), { className: 'hint', textContent: 'No se pudo leer la lista de sonidos del servidor.' })); }
}
$('sound-picker').addEventListener('toggle', () => { if ($('sound-picker').open) void refreshSounds(); });
for (const control of ['sound-file', 'sound-name', 'sound-send']) $(control).disabled = !publishToken;
$('sound-file').addEventListener('change', () => {
  const file = $('sound-file').files?.[0];
  if (file && !$('sound-name').value) $('sound-name').value = file.name.replace(/\.ogg$/i, '').toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 32);
});
async function sendSound(name, file, replace) {
  const response = await fetch(`/upload-sound?name=${encodeURIComponent(name)}${replace ? '&replace=1' : ''}`, { method: 'POST', headers: { 'content-type': 'audio/ogg', 'x-tideframe-token': publishToken }, body: file });
  return { response, result: await response.json().catch(() => ({ ok: false, error: `HTTP ${response.status}` })) };
}
$('sound-send').addEventListener('click', async () => {
  if (!publishToken) return;
  const file = $('sound-file').files?.[0], name = $('sound-name').value.trim();
  if (!file) { status('Elegí un archivo .ogg.', true); return; }
  if (!/^[a-z0-9_]{1,32}$/.test(name)) { status('El nombre del sonido usa a-z, 0-9 y _, hasta 32 caracteres (por ejemplo clic).', true); return; }
  $('sound-send').disabled = true;
  try {
    let { response, result } = await sendSound(name, file, false);
    if (response.status === 409 && window.confirm(`Ya hay un sonido "${name}" en el servidor. ¿Reemplazarlo?`)) ({ response, result } = await sendSound(name, file, true));
    if (!response.ok || !result.ok) throw Error(result.error ?? `HTTP ${response.status}`);
    status(`Sonido ${result.id} subido (${result.channels === 1 ? 'mono' : 'estéreo'}). Publicá o recargá para que entre al pack.`);
    $('sound-file').value = ''; $('sound-name').value = ''; void refreshSounds();
  } catch (error) { status(`No se pudo subir el sonido: ${es(error.message)}`, true); }
  finally { $('sound-send').disabled = false; }
});
// Subir una animación (idea 15): files go to the server's images folder, then the element points at them.
$('upload-files').addEventListener('change', () => {
  const first = $('upload-files').files?.[0];
  if (first && !$('upload-name').value) $('upload-name').value = first.name.replace(/\.(gif|png)$/i, '').toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^[^a-z0-9]+/, '').slice(0, 32);
});
async function sendImage(name, file, replace) {
  const response = await fetch(`/upload-image?name=${encodeURIComponent(name)}${replace ? '&replace=1' : ''}`, { method: 'POST', headers: { 'content-type': file.type || 'application/octet-stream', 'x-tideframe-token': publishToken }, body: file });
  return { response, result: await response.json().catch(() => ({ ok: false, error: `HTTP ${response.status}` })) };
}
$('upload-send').addEventListener('click', async () => {
  if (!publishToken || !active() || isLocked(active().id) || drag || !check() || !requireNoDrafts()) return;
  const files = [...($('upload-files').files ?? [])], name = $('upload-name').value.trim();
  if (!files.length) { status('Elegí un GIF o los PNG de la animación.', true); return; }
  if (!/^[a-z0-9_-]{1,32}$/.test(name)) { status('El nombre usa a-z, 0-9, _ y -, hasta 32 caracteres (por ejemplo fuego).', true); return; }
  const gifs = files.filter(f => /\.gif$/i.test(f.name)), pngs = files.filter(f => /\.png$/i.test(f.name)).sort((a, b) => a.name.localeCompare(b.name, 'en', { numeric: true }));
  if (gifs.length + pngs.length !== files.length || gifs.length > 1 || gifs.length && pngs.length) { status('Subí un solo GIF, o solo archivos PNG.', true); return; }
  const id = active().id; $('upload-send').disabled = true;
  try {
    const plan = gifs.length ? [[name, gifs[0]]] : pngs.map((file, i) => [`${name}/${String(i + 1).padStart(3, '0')}.png`, file]);
    let replace = false;
    for (const [target, file] of plan) {
      status(`Subiendo ${target}...`);
      let { response, result } = await sendImage(target, file, replace);
      if (response.status === 409 && !replace && window.confirm(`Ya hay una animación "${name}" en el servidor. ¿Reemplazarla?`)) { replace = true; ({ response, result } = await sendImage(target, file, true)); }
      if (!response.ok || !result.ok) throw Error(result.error ?? `HTTP ${response.status}`);
    }
    applyScreen(writeSetting(screen, 'element', 'frames.folder', name, id), 'Edit advanced settings', [id], true);
    status(`Animación "${name}" subida (${plan.length} archivo${plan.length === 1 ? '' : 's'}) y puesta en ${id}. Publicá para que el servidor la tome.`);
  } catch (error) { status(`No se pudo subir: ${es(error.message)}`, true); }
  finally { $('upload-send').disabled = false; }
});
$('review').addEventListener('click', async () => { if (!check() || drag || !requireNoDrafts()) return; showReview(await reviewFindings(), false); });
$('review-close').addEventListener('click', () => $('review-dialog').close());
$('review-publish').addEventListener('click', () => { $('review-dialog').close(); void publishNow(); });
$('publish').addEventListener('click', async () => {
  if (!publishToken || !check() || drag || !requireNoDrafts()) return;
  const found = await reviewFindings();
  if (found.some(entry => entry.level === 'warning')) { showReview(found, true); return; }
  await publishNow();
});
// What the plugin reports once a load is live (EditorBridge.java): its screens, their warnings and file times, or the failure.
async function serverStatus() { try { return await (await fetch('/server-status')).json(); } catch { return { ok: null }; } }
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let reloadWatch = 0, openWatch = 0;
/** After Publicar: waits until the server runs the published file (its time appears in "stamps"), then shows its warnings. */
async function waitForReload(id, file, stamp, since) {
  const watch = ++reloadWatch;
  let failed = false;
  for (let waited = 0; waited <= 120000; waited += 1000) {
    const report = await serverStatus();
    if (watch !== reloadWatch) return;
    // A Tideframe older than this editor writes no status: say what to do instead of waiting.
    if (report.ok === null && waited === 0) { status(`Publicada ${file}. Cuando el servidor la recargue, aceptá el pack nuevo y abrila con /tideframe ${id}.`); return; }
    if (report.stamps?.[id] === stamp) {
      const warnings = (report.problems?.[id] ?? []).map(es);
      status(`El servidor ya usa ${id}${warnings.length ? `, con avisos: ${warnings.join(' · ')}` : ''}. En el juego, aceptá el pack nuevo o tocá "Abrir en el juego".`, warnings.length > 0);
      return;
    }
    // A failed load keeps the previous screens; a later load may still take this file, so the wait goes on.
    if (report.ok === false && report.time >= since && !failed) { failed = true; status(`El servidor no pudo cargar los cambios: ${es(report.error ?? '')}. Sigue activa la versión anterior.`, true); }
    await pause(1000);
  }
  if (!failed) status(`El servidor todavía no cargó ${id}. ¿Está prendido, con editor.auto-reload: true?`, true);
}
function rememberedPlayer() { try { return localStorage.getItem('tideframe-player') ?? ''; } catch { return ''; } }
/** Abrir en el juego: the plugin opens the published page for the only player online, or for the one named. */
async function openInGame(player) {
  const watch = ++openWatch, id = page().screen.id;
  if ((await serverStatus()).ok === null) { status('Este Tideframe no atiende pedidos del editor: hace falta la versión 0.23 o más nueva, prendida.', true); return; }
  let request;
  try {
    const response = await fetch('/open-in-game', { method: 'POST', headers: { 'content-type': 'application/json', 'x-tideframe-token': publishToken }, body: JSON.stringify({ screen: id, ...(player ? { player } : {}) }) });
    const result = await response.json().catch(() => ({ ok: false, error: `HTTP ${response.status}` }));
    if (!response.ok || !result.ok) throw new Error(result.error ?? `HTTP ${response.status}`);
    request = result.request;
  } catch (error) { status(`No se pudo pedir que la abra: ${es(error.message)}`, true); return; }
  status(`Pidiendo al servidor que abra ${id}${player ? ` para ${player}` : ''}...`);
  for (let waited = 0; waited < 10000; waited += 500) {
    await pause(500);
    const answer = String((await serverStatus()).open ?? '');
    if (watch !== openWatch) return;
    if (answer.slice(0, answer.indexOf(' ')) !== request) continue;
    const text = answer.slice(answer.indexOf(' ') + 1);
    if (text.startsWith('several players')) { askPlayer('Hay varios jugadores conectados. ¿Para quién la abro?'); return; }
    if (text.startsWith('player ')) { askPlayer(`${player} no está conectado. Escribí otro nombre, o dejalo vacío si sos el único conectado.`); return; }
    status(es(text), !text.startsWith('opened'));
    return;
  }
  status('El servidor no respondió el pedido. ¿Está prendido?', true);
}
function askPlayer(note) {
  status(note, true);
  $('player-note').textContent = note;
  $('player-name').value = rememberedPlayer();
  $('player-dialog').showModal();
  $('player-name').select();
}
function confirmPlayer() {
  const name = $('player-name').value.trim();
  if (name && !/^[A-Za-z0-9_]{1,16}$/.test(name)) { $('player-note').textContent = 'El nombre del juego tiene de 1 a 16 letras, números o _.'; return; }
  try { if (name) localStorage.setItem('tideframe-player', name); else localStorage.removeItem('tideframe-player'); } catch { /* it is asked again next time */ }
  $('player-dialog').close();
  void openInGame(name);
}
async function publishNow() {
  if (!publishToken || !check() || drag || !requireNoDrafts()) return;
  let raw; try { raw = workspace.exportVisibleJson(); } catch (error) { status(`Publish failed: ${error.message}`, true); return; }
  $('publish').disabled = true; status(`Publishing ${screen.id}.json to the server...`);
  const since = Date.now();
  try {
    const response = await fetch('/publish', { method: 'POST', headers: { 'content-type': 'application/json', 'x-tideframe-token': publishToken }, body: raw });
    const result = await response.json().catch(() => ({ ok: false, error: `HTTP ${response.status}` }));
    if (!response.ok || !result.ok) throw new Error(result.error ?? `HTTP ${response.status}`);
    status(`Publicada ${result.file}. Esperando que el servidor la cargue...`);
    void waitForReload(result.id, result.file, result.stamp, since);
  } catch (error) { status(`Publish failed: ${error.message}`, true); }
  finally { $('publish').disabled = false; }
}
$('export').addEventListener('click', () => { if (!check() || drag) return; try { download(workspace.exportVisibleJson(), `${screen.id}.json`); status(`Export requested for ${screen.id}.json. Hidden elements omitted. Install in plugins/BigCasares/tideframe/engine/screens/.`); } catch (error) { status(`Export failed: ${error.message}`, true); } });
$('export-all').addEventListener('click', () => {
  if (!check() || drag) return; try { const pages = workspace.project.pages.map(entry => exportVisibleScreen(entry)); for (const entry of pages) download(serializeScreen(entry), `${entry.id}.json`); status(`Requested ${pages.length} separate JSON downloads. Your browser may require permission for multiple downloads. Hidden elements omitted.`); } catch (error) { status(`Export failed: ${error.message}`, true); }
});
$('recover-project').addEventListener('click', () => {
  if (!check() || drag) return;
  try {
    const options = [{ id: 'local', name: 'Último guardado en el navegador' }, ...workspace.listBackups().slice().reverse().map(entry => ({ id: entry.id, name: `${new Date(entry.createdAt).toLocaleString('es-AR')} / ${es(entry.label)}` }))];
    $('recovery-choice').replaceChildren(...options.map(entry => { const option = document.createElement('option'); option.value = entry.id; option.textContent = entry.name; return option; })); $('recovery-dialog').showModal();
  } catch (error) { status(`Recovery unavailable: ${error.message}`, true); }
});
$('close-recovery').addEventListener('click', () => $('recovery-dialog').close());
$('restore-backup').addEventListener('click', () => runWorkspace('Saved project recovered', () => {
  const id = $('recovery-choice').value; if (id === 'local') { const result = workspace.restore({ persistBackup: true }); if (!result.ok) throw Error(result.message); } else workspace.restoreBackup(id, { persistBackup: true }); $('recovery-dialog').close();
}));
$('template-choice').replaceChildren(...templateCatalog.map(entry => { const option = document.createElement('option'); option.value = entry.id; option.textContent = entry.name; return option; }));
$('insert-template').addEventListener('click', () => {
  if (!check() || drag || !requireNoDrafts()) return; publication.invalidate();
  try {
    const result = addTemplate(screen, $('template-choice').value); workspace.begin('Insert component'); workspace.commitScreen(result.screen, 'Insert component'); let group = result.group, n = 1; while (page().groups.some(g => g.id === group.id)) group = { ...group, id: `component${n++}` }; workspace.setGroups([...page().groups, group]); workspace.commit(); hydrate(result.ids); autoSave(); status('Original component inserted. Edit or ungroup its members.');
  } catch (error) { if (workspace.inTransaction) workspace.cancel(); hydrate(); status(`Component rejected: ${error.message}`, true); }
});
// Probar: the menu as the game runs it, from the pages as they would be exported (editor-hidden elements left out).
// The editor panels are inert meanwhile, so the run always matches the document.
const play = mountPlay({
  viewport: $('screen-frame'), stage: $('stage'), createNode, paintNode, report: status, server: Boolean(publishToken),
  getProject: () => ({ pages: workspace.project.pages.map(entry => ({ screen: exportVisibleScreen(entry) })) }), getPageId: () => page().screen.id,
  controls: { toggle: $('play-toggle'), bar: $('play-bar'), state: $('play-state'), permissions: $('play-permissions'), restart: $('play-restart'), exit: $('play-exit'),
    damage: $('play-damage'), world: $('play-world'), varsPanel: $('play-vars-panel'), vars: $('play-vars'), samples: $('play-samples'),
    addVariable: $('play-var-add'), newName: $('play-var-name'), newValue: $('play-var-value'), placeholders: $('play-placeholders'),
    logPanel: $('play-log-panel'), log: $('play-log'), clear: $('play-log-clear'), editTools: $('edit-tools'), hint: $('stage-hint') },
  onExit: () => { for (const panel of document.querySelectorAll('.structure, .inspector, .file-actions')) panel.inert = false; render(); }
});
$('play-toggle').addEventListener('click', () => {
  if (play.active()) { play.stop(); return; }
  if (drag || !check() || !requireNoDrafts()) return;
  publication.invalidate();
  for (const panel of document.querySelectorAll('.structure, .inspector, .file-actions')) panel.inert = true;
  try { play.start(); applySimulation(false); play.render(); } catch (error) { for (const panel of document.querySelectorAll('.structure, .inspector, .file-actions')) panel.inert = false; status(`No se pudo probar: ${error.message}`, true); }
});
window.addEventListener('beforeunload', event => { if (!valid || workspace.dirty || drag || hasPendingDrafts()) { event.preventDefault(); event.returnValue = ''; } });
window.addEventListener('resize', () => renderPreview());
$('sim-window').append(...WINDOW_PRESETS.map(entry => new Option(entry.label, entry.id)));
for (const id of ['sim-window', 'sim-gui', 'sim-size']) $(id).addEventListener('change', () => { applySimulation(); if (play.active()) play.render(); });
render(); $('autosave-state').textContent = es(recovered.message); $('autosave-state').dataset.error = String(!recovered.ok && !recovered.missing);
status(recovered.ok ? 'Recovered the latest locally saved project.' : 'Ready. Valid edits autosave locally; Download project creates a portable backup.');
