// Original editor-only controls for the existing schema 9 runtime. No plugin features are invented here.
import { cloneScreen, upgradeLogic, validateScreen, setScreenLogic, setElementLogic, EASINGS, KEYFRAME_TRIGGERS, durationMs } from './model.mjs';

export const BLUR_PRESETS = Object.freeze([
  { id: 'soft', label: 'Soft blur', style: { blur: 2, backdropBlur: 0, shadowBlur: 0, shadow: false } },
  { id: 'glass', label: 'Frosted glass', background: true, style: { blur: 0, backdropBlur: 6, shadowBlur: 0, shadow: false, opacity: .82 } },
  { id: 'shadow', label: 'Soft shadow', style: { blur: 0, backdropBlur: 0, shadowBlur: 4, shadow: true } },
  { id: 'clear', label: 'Clear blurs', style: { blur: 0, backdropBlur: 0, shadowBlur: 0, shadow: false } }
]);
export function applyBlurPreset(screen, ids, presetId) {
  const preset = BLUR_PRESETS.find(p => p.id === presetId), selected = new Set(ids);
  if (!preset) throw Error('Unknown blur preset.');
  if (!selected.size || [...selected].some(id => !screen.elements.some(e => e.id === id))) throw Error('Select existing elements.');
  const next = screen.version < 9 ? upgradeLogic(screen) : cloneScreen(screen);
  next.elements = next.elements.map(e => selected.has(e.id) ? { ...e, ...(preset.background === undefined ? {} : { background: preset.background }), style: { ...e.style, ...preset.style } } : e);
  validateScreen(next); return next;
}

const field = (scope, group, path, label, type = 'text', extra = {}) => ({ scope, group, path, label, type, ...extra });
const s = (group, path, label, type, extra) => field('screen', group, path, label, type, extra);
const e = (group, path, label, type, extra) => field('element', group, path, label, type, extra);
export const SETTINGS = Object.freeze([
  s('Access', 'command', 'Open command (without /)', 'text', { help: 'Optional command alias, e.g. shop. Reserved server commands are rejected.' }),
  s('Access', 'permission', 'Permission to open', 'text'),
  s('Access', 'open-on-join', 'Open after joining and accepting the pack', 'boolean', { default: false }),
  ...[['close-on-sneak', 'Close on sneak', true], ['close-on-damage', 'Close on damage', false], ['close-on-f', 'Close on F', false],
    ['close-on-shift-f', 'Close on Shift+F', false], ['reopen', 'Return to the previous screen on close', false]].map(([key, name, value]) => s('Closing rules', `behavior.${key}`, name, 'boolean', { default: value })),
  ...['open', 'close'].flatMap(phase => [
    s('Page transitions', `animation.${phase}.effect`, `${phase === 'open' ? 'Opening' : 'Closing'} effect`, 'select', { options: ['fade', 'slide', 'none'], default: phase === 'open' ? 'fade' : 'none' }),
    s('Page transitions', `animation.${phase}.duration-ms`, `${phase} duration (ms)`, 'number', { min: 0, max: 3000, default: 200 }),
    s('Page transitions', `animation.${phase}.easing`, `${phase} easing`, 'select', { options: EASINGS, default: 'ease-out' }),
    s('Page transitions', `animation.${phase}.from`, `${phase} slide side`, 'select', { options: ['left', 'right'], default: 'left' }),
    s('Page transitions', `animation.${phase}.distance`, `${phase} slide distance (logical px)`, 'number', { min: 1, max: 256, default: 48 })
  ]),
  s('Page transitions', 'animation.close-reversed', 'Legacy reverse close (fade, overrides closing effect)', 'boolean', { default: false,
    help: 'For a sliding exit, leave this OFF and set Closing effect to slide. Slides are horizontal only.' }),
  s('Variables and functions', 'variables', 'Initial variables', 'json', { example: { coins: '100', '*total': '0' }, help: 'Local names reset on open. *name or $name is shared across the player session. Use {coins} in text.' }),
  s('Variables and functions', 'functions', 'Reusable functions and parameters', 'json', { example: { greet: { params: { name: 'player' }, actions: [{ type: 'message', value: 'Hello {name}' }] } } }),
  s('Keyboard and lifecycle', 'keybinds', 'Key bindings', 'json', { example: [{ key: 'Q', actions: [{ type: 'close' }] }], help: 'W A S D SPACE Q F L, 1–9, SCROLL_UP/DOWN, optionally SHIFT_. Sneak closes by default; turn that rule off to use Shift bindings.' }),
  s('Keyboard and lifecycle', 'behavior.keyboard-navigation', 'Keyboard navigation', 'boolean', { default: false, help: 'W A S D move to the nearest button that way and Space clicks it. Keys bound above keep their own actions.' }),
  s('Keyboard and lifecycle', 'on-open', 'On open actions', 'actions'),
  s('Keyboard and lifecycle', 'on-close', 'On close actions', 'actions', { help: 'No delayed actions or redirects: the screen is already closing.' }),
  s('Triggers', 'timers', 'Timers', 'json', { example: [{ every: '5s', actions: [{ type: 'message', value: 'Five more seconds' }] }],
    help: 'Each runs its actions every period (0.25 s to an hour) while the page is open; the first run comes one period after opening.' }),
  s('Triggers', 'on-damage', 'On damage', 'actions', { help: '{damage} is the damage taken and {cause} why (fall, lava, entity_attack...). Runs before close-on-damage.' }),
  s('Triggers', 'on-world-change', 'On world change', 'actions', { help: '{world} is the new world and {from} the one the player left.' }),
  s('Triggers', 'on-change', 'When a variable changes', 'json', { example: { coins: [{ type: 'message', value: 'Coins: {old} -> {new}' }] },
    help: 'Variable name to actions, with {old} and {new}. A list never sets itself off again; chains stop after 8 changes.' }),
  e('Visibility and interaction', 'hidden', 'Start hidden', 'boolean', { default: false }),
  e('Visibility and interaction', 'visible-if', 'Visible when (condition)', 'text', { help: 'Example: {coins} >= 100. Rechecked in game; not evaluated by this design preview.' }),
  e('Visibility and interaction', 'draggable', 'Pick up and drop with two clicks', 'boolean', { default: false, buttonOnly: true }),
  e('Visibility and interaction', 'tooltip', 'Tooltip element', 'element', { buttonOnly: true, help: 'Another element shown while this button is hovered.' }),
  ...[['actions', 'Left click'], ['right-actions', 'Right click'], ['on-hover', 'Cursor enter'], ['on-leave', 'Cursor leave'], ['on-drop', 'Drop received']]
    .map(([path, label]) => e('Event actions', path, label, 'actions', { buttonOnly: true })),
  e('Click cooldown', 'cooldown', 'Wait between clicks', 'text', { buttonOnly: true, help: 'For example 2s or 500ms. Clicks before then do nothing (or run the actions below); the wait lasts while the player is online, across menus.' }),
  e('Click cooldown', 'on-cooldown', 'When clicked too soon', 'actions', { buttonOnly: true, help: '{cooldown} is the seconds left, for example: Wait {cooldown}s.' }),
  e('Keyframe animation', 'keyframes.trigger', 'Animation trigger', 'select', { options: KEYFRAME_TRIGGERS, default: 'open' }),
  e('Keyframe animation', 'keyframes.easing', 'Animation easing', 'select', { options: EASINGS, default: 'ease-in-out' }),
  e('Keyframe animation', 'keyframes.steps', 'Keyframe steps', 'json', { example: [{ at: 0, opacity: 0, x: -24 }, { at: 300, opacity: 1, x: 0 }],
    help: '2–16 steps; start at 0 ms, end by 10000 ms. x/y are logical pixels; scale .1–4, opacity 0–1, rotation -360–360. Cannot combine with frames or lists.' }),
  e('PNG / GIF animation', 'frames.folder', 'PNG folder or GIF name', 'text', { help: 'Server file: plugins/BigCasares/tideframe/engine/images/fire/ or fire.gif → enter fire. This field does not upload files. Load image above imports a static frame only.' }),
  e('PNG / GIF animation', 'frames.interval-ms', 'Frame interval (ms, blank = automatic GIF timing)', 'number', { min: 50, max: 60000 }),
  e('PNG / GIF animation', 'frames.loop', 'Loop frames', 'boolean', { default: true }),
  e('Data list', 'list.source', 'Data source variable / API list', 'text', { help: 'New lists start with one safe row; increase Visible rows below. Example: servers. Set a variable servers to Survival,Skyblock. Use {item}, {index}, {item.field} in row text.' }),
  e('Data list', 'list.rows', 'Visible rows', 'number', { min: 1, max: 16, default: 5 }),
  e('Data list', 'list.spacing', 'Row spacing (logical px)', 'number', { min: 1, max: 72, default: 16 }),
  e('Data list', 'list.filter', 'Filter variable', 'text', { help: 'Use an input action to fill this variable. Mouse wheel scrolls over the list. Rows must fit inside the canvas.' }),
  e('Data list', 'list.on-select', 'On row selected', 'actions', { buttonOnly: true })
]);

const recipe = (id, label, action) => ({ id, label, action: { type: id, ...action } });
export const ACTION_RECIPES = Object.freeze([
  recipe('message', 'Chat message', { value: '<green>Hello {player}' }),
  recipe('actionbar', 'Action bar message', { value: 'Ready!' }),
  recipe('command', 'Player command', { value: 'spawn' }),
  recipe('console', 'Console command (server allowlist required)', { value: 'give {player} diamond 1' }),
  recipe('sound', 'Sound', { value: 'UI_BUTTON_CLICK 1 1' }),
  recipe('teleport', 'Teleport', { value: 'world 0 80 0' }),
  recipe('delay', 'Wait / delay', { value: '500ms' }),
  recipe('give', 'Give an item', { value: 'DIAMOND 1' }),
  recipe('redirect', 'Open a screen', { value: 'menu' }),
  recipe('back', 'Previous screen', {}), recipe('close', 'Close screen', {}),
  ...['show', 'hide', 'toggle'].map(id => recipe(id, `${id[0].toUpperCase() + id.slice(1)} elements`, { elements: ['heading'] })),
  recipe('visibility', 'Element visibility', { elements: ['heading'], value: 'show' }),
  recipe('variable', 'Change a variable', { variable: 'coins', operation: 'add', value: '1' }),
  recipe('condition', 'Conditional branches', { value: '{coins} >= 100', then: [{ type: 'message', value: 'Enough coins' }], else: [{ type: 'message', value: 'Not enough coins' }] }),
  recipe('permission', 'Permission branches', { permission: 'tideframe.vip', then: [{ type: 'message', value: 'Access granted' }], else: [{ type: 'message', value: 'Access denied' }] }),
  recipe('function', 'Call a function', { function: 'greet', arguments: { name: '{player}' } }),
  recipe('input', 'Text input dialog', { variable: 'search', title: 'Search', label: 'Enter text', default: '', 'max-length': 64, filter: 'any', 'allow-empty': true, 'on-submit': [{ type: 'message', value: 'Search: {search}' }], 'on-cancel': [] }),
  recipe('offset', 'Horizontal offset', { element: 'heading', dx: '16' }),
  recipe('tint', 'Tint an element', { element: 'heading', value: '#7fefcf' }),
  recipe('animation', 'Play element animation', { element: 'heading' }),
  recipe('scroll', 'Scroll a list', { element: 'start', rows: 1, absolute: false }),
  recipe('charge', 'Charge money (Vault)', { amount: '100', then: [{ type: 'message', value: '<green>¡Compra hecha!' }], else: [{ type: 'message', value: '<red>No te alcanza la plata' }] }),
  recipe('pay', 'Pay money (Vault)', { amount: '50' }),
  recipe('server', 'Send to another server (proxy)', { value: 'lobby' }),
  recipe('confirm', 'Ask yes or no first', { title: '¿Seguro?', value: 'Esto cuesta 100 monedas.', yes: 'Sí', no: 'No', then: [{ type: 'message', value: '<green>¡Hecho!' }], else: [] }),
  recipe('pause', 'Pause a GIF or PNG animation', { element: 'heading' }),
  recipe('resume', 'Resume a GIF or PNG animation', { element: 'heading' }),
  recipe('frame', 'Jump to a frame of a GIF or PNG animation', { element: 'heading', value: '1' }),
  recipe('hud', 'Add a HUD page', { value: 'hud_vida' }),
  { id: 'hud-remove', label: 'Remove a HUD page', action: { type: 'hud', value: 'hud_vida', mode: 'remove' } },
  recipe('toast', 'Show a toast', { value: 'aviso', seconds: '4', values: { titulo: '¡Listo!', texto: 'Compra hecha' } })
]);
const copy = value => structuredClone(value);
const canonical = key => key.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase().replaceAll('_', '-');
// Existing imported camelCase keys are edited in place; do not create conflicting duplicate aliases.
function actualKey(object, wanted) { return Object.keys(object).find(key => canonical(key) === wanted) ?? wanted; }
function get(object, path) { return path.split('.').reduce((value, key) => value?.[actualKey(value ?? {}, key)], object); }
export function readSetting(screen, scope, path, elementId) {
  return get(scope === 'screen' ? screen.logic : screen.elements.find(e => e.id === elementId)?.logic, path);
}
function put(object, keys, value) {
  const key = actualKey(object, keys[0]);
  if (keys.length === 1) { if (value === undefined) delete object[key]; else Object.defineProperty(object, key, { value: copy(value), writable: true, configurable: true, enumerable: true }); }
  else { const child = object[key] && typeof object[key] === 'object' ? copy(object[key]) : {}; put(child, keys.slice(1), value); if (Object.keys(child).length) object[key] = child; else delete object[key]; }
}
function validateField(spec, value) {
  if (value === undefined) return;
  if (spec.type === 'number' && (!Number.isInteger(value) || value < spec.min || value > spec.max)) throw Error(`${spec.label}: use an integer from ${spec.min} to ${spec.max}.`);
  if (spec.type === 'boolean' && typeof value !== 'boolean') throw Error(`${spec.label}: use true or false.`);
  if (spec.type === 'select' && !spec.options.includes(value)) throw Error(`${spec.label}: select a supported option.`);
  if (['text', 'element'].includes(spec.type) && typeof value !== 'string') throw Error(`${spec.label}: text required.`);
  if (['variables', 'functions'].includes(spec.path) && (!value || typeof value !== 'object' || Array.isArray(value))) throw Error(`${spec.label}: use a JSON object.`);
  if (spec.path === 'keybinds' && (!value || typeof value !== 'object')) throw Error('Key bindings must be an object or an array.');
  if (spec.path === 'command' && (!/^[a-z0-9_-]{1,32}$/.test(value) || ['tideframe', 'tf', 'help', 'op', 'stop', 'reload', 'plugins', 'pl'].includes(value))) throw Error('Invalid or reserved command.');
  if (spec.path === 'permission' && !/^[A-Za-z0-9_.-]{1,128}$/.test(value)) throw Error('Invalid permission.');
  if (spec.path === 'frames.folder' && !/^[a-z0-9_-]{1,32}(\/[a-z0-9_-]{1,32}){0,3}$/.test(value)) throw Error('Use a relative frames folder name, e.g. fire or icons/fire.');
  if (spec.path === 'list.source' && !/^[a-z_][a-z0-9_]{0,23}$/.test(value)) throw Error('List source: a-z, 0-9 and _, up to 24 characters.');
  const variableName = name => typeof name === 'string' && /^[*$]?[a-z_][a-z0-9_]{0,23}$/.test(name);
  if (spec.path === 'list.filter' && !variableName(value)) throw Error('Invalid filter variable name.');
  if (spec.path === 'visible-if' && (value.length > 256 || !value.trim())) throw Error('Visibility condition must be 1 to 256 characters.');
  if (spec.path === 'cooldown' && (durationMs(value) === null || durationMs(value) > 3600000)) throw Error('Wait between clicks: use a duration like 2s or 500ms, up to one hour.');
  if (spec.path === 'timers' && (!Array.isArray(value) || value.some(timer => durationMs(timer?.every) === null || durationMs(timer.every) < 250 || !Array.isArray(timer.actions) || !timer.actions.length)))
    throw Error('Timers: a list of {every, actions}, every 0.25 to 3600 seconds, with at least one action each.');
  if (spec.path === 'on-change' && (!value || typeof value !== 'object' || Array.isArray(value) || Object.entries(value).some(([name, list]) => !variableName(name) || !Array.isArray(list))))
    throw Error('When a variable changes: an object of variable names and action lists.');
  if (spec.path === 'variables') {
    if (Object.keys(value).length > 256) throw Error('At most 256 variables.');
    for (const [name, text] of Object.entries(value)) if (!variableName(name) || text === null || typeof text === 'object' || String(text).length > 256) throw Error('Variables need valid names and values up to 256 characters.');
  }
  if (spec.path === 'functions') {
    if (Object.keys(value).length > 64) throw Error('At most 64 functions.');
    for (const [name, body] of Object.entries(value)) {
      const actions = body?.actions;
      if (!body || Array.isArray(body) || !/^[a-z][a-z0-9_-]{0,31}$/.test(name) || !Array.isArray(actions) || !actions.length) throw Error('Each named function needs an object with at least one action.');
      const params = body.params ?? {};
      if (!params || typeof params !== 'object' || Object.keys(params).length > 32 || !(Array.isArray(params) ? params : Object.keys(params)).every(variableName)) throw Error('Invalid function parameters (up to 32 variable names).');
      if (!Array.isArray(params) && Object.values(params).some(v => v === null || !['string', 'number', 'boolean'].includes(typeof v) || typeof v === 'number' && !Number.isFinite(v))) throw Error('Function parameter defaults must be scalar text, numbers or booleans.');
    }
  }
  if (spec.path === 'keybinds') {
    const keys = ['W','A','S','D','SPACE','Q','F','L','1','2','3','4','5','6','7','8','9','SCROLL_UP','SCROLL_DOWN'];
    const bindings = Array.isArray(value) ? value : Object.entries(value).map(([key, actions]) => ({ key, actions }));
    for (const bind of bindings) if (!bind || typeof bind.key !== 'string' || !keys.includes(bind.key.replace(/^SHIFT_/, ''))) throw Error('Unsupported key. Use W/A/S/D/SPACE/Q/F/L, 1–9 or SCROLL_UP/DOWN, optionally SHIFT_.');
  }
}
export function writeSetting(screen, scope, path, value, elementId) {
  const spec = SETTINGS.find(s => s.scope === scope && s.path === path);
  if (!spec) throw Error('Unknown advanced setting.');
  validateField(spec, value);
  const element = scope === 'element' ? screen.elements.find(e => e.id === elementId) : null;
  if (scope === 'element' && !element) throw Error('Select an element.');
  const logic = copy((scope === 'screen' ? screen.logic : element.logic) ?? {});
  const creatingList = path === 'list.source' && value !== undefined && !get(logic, 'list');
  put(logic, path.split('.'), value);
  if (creatingList) put(logic, ['list', 'rows'], 1);
  // A newly enabled compound feature needs its required identity/steps before publication.
  if (get(logic, 'keyframes') && !get(logic, 'keyframes.steps')) put(logic, ['keyframes', 'steps'], [{ at: 0 }, { at: 300, opacity: 1 }]);
  if (get(logic, 'frames') && !get(logic, 'frames.folder')) throw Error('Set the PNG folder or GIF name first.');
  if (get(logic, 'list') && !get(logic, 'list.source')) throw Error('Set the list data source first.');
  if (get(logic, 'keyframes') && get(logic, 'list')) throw Error('A list cannot use keyframes. Remove one animation feature first.');
  return scope === 'screen' ? setScreenLogic(screen, Object.keys(logic).length ? logic : null) : setElementLogic(screen, elementId, Object.keys(logic).length ? logic : null);
}
export function removeFeature(screen, scope, feature, elementId) {
  if (!SETTINGS.some(s => s.scope === scope && s.path.split('.')[0] === feature)) throw Error('Unknown feature.');
  const next = cloneScreen(screen), target = scope === 'screen' ? next : next.elements.find(e => e.id === elementId);
  if (!target) throw Error('Select an element.');
  if (target.logic) delete target.logic[actualKey(target.logic, feature)];
  return scope === 'screen' ? setScreenLogic(next, target.logic && Object.keys(target.logic).length ? target.logic : null) : setElementLogic(next, elementId, target.logic && Object.keys(target.logic).length ? target.logic : null);
}
export function appendAction(raw, recipeId) {
  const recipe = ACTION_RECIPES.find(r => r.id === recipeId); if (!recipe) throw Error('Unknown action template.');
  const parsed = typeof raw === 'string' ? (raw.trim() ? JSON.parse(raw) : []) : raw;
  const actions = Array.isArray(parsed) ? parsed : parsed?.actions;
  if (!Array.isArray(actions)) throw Error('Use an action list.');
  return [...copy(actions), copy(recipe.action)];
}
