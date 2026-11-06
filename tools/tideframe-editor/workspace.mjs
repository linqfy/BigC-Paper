// Original Tideframe workspace, history and durable local storage helpers.
// Screen JSON stays in its supported plugin schema; selection visibility, locking and groups are editor metadata.
import { createScreen, promoteScreen, cloneScreen, validateScreenBudget, serializeScreen, MAX_BYTES } from './model.mjs';
import { validateScreenImages } from './images.mjs';

export const MAX_PAGES = 16;
export const MAX_HISTORY = 40;
export const DEFAULT_PREFERENCES = Object.freeze({ gridVisible: true, snapEnabled: false, snapStep: .02, zoom: 1,
  smartGuides: true, snapObjects: false, showHotbar: false, showHand: false,
  showTools: true, showPages: true, showFooter: true, primaryColor: '#96e4da', secondaryColor: '#102939' });
const PROJECT_KEYS = ['format', 'version', 'pages', 'activePageId', 'preferences'];
const PAGE_KEYS = ['screen', 'hiddenIds', 'lockedIds', 'groups'];
const ID = /^[a-z][a-z0-9_-]{0,31}$/;
const preferenceKeys = Object.keys(DEFAULT_PREFERENCES);
const encoder = new TextEncoder();
const trustedPages = new WeakSet(), trustedScreens = new WeakSet();

function keys(value, expected, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== expected.length || !expected.every(key => Object.hasOwn(value, key))) throw Error(`${label} keys must be exactly: ${expected.join(', ')}.`);
}
function assertId(id, label) { if (typeof id !== 'string' || !ID.test(id)) throw Error(`${label} requires a lowercase ID of 1-32 characters.`); }
function clonePage(page) { return { screen: cloneScreen(page.screen), hiddenIds: [...page.hiddenIds], lockedIds: [...page.lockedIds], groups: page.groups.map(group => ({ id: group.id, members: [...group.members] })) }; }
function cloneProject(project) { return { format: 'tideframe-project', version: 1, pages: project.pages.map(page => trustedPages.has(page) ? page : clonePage(page)), activePageId: project.activePageId, preferences: { ...project.preferences } }; }
function frozen(value) { if (value && typeof value === 'object' && !Object.isFrozen(value)) { for (const child of Object.values(value)) frozen(child); Object.freeze(value); } return value; }
// Only our recursively frozen snapshots are trusted; externally shallow-frozen objects never bypass validation.
function snapshot(project) {
  const result = frozen(cloneProject(project));
  for (const page of result.pages) { trustedPages.add(page); trustedScreens.add(page.screen); }
  return result;
}
function equal(a, b) {
  if (a === b) return true;
  if (!a || !b || typeof a !== 'object' || typeof b !== 'object' || Array.isArray(a) !== Array.isArray(b)) return false;
  const names = Object.keys(a), other = Object.keys(b);
  return names.length === other.length && names.every(name => Object.hasOwn(b, name) && equal(a[name], b[name]));
}
// Counting immutable Base64 separately avoids constructing a giant JSON string during edits.
function encodedLength(value) {
  let photoBytes = 0;
  const small = JSON.stringify(value, (key, item) => { if (key === 'image' && typeof item === 'string') { photoBytes += item.length; return ''; } return item; }, 2) + '\n';
  return encoder.encode(small).length + photoBytes;
}
function assertBudget(value) { if (encodedLength(value) > MAX_BYTES) throw Error('Project exceeds the 128 MiB encoded limit.'); }
function validatePreferences(value) {
  keys(value, preferenceKeys, 'Preferences');
  for (const key of preferenceKeys) {
    if (key === 'zoom') { if (typeof value[key] !== 'number' || !Number.isFinite(value[key]) || value[key] < .25 || value[key] > 4) throw Error('Preference zoom must be from 0.25 to 4.'); }
    else if (key === 'snapStep') { if (typeof value[key] !== 'number' || !Number.isFinite(value[key]) || value[key] < .01 || value[key] > 1) throw Error('Preference snapStep must be from 0.01 to 1.'); }
    else if (key.endsWith('Color')) { if (typeof value[key] !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(value[key])) throw Error(`Preference ${key} requires color #RRGGBB.`); }
    else if (typeof value[key] !== 'boolean') throw Error(`Preference ${key} must be boolean.`);
  }
}
function validateIds(ids, members, label) {
  if (!Array.isArray(ids) || ids.length > 128) throw Error(`${label} must be an array of at most 128 element IDs.`);
  const seen = new Set();
  for (const id of ids) { assertId(id, label); if (seen.has(id)) throw Error(`${label} contains a duplicate ID: ${id}.`); if (!members.has(id)) throw Error(`${label} refers to unknown element: ${id}.`); seen.add(id); }
}
export function validateProject(project) {
  keys(project, PROJECT_KEYS, 'Project');
  if (project.format !== 'tideframe-project' || project.version !== 1) throw Error('Project format/version must be tideframe-project version 1.');
  if (!Array.isArray(project.pages) || project.pages.length < 1 || project.pages.length > MAX_PAGES) throw Error('Project requires 1-16 pages.');
  validatePreferences(project.preferences);
  const pageIds = new Set();
  for (const page of project.pages) {
    keys(page, PAGE_KEYS, 'Page'); if (!trustedScreens.has(page.screen)) validateScreenBudget(page.screen);
    if (![4, 5, 6, 7, 8, 9].includes(page.screen.version)) throw Error('Project pages must use screen schema 4 to 9. Import legacy screens separately.');
    if (pageIds.has(page.screen.id)) throw Error(`Duplicate page ID: ${page.screen.id}.`); pageIds.add(page.screen.id);
    const members = new Set(page.screen.elements.map(element => element.id));
    validateIds(page.hiddenIds, members, 'Hidden IDs'); validateIds(page.lockedIds, members, 'Locked IDs');
    if (!Array.isArray(page.groups) || page.groups.length > 128) throw Error('Groups must be an array of at most 128 groups.');
    const groupIds = new Set(), grouped = new Set();
    for (const group of page.groups) {
      keys(group, ['id', 'members'], 'Group'); assertId(group.id, 'Group ID');
      if (groupIds.has(group.id)) throw Error(`Duplicate group ID: ${group.id}.`); groupIds.add(group.id);
      validateIds(group.members, members, 'Group members'); if (!group.members.length) throw Error('A group requires at least one member.');
      for (const member of group.members) { if (grouped.has(member)) throw Error(`Group member belongs to multiple groups: ${member}.`); grouped.add(member); }
    }
  }
  if (!pageIds.has(project.activePageId)) throw Error('Active page ID must refer to a page in the project.');
  assertBudget(project); return project;
}
export function createProject(screen = createScreen()) {
  const ready = promoteScreen(screen);
  return validateProject({ format: 'tideframe-project', version: 1, pages: [{ screen: ready, hiddenIds: [], lockedIds: [], groups: [] }], activePageId: ready.id, preferences: { ...DEFAULT_PREFERENCES } });
}
export function projectByteLength(project) { validateProject(project); return encodedLength(project); }
export function serializeProject(project) { validateProject(project); return JSON.stringify(project, null, 2) + '\n'; }
function exactInteger(raw) {
  const match = raw.match(/^-?(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/); if (!match) return false;
  let digits = (match[1] + (match[2] ?? '')).replace(/^0+/, ''), exponent = Number(match[3] ?? 0) - (match[2]?.length ?? 0);
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); exponent++; }
  return !digits || exponent >= 0;
}
// Strict reader rejects duplicates before JSON.parse could erase them. Raw integer
// tokens are checked before Number rounding, including those inside nested screens.
function readJson(raw) {
  if (typeof raw !== 'string') throw Error('Project input must be JSON text.');
  if (raw.length > MAX_BYTES || encoder.encode(raw).length > MAX_BYTES) throw Error('Project exceeds the 128 MiB encoded limit.');
  let cursor = 0;
  const fail = message => { throw Error(`${message} At character ${cursor + 1}.`); };
  const spaces = () => { while (cursor < raw.length && /[\x20\t\r\n]/.test(raw[cursor])) cursor++; };
  function string() {
    const start = cursor++;
    while (cursor < raw.length) { const char = raw[cursor++]; if (char === '\\') cursor++; else if (char === '"') { try { return JSON.parse(raw.slice(start, cursor)); } catch { fail('Invalid JSON string.'); } } }
    fail('Unterminated JSON string.');
  }
  function value(depth, path) {
    if (depth > 28) fail('Project JSON nesting exceeds 28 levels.'); spaces();
    if (raw[cursor] === '"') return string();
    if (raw[cursor] === '{') {
      cursor++; spaces(); const pairs = [], seen = new Set(); if (raw[cursor] === '}') { cursor++; return {}; }
      while (cursor < raw.length) {
        if (raw[cursor] !== '"') fail('Expected a JSON key.'); const name = string();
        if (seen.has(name)) fail(`Duplicate JSON key: ${name}.`); seen.add(name); spaces(); if (raw[cursor++] !== ':') fail('Expected a colon.');
        pairs.push([name, value(depth + 1, `${path}/${name}`)]); spaces(); const next = raw[cursor++]; if (next === '}') return Object.fromEntries(pairs); if (next !== ',') fail('Expected comma or closing brace.'); spaces();
      }
      fail('Unterminated JSON object.');
    }
    if (raw[cursor] === '[') {
      cursor++; spaces(); const items = []; if (raw[cursor] === ']') { cursor++; return items; }
      while (cursor < raw.length) { items.push(value(depth + 1, `${path}/${items.length}`)); spaces(); const next = raw[cursor++]; if (next === ']') return items; if (next !== ',') fail('Expected comma or closing bracket.'); }
      fail('Unterminated JSON array.');
    }
    for (const [token, result] of [['true', true], ['false', false], ['null', null]]) if (raw.startsWith(token, cursor)) { cursor += token.length; return result; }
    const match = raw.slice(cursor).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
    if (match) { cursor += match[0].length; if ((path.endsWith('/version') || /\/screen\/quality\/(?:width|height)$/.test(path) || /\/screen\/presentation\/scale$/.test(path) || /\/screen\/elements\/\d+\/layer$/.test(path) || /\/screen\/elements\/\d+\/style\/(?:outlineWidth|wrapWidth)$/.test(path)) && !exactInteger(match[0])) fail('Project version, screen version, quality, layers and appearance widths require exact integers.'); return Number(match[0]); }
    fail('Expected a JSON value.');
  }
  const result = value(1, ''); spaces(); if (cursor !== raw.length) fail('Unexpected trailing JSON content.'); return result;
}
export function parseProject(raw) { return validateProject(readJson(raw)); }
export async function parseProjectForPublication(raw, decode) {
  const project = parseProject(raw);
  await validateScreenImages({ elements: project.pages.flatMap(page => page.screen.elements) }, decode);
  return project;
}
export function exportVisibleScreen(source) {
  const page = source?.format === 'tideframe-project' ? source.pages.find(page => page.screen.id === source.activePageId) : source;
  if (!page || !page.screen || !Array.isArray(page.hiddenIds)) throw Error('Export requires a project page.');
  keys(page, PAGE_KEYS, 'Page');
  validateIds(page.hiddenIds, new Set(page.screen.elements.map(element => element.id)), 'Hidden IDs');
  const hidden = new Set(page.hiddenIds), screen = cloneScreen(page.screen); screen.elements = screen.elements.filter(element => !hidden.has(element.id));
  validateScreenBudget(screen); return screen;
}
function uniqueId(base, used) {
  if (!used.has(base)) return base;
  for (let number = 1; ; number++) { const suffix = `_copy${number}`, candidate = base.slice(0, 32 - suffix.length) + suffix; if (!used.has(candidate)) return candidate; }
}
function reconcile(page, screen, renames = {}) {
  const available = new Set(screen.elements.map(element => element.id));
  const retained = ids => [...new Set(ids.map(id => Object.hasOwn(renames, id) ? renames[id] : id).filter(id => available.has(id)))];
  return { screen: cloneScreen(screen), hiddenIds: retained(page.hiddenIds), lockedIds: retained(page.lockedIds), groups: page.groups.map(group => ({ id: group.id, members: retained(group.members) })).filter(group => group.members.length) };
}
// Page IDs are the destinations of the plugin's open:id actions. Retarget exact
// references together with renaming, including self references, in one snapshot.
function retargetPageLinks(pages, oldId, nextId) {
  if (oldId === nextId) return pages;
  const oldAction = `open:${oldId}`, nextAction = `open:${nextId}`;
  return pages.map(page => page.screen.elements.some(element => element.action === oldAction)
    ? { ...page, screen: { ...page.screen, elements: page.screen.elements.map(element => element.action === oldAction ? { ...element, action: nextAction } : element) } }
    : page);
}
const BACKUP_FORMAT = 'tideframe-workspace-backups';
function validateBackups(value) {
  keys(value, ['format', 'version', 'entries'], 'Backups');
  if (value.format !== BACKUP_FORMAT || value.version !== 1 || !Array.isArray(value.entries) || value.entries.length > MAX_HISTORY) throw Error('Backup archive must contain at most 40 entries.');
  const ids = new Set();
  for (const entry of value.entries) {
    keys(entry, ['id', 'createdAt', 'label', 'project'], 'Backup entry');
    if (typeof entry.id !== 'string' || !/^[0-9]+-[0-9]+$/.test(entry.id) || ids.has(entry.id)) throw Error('Backup ID is invalid or duplicate.'); ids.add(entry.id);
    if (!Number.isSafeInteger(entry.createdAt) || entry.createdAt < 0 || typeof entry.label !== 'string' || entry.label.length > 80 || !/^[\x20-\x7e]*$/.test(entry.label)) throw Error('Backup metadata is invalid.');
    validateProject(entry.project);
  }
  assertBudget(value); return value;
}

/** Inject a synchronous Web Storage compatible adapter. setItem must be atomic
 * like localStorage: quota/security failures leave its previous value unchanged.
 * Save failures are reported explicitly; callers must not label them as saved. */
export function createWorkspace(initial = createScreen(), { storage = null, key = 'tideframe.project.v1', historyLimit = MAX_HISTORY } = {}) {
  if (!Number.isInteger(historyLimit) || historyLimit < 1 || historyLimit > MAX_HISTORY) throw Error('History limit must be from 1 to 40.');
  if (typeof key !== 'string' || !key || key.length > 160) throw Error('Storage key must be nonempty and at most 160 characters.');
  let current = snapshot(initial?.format === 'tideframe-project' ? validateProject(initial) : createProject(initial));
  let saved = current, past = [], future = [], transaction = null, backupNumber = 0;
  const backupKey = `${key}.backups`;
  function activePage() { return current.pages.find(page => page.screen.id === current.activePageId); }
  function pageById(id) { const page = current.pages.find(page => page.screen.id === id); if (!page) throw Error(`Unknown page: ${id}.`); return page; }
  function record(before, label) { past.push({ project: before, label }); if (past.length > historyLimit) past.shift(); future = []; }
  function apply(project, label = 'Edit', history = true) {
    validateProject(project); if (equal(project, current)) return false;
    const before = current; current = snapshot(project); if (history && !transaction) record(before, label); return true;
  }
  function archive() { if (!storage?.getItem) throw Error('Local storage unavailable. Export the project before closing.'); const raw = storage.getItem(backupKey); return raw === null ? { format: BACKUP_FORMAT, version: 1, entries: [] } : validateBackups(readJson(raw)); }
  function backup(label = 'Workspace backup') {
    try {
      if (!storage?.setItem) throw Error('Local storage unavailable. Export the project before closing.');
      const previous = archive(); if (previous.entries.length >= MAX_HISTORY) throw Error('Backup archive reached 40 entries. Export its projects before removing old backups.');
      const time = Date.now(), used = new Set(previous.entries.map(entry => entry.id));
      let id; do { id = `${time}-${++backupNumber}`; } while (used.has(id));
      const entry = { id, createdAt: time, label, project: cloneProject(current) };
      // Never evict previous backups: quota/budget failure keeps every saved snapshot.
      const next = validateBackups({ ...previous, entries: [...previous.entries, entry] }); storage.setItem(backupKey, JSON.stringify(next, null, 2) + '\n');
      return { ok: true, backupId: entry.id, message: 'Project backup saved locally.' };
    } catch (error) { return { ok: false, message: `Backup failed: ${error.message}` }; }
  }
  function preserve(options, label) {
    if (options?.discard === true) return;
    if (options?.persistBackup !== true) throw Error('Closing or replacing pages requires a durable backup or explicit discard.');
    const result = backup(label); if (!result.ok) throw Error(result.message);
  }
  const controller = {
    get project() { return current; }, get dirty() { return !equal(current, saved); }, get undoCount() { return past.length; }, get redoCount() { return future.length; },
    get canUndo() { return past.length > 0 || Boolean(transaction && !equal(transaction.project, current)); }, get canRedo() { return future.length > 0; },
    get storageKey() { return key; }, get backupKey() { return backupKey; }, get inTransaction() { return transaction !== null; },
    activePage,
    begin(label = 'Gesture') { if (transaction) throw Error('A workspace transaction is already active.'); transaction = { project: current, label }; return current; },
    commit() { if (!transaction) return false; const before = transaction; transaction = null; if (equal(before.project, current)) return false; record(before.project, before.label); return true; },
    cancel() { if (!transaction) return false; current = transaction.project; transaction = null; return true; },
    commitScreen(screen, label = 'Edit screen', { idRenames = {} } = {}) {
      if (!trustedScreens.has(screen)) validateScreenBudget(screen); if (![4, 5, 6, 7, 8, 9].includes(screen.version)) throw Error('Workspace screens require schema 4 to 9.');
      const page = activePage(); if (current.pages.some(other => other !== page && other.screen.id === screen.id)) throw Error(`Duplicate page ID: ${screen.id}.`);
      const pages = current.pages.map(other => other === page ? reconcile(page, screen, idRenames) : other);
      return apply({ ...current, activePageId: screen.id, pages: retargetPageLinks(pages, page.screen.id, screen.id) }, label);
    },
    setActivePage(id) { pageById(id); if (transaction) throw Error('Finish the gesture before switching pages.'); return apply({ ...current, activePageId: id }, 'Switch page', false); },
    addPage(screen = createScreen()) {
      if (current.pages.length >= MAX_PAGES) throw Error('Project reached the 16 page limit.'); const ready = promoteScreen(screen);
      ready.id = uniqueId(ready.id, new Set(current.pages.map(page => page.screen.id)));
      const page = { screen: ready, hiddenIds: [], lockedIds: [], groups: [] }; apply({ ...current, pages: [...current.pages, page], activePageId: ready.id }, 'Add page'); return ready.id;
    },
    duplicatePage(id = current.activePageId) {
      if (current.pages.length >= MAX_PAGES) throw Error('Project reached the 16 page limit.'); const page = clonePage(pageById(id)); page.screen.id = uniqueId(page.screen.id, new Set(current.pages.map(other => other.screen.id)));
      apply({ ...current, pages: [...current.pages, page], activePageId: page.screen.id }, 'Duplicate page'); return page.screen.id;
    },
    renamePage(id, nextId, title) {
      assertId(nextId, 'Page ID'); const page = pageById(id);
      if (current.pages.some(other => other !== page && other.screen.id === nextId)) throw Error(`Duplicate page ID: ${nextId}.`);
      const screen = { ...page.screen, id: nextId, ...(title === undefined ? {} : { title }) };
      const pages = current.pages.map(other => other === page ? { ...page, screen } : other);
      return apply({ ...current, pages: retargetPageLinks(pages, id, nextId), activePageId: current.activePageId === id ? nextId : current.activePageId }, 'Rename page');
    },
    closePage(id = current.activePageId, options = {}) {
      const page = pageById(id); preserve(options, `Close ${id}`);
      const pages = current.pages.filter(other => other !== page);
      if (!pages.length) return apply(createProject(), 'Close last page');
      return apply({ ...current, pages, activePageId: current.activePageId === id ? pages[Math.min(current.pages.indexOf(page), pages.length - 1)].screen.id : current.activePageId }, 'Close page');
    },
    closeAll(options = {}) { preserve(options, 'Close all pages'); return apply(createProject(), 'Close all pages'); },
    setHidden(ids, hidden = true) { const page = activePage(); validateIds(ids, new Set(page.screen.elements.map(e => e.id)), 'Hidden IDs'); if (typeof hidden !== 'boolean') throw Error('Hidden state must be boolean.'); const wanted = new Set(ids), hiddenIds = hidden ? [...new Set([...page.hiddenIds, ...ids])] : page.hiddenIds.filter(id => !wanted.has(id)); return apply({ ...current, pages: current.pages.map(other => other === page ? { ...page, hiddenIds } : other) }, hidden ? 'Hide selection' : 'Unhide selection'); },
    setLocked(ids, locked = true) { const page = activePage(); validateIds(ids, new Set(page.screen.elements.map(e => e.id)), 'Locked IDs'); if (typeof locked !== 'boolean') throw Error('Locked state must be boolean.'); const wanted = new Set(ids), lockedIds = locked ? [...new Set([...page.lockedIds, ...ids])] : page.lockedIds.filter(id => !wanted.has(id)); return apply({ ...current, pages: current.pages.map(other => other === page ? { ...page, lockedIds } : other) }, locked ? 'Block selection' : 'Unblock selection'); },
    setGroups(groups) { const page = activePage(); return apply({ ...current, pages: current.pages.map(other => other === page ? { ...page, groups } : other) }, 'Edit groups'); },
    setPreferences(patch) { if (!patch || typeof patch !== 'object' || Array.isArray(patch) || Object.keys(patch).some(key => !preferenceKeys.includes(key))) throw Error('Preferences patch contains unknown keys.'); return apply({ ...current, preferences: { ...current.preferences, ...patch } }, 'Editor preferences'); },
    undo() { if (transaction) return controller.cancel(); if (!past.length) return false; const entry = past.pop(); future.push({ project: current, label: entry.label }); current = entry.project; return true; },
    redo() { if (transaction) throw Error('Finish the gesture before redo.'); if (!future.length) return false; const entry = future.pop(); past.push({ project: current, label: entry.label }); current = entry.project; return true; },
    save() {
      try { if (transaction) throw Error('Finish the gesture before saving.'); if (!storage?.setItem) throw Error('Local storage unavailable. Export the project instead.'); const raw = serializeProject(current); storage.setItem(key, raw); saved = current; return { ok: true, message: 'Project saved locally.', project: current }; }
      catch (error) { return { ok: false, message: `Save failed: ${error.message}. Export the project as a file.` }; }
    },
    restore({ persistBackup = false } = {}) {
      try {
        if (!storage?.getItem) throw Error('Local storage unavailable.'); const raw = storage.getItem(key); if (raw === null) return { ok: false, missing: true, message: 'No saved local project.' };
        const loaded = parseProject(raw); if (persistBackup) preserve({ persistBackup: true }, 'Before restore'); current = snapshot(loaded); saved = current; past = []; future = []; transaction = null;
        return { ok: true, message: 'Saved local project restored.', project: current };
      } catch (error) { return { ok: false, message: `Restore failed: ${error.message}` }; }
    },
    loadProject(project, options = {}) { validateProject(project); preserve(options, 'Before opening project'); return apply(project, 'Open project'); },
    backup,
    listBackups() { return frozen(archive().entries.map(entry => ({ ...entry, project: cloneProject(entry.project) }))); },
    restoreBackup(id, options = { persistBackup: true }) { const entry = archive().entries.find(entry => entry.id === id); if (!entry) throw Error(`Unknown backup: ${id}.`); preserve(options, 'Before backup restore'); return apply(entry.project, 'Restore project backup'); },
    exportProject() { return serializeProject(current); },
    exportVisibleScreen() { return exportVisibleScreen(activePage()); },
    exportVisibleJson() { return serializeScreen(exportVisibleScreen(activePage())); }
  };
  return controller;
}
