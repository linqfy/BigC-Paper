// Original Tideframe schema and editing operations. No browser or runtime dependencies.
import { validatePngBase64, validateScreenImages } from './images.mjs';
import { hasTags, colors as wordColors, escape as escapeTags, MAX_COLORS } from './richtext.mjs';
const ROOT_KEYS = ['version', 'id', 'title', 'elements'];
const V4_ROOT_KEYS = ['version', 'id', 'title', 'quality', 'elements'];
const V6_ROOT_KEYS = [...V4_ROOT_KEYS, 'presentation'];
const ELEMENT_KEYS = ['id', 'text', 'x', 'y', 'width', 'height', 'kind', 'action'];
const V2_ELEMENT_KEYS = [...ELEMENT_KEYS, 'shape', 'border', 'messageCondition'];
const V3_ELEMENT_KEYS = [...V2_ELEMENT_KEYS, 'background', 'image'];
const V4_ELEMENT_KEYS = [...V3_ELEMENT_KEYS, 'style', 'layer', 'actionArea'];
export const DEFAULT_STYLE = Object.freeze({ opacity: 1, fillColor: '#28384b', borderColor: '#ffffff', textColor: '#ffffff', shadow: false });
export const DEFAULT_APPEARANCE_STYLE = Object.freeze({ ...DEFAULT_STYLE, rotation: 0, radius: .08, outlineWidth: 1,
  hoverColor: null, hoverEffect: 'none', textAlign: 'center', wrapWidth: 0 });
export const DEFAULT_PRESENTATION_STYLE = Object.freeze({ ...DEFAULT_APPEARANCE_STYLE, brightness: 1 });
// Schema 7: big text (1-4x), text outline colour and text drop shadow.
export const DEFAULT_TEXT_STYLE = Object.freeze({ ...DEFAULT_PRESENTATION_STYLE, textScale: 1, textOutline: null, textShadow: false });
// Schema 8: gradient fills (vertical, horizontal, diagonal, radial) and vertical text gradients.
export const DEFAULT_GRADIENT_STYLE = Object.freeze({ ...DEFAULT_TEXT_STYLE, gradientColor: null, gradientDirection: 'vertical', textGradient: null });
export const GRADIENT_DIRECTIONS = Object.freeze(['vertical', 'horizontal', 'diagonal', 'radial']);
export const HOVER_EFFECTS = Object.freeze(['none', 'translate', 'scale', 'glow']);
export const BAR_SOURCES = Object.freeze(['health', 'food', 'xp', 'air', 'armor', 'boss', 'hp', 'timer']);
// LiveText.BAR: a built-in source, a variable or a %placeholder%, an optional maximum (/10, /max_coins) and notches (:N).
const BAR_VALUE = '%[A-Za-z0-9_:.-]{1,64}%|[a-z_][a-z0-9_]{0,23}';
const BAR = new RegExp(`^\\{bar:(${BAR_VALUE})(?:/([0-9]{1,9}(?:\\.[0-9]{1,4})?|${BAR_VALUE}))?(?::(\\d{1,2}))?\\}$`);
export function barSource(element) { return element.kind !== 'decoration' && BAR.test(element.text) ? element.text.match(BAR)[1] : null; }
export const DEFAULT_PRESENTATION = Object.freeze({ mode: 'menu', scale: 1, cursor: 'cross' });
// Schema 9: accented letters and symbols the game font draws besides printable ASCII (same order as LiveText.EXTRA),
// and an optional style.textCase: 'upper' (default, capitals like every earlier schema) or 'original' (as written).
export const LATIN_UPPER = 'ÁÉÍÓÚÜÑÀÈÌÒÙÂÊÎÔÛÄËÏÖÃÕÇ', LATIN_LOWER = 'áéíóúüñàèìòùâêîôûäëïöãõç', TEXT_SYMBOLS = '¡¿°ºª«»·€×÷±²³•…←↑→↓★❤✔✖';
export const TEXT_EXTRA = LATIN_UPPER + LATIN_LOWER + TEXT_SYMBOLS;
export const TEXT_CASES = Object.freeze(['upper', 'original']);
// Schema 9 blurs, in logical pixels of standard deviation (0 = off, at most MAX_BLUR): the whole element, what is
// underneath it (frosted glass) and the edge of its drop shadow. Same meaning as Screen.Style in the plugin.
export const BLUR_FIELDS = Object.freeze(['blur', 'backdropBlur', 'shadowBlur']);
/** 9-slice (schema 9): fixed image border in image pixels, and image pixels per logical pixel. */
export const SLICE_FIELDS = Object.freeze(['slice', 'sliceScale']);
export const MAX_BLUR = 16;
const EXTENDED_TEXT = new RegExp(`^[\\x20-\\x7e\\n${TEXT_EXTRA}]*$`, 'u');
const LOOK_ALIKES = { '\u2018': "'", '\u2019': "'", '\u201a': "'", '\u2032': "'", '\u00b4': "'", '\u201c': '"', '\u201d': '"', '\u201e': '"', '\u2033': '"',
  '\u2013': '-', '\u2014': '-', '\u2212': '-', '\u00a0': ' ', '\u2665': '❤', '\u2661': '❤', '\u2763': '❤', '\u2713': '✔', '\u2611': '✔', '\u2715': '✖', '\u2717': '✖',
  '\u2718': '✖', '\u274c': '✖', '\u2b50': '★', '\u2606': '★', '\u2729': '★', '\u272a': '★', '\u2730': '★', '\u2219': '•', '\u25cf': '•' };
/** True when text needs schema 9: a character beyond printable ASCII and line feeds. */
export function extendedText(text) { return /[^\x20-\x7e\n]/.test(text) || hasTags(text); }
/** True when the game font draws every character (schema 9 rules). */
export function drawableText(text) { return typeof text === 'string' && EXTENDED_TEXT.test(text); }
/** Same as LiveText.normalize: curly quotes, dashes, ♥ ✓ ⭐... become the font's own; emoji selectors are dropped. */
export function normalizeText(text) { return text.replace(/[\ufe00-\ufe0f\u200b\u200d]/g, '').replace(/[\u2018\u2019\u201a\u2032\u00b4\u201c\u201d\u201e\u2033\u2013\u2014\u2212\u00a0\u2665\u2661\u2763\u2713\u2611\u2715\u2717\u2718\u274c\u2b50\u2606\u2729\u272a\u2730\u2219\u25cf]/g, c => LOOK_ALIKES[c]); }
/** Text as the font draws it: capitals for textCase 'upper' (accented letters included), unchanged for 'original'. */
export function caseText(text, textCase = 'upper') {
  if (textCase === 'original') return text;
  let out = '';
  for (const c of text) { const accented = LATIN_LOWER.indexOf(c); out += c >= 'a' && c <= 'z' ? c.toUpperCase() : accented >= 0 ? LATIN_UPPER[accented] : c; }
  return out;
}
export const QUALITY_PRESETS = Object.freeze({ low: { preset: 'low', width: 512, height: 288 }, medium: { preset: 'medium', width: 1920, height: 1080 }, high: { preset: 'high', width: 2560, height: 1440 } });
export function validateQuality(value) {
  keys(value, ['preset', 'width', 'height'], 'Quality');
  const { preset, width, height } = value;
  if (!Number.isSafeInteger(width) || !Number.isSafeInteger(height) || width < 256 || width > 3840 || width % 32 || height * 16 !== width * 9) throw Error('Quality requires exact 16:9 dimensions, width 256-3840 in steps of 32.');
  if (preset === 'custom') return value;
  const expected = QUALITY_PRESETS[preset];
  if (!expected || expected.width !== width || expected.height !== height) throw Error('Quality preset dimensions must match low 512x288, medium 1920x1080 or high 2560x1440.');
  return value;
}
const cloneLogic = logic => JSON.parse(JSON.stringify(logic));
export function cloneElement(e) { return { ...e, ...(e.style ? { style: { ...e.style } } : {}), ...(e.actionArea ? { actionArea: { ...e.actionArea } } : {}), ...(e.messageCondition ? { messageCondition: { ...e.messageCondition } } : {}), ...(e.logic ? { logic: cloneLogic(e.logic) } : {}) }; }
export function cloneScreen(value) { return { ...value, ...(value.quality ? { quality: { ...value.quality } } : {}), ...(value.presentation ? { presentation: { ...value.presentation } } : {}), ...(value.logic ? { logic: cloneLogic(value.logic) } : {}), elements: value.elements.map(cloneElement) }; }
// Schema 9: behaviour (variables, functions, keybinds, actions...). The server validates every rule on publish;
// the editor checks the shape so mistakes show up while typing.
export const SCREEN_LOGIC_KEYS = Object.freeze(['behavior', 'variables', 'functions', 'keybinds', 'command', 'permission', 'open-on-join', 'on-open', 'on-close', 'animation',
  // 0.23: triggers.
  'timers', 'on-damage', 'on-world-change', 'on-change']);
export const ELEMENT_LOGIC_KEYS = Object.freeze(['actions', 'right-actions', 'hidden', 'visible-if', 'frames', 'list', 'draggable', 'on-drop', 'on-hover', 'on-leave', 'tooltip', 'keyframes',
  // 0.23: click cooldown.
  'cooldown', 'on-cooldown']);
/** A delay-style duration in ms ("2s", "500ms", "20t" or ticks), as ActionParser reads cooldowns and timers; null when it is not one. */
export function durationMs(value) {
  if (typeof value !== 'string' && typeof value !== 'number') return null;
  const t = String(value).trim().toLowerCase();
  const [digits, scale] = t.endsWith('ms') ? [t.slice(0, -2), 1] : t.endsWith('s') ? [t.slice(0, -1), 1000] : t.endsWith('t') ? [t.slice(0, -1), 50] : [t, 50];
  const n = Number(digits);
  return digits.trim() !== '' && Number.isFinite(n) && n >= 0 ? Math.round(n * scale) : null;
}
export const KEYFRAME_TRIGGERS = Object.freeze(['open', 'loop', 'hover', 'action']);
// One-click starting points for the logic boxes (0.21); each merges into the current logic, replacing its keys.
export const ELEMENT_PRESETS = Object.freeze({
  'hover-grow': { keyframes: { trigger: 'hover', easing: 'ease-out', steps: [{ at: 0 }, { at: 150, scale: 1.08 }] } },
  'enter-slide': { keyframes: { trigger: 'open', easing: 'ease-out', steps: [{ at: 0, x: -24, opacity: 0 }, { at: 300, x: 0, opacity: 1 }] } },
  'pulse-loop': { keyframes: { trigger: 'loop', easing: 'ease-in-out', steps: [{ at: 0, scale: 1 }, { at: 400, scale: 1.15 }, { at: 800, scale: 1 }] } },
  'spin-action': { keyframes: { trigger: 'action', easing: 'ease-in-out', steps: [{ at: 0 }, { at: 600, rotation: 360 }] } },
  'hover-sound': { 'on-hover': [{ type: 'sound', value: 'UI_BUTTON_CLICK 0.4 1.6' }] }
});
export const SCREEN_PRESETS = Object.freeze({
  'open-slide-left': { animation: { open: { effect: 'slide', from: 'left', distance: 48, 'duration-ms': 350, easing: 'ease-out' }, close: { effect: 'fade', 'duration-ms': 250 } } },
  'open-slide-right': { animation: { open: { effect: 'slide', from: 'right', distance: 48, 'duration-ms': 350, easing: 'ease-out' }, close: { effect: 'fade', 'duration-ms': 250 } } },
  'open-fade-slow': { animation: { open: { effect: 'fade', 'duration-ms': 500 }, close: { effect: 'fade', 'duration-ms': 400 } } },
  // 0.23: the closing slide; enters from the left and leaves to the right.
  'slide-in-out': { animation: { open: { effect: 'slide', from: 'left', distance: 48, 'duration-ms': 350, easing: 'ease-out' }, close: { effect: 'slide', from: 'right', distance: 96, 'duration-ms': 350, easing: 'ease-in' } } },
  'open-none': { animation: { open: { effect: 'none' }, close: { effect: 'none' } } }
});
/** Logic with {@code patch}'s keys replaced (a deep copy; null logic starts empty). */
export function mergeLogic(logic, patch) {
  const next = JSON.parse(JSON.stringify(logic ?? {}));
  for (const [key, value] of Object.entries(patch)) {
    const aliases = Object.keys(next).filter(existing => kebab(existing) === kebab(key));
    const target = aliases[0] ?? key;
    for (const alias of aliases) delete next[alias];
    Object.defineProperty(next, target, { value: JSON.parse(JSON.stringify(value)), writable: true, configurable: true, enumerable: true });
  }
  return next;
}
export const EASINGS = Object.freeze(['linear', 'ease-in', 'ease-out', 'ease-in-out']);
const KEYFRAME_RANGES = { x: [-256, 256], y: [-144, 144], scale: [.1, 4], opacity: [0, 1], rotation: [-360, 360] };
function validateKeyframes(value, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error(`${name} keyframes must be an object with steps.`);
  for (const key of Object.keys(value)) if (!['trigger', 'easing', 'steps'].includes(key)) throw Error(`${name} keyframes: unknown key ${key}.`);
  if (value.trigger !== undefined && !KEYFRAME_TRIGGERS.includes(value.trigger)) throw Error(`${name} keyframes trigger must be ${KEYFRAME_TRIGGERS.join(', ')}.`);
  if (value.easing !== undefined && !EASINGS.includes(value.easing)) throw Error(`${name} keyframes easing must be ${EASINGS.join(', ')}.`);
  if (!Array.isArray(value.steps) || value.steps.length < 2 || value.steps.length > 16) throw Error(`${name} keyframes need 2 to 16 steps.`);
  let previous = -1;
  value.steps.forEach((step, index) => {
    if (!step || typeof step !== 'object' || Array.isArray(step)) throw Error(`${name} keyframe ${index + 1} must be an object.`);
    for (const key of Object.keys(step)) if (key !== 'at' && !(key in KEYFRAME_RANGES)) throw Error(`${name} keyframe ${index + 1}: unknown key ${key} (at, x, y, scale, opacity, rotation).`);
    const at = step.at ?? 0;
    if (!Number.isInteger(at) || at < 0 || at > 10000 || at <= previous || (index === 0 && at !== 0)) throw Error(`${name} keyframe ${index + 1}: at must start at 0 and increase (ms, up to 10000).`);
    previous = at;
    for (const [key, [min, max]] of Object.entries(KEYFRAME_RANGES)) if (step[key] !== undefined && (typeof step[key] !== 'number' || !Number.isFinite(step[key]) || step[key] < min || step[key] > max))
      throw Error(`${name} keyframe ${index + 1}: ${key} must be ${min} to ${max}.`);
  });
}
function validateTransition(value, name, closing) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error(`${name} must be an object.`);
  for (const key of Object.keys(value)) if (!['effect', 'duration-ms', 'easing', 'from', 'distance'].includes(key)) throw Error(`${name}: unknown key ${key}.`);
  // Closing slides (0.22) move the last frame out towards `from`; opening slides come in from it.
  if (value.effect !== undefined && !['fade', 'slide', 'none'].includes(value.effect)) throw Error(`${name} effect must be fade, slide or none.`);
  if (value['duration-ms'] !== undefined && (!Number.isInteger(value['duration-ms']) || value['duration-ms'] < 0 || value['duration-ms'] > 3000)) throw Error(`${name} duration-ms must be 0 to 3000.`);
  if (value.easing !== undefined && !EASINGS.includes(value.easing)) throw Error(`${name} easing must be ${EASINGS.join(', ')}.`);
  if (value.from !== undefined && !['left', 'right'].includes(value.from)) throw Error(`${name} from must be left or right.`);
  if (value.distance !== undefined && (!Number.isInteger(value.distance) || value.distance < 1 || value.distance > 256)) throw Error(`${name} distance must be 1 to 256.`);
}
export const ACTION_TYPES = Object.freeze(['command', 'player', 'console', 'message', 'actionbar', 'action-bar', 'sound', 'teleport', 'delay', 'wait', 'give',
  'redirect', 'open', 'back', 'close', 'show', 'hide', 'toggle', 'visibility', 'variable', 'set', 'condition', 'if', 'permission', 'function', 'call',
  'input', 'offset', 'move', 'tint', 'color', 'animation', 'animate', 'scroll',
  // 0.23: money through Vault and proxy server changes; yes/no questions and GIF playback; HUD pages and toasts.
  'charge', 'withdraw', 'pay', 'deposit', 'server', 'connect', 'confirm', 'pause', 'resume', 'frame', 'hud', 'toast']);
/** The plugin's own rules for hud and toast (ActionParser, UiAction.Toast), so a page the editor accepts also loads. */
function validateHudAction(action, name) {
  const type = action.type.toLowerCase();
  if (type === 'hud' && action.mode !== undefined && !['add', 'remove'].includes(String(action.mode).toLowerCase())) throw Error(`${name}: hud mode must be add or remove.`);
  if (type !== 'toast' || action.values === undefined || action.values === null) return;
  if (typeof action.values !== 'object' || Array.isArray(action.values) || Object.keys(action.values).length > 16) throw Error(`${name}: toast values must map at most 16 variable names to texts.`);
  for (const [key, value] of Object.entries(action.values))
    if (!/^[*$]?[a-z_][a-z0-9_]{0,23}$/.test(key) || !['string', 'number', 'boolean'].includes(typeof value) || String(value).length > 256)
      throw Error(`${name}: toast value ${key} needs a variable name (a-z, 0-9 and _) and a text of up to 256 characters.`);
}
const kebab = key => key.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase().replaceAll('_', '-');
function validateActions(list, name, depth = 0) {
  if (list === undefined || list === null) return;
  if (!Array.isArray(list) && list && typeof list === 'object' && Array.isArray(list.actions)) list = list.actions;
  if (!Array.isArray(list) || list.length > 128) throw Error(`${name} must be a list of at most 128 actions.`);
  if (depth > 8) throw Error(`${name}: actions are nested more than 8 levels.`);
  for (const action of list) {
    if (!action || typeof action !== 'object' || Array.isArray(action) || typeof action.type !== 'string') throw Error(`${name}: every action needs a type.`);
    if (!ACTION_TYPES.includes(action.type.toLowerCase())) throw Error(`${name}: unknown action type ${action.type}.`);
    validateHudAction(action, name);
    for (const branch of ['then', 'else', 'on-submit', 'onSubmit', 'on-cancel', 'onCancel']) validateActions(action[branch], `${name} ${branch}`, depth + 1);
  }
}
export function validateLogic(logic, element, name) {
  if (!logic || typeof logic !== 'object' || Array.isArray(logic)) throw Error(`${name} logic must be an object.`);
  if (JSON.stringify(logic).length > 262144) throw Error(`${name} logic exceeds 256 KiB.`);
  const allowed = element ? ELEMENT_LOGIC_KEYS : SCREEN_LOGIC_KEYS;
  for (const key of Object.keys(logic)) if (!allowed.includes(kebab(key))) throw Error(`${name} logic: unknown key ${key} (${allowed.join(', ')}).`);
  for (const [key, value] of Object.entries(logic)) {
    const k = kebab(key);
    if (['actions', 'right-actions', 'on-drop', 'on-open', 'on-close', 'on-hover', 'on-leave', 'on-cooldown', 'on-damage', 'on-world-change'].includes(k)) validateActions(value, `${name} ${k}`);
    if (k === 'cooldown') {
      const ms = durationMs(value);
      if (ms === null || ms > 3600000) throw Error(`${name} cooldown must be a duration like "2s" or "500ms", up to one hour.`);
    }
    if (k === 'timers') {
      if (!Array.isArray(value) || value.length > 16) throw Error(`${name} timers must be a list of at most 16 {every, actions}.`);
      value.forEach((timer, index) => {
        if (!timer || typeof timer !== 'object' || Array.isArray(timer) || Object.keys(timer).some(key => !['every', 'actions'].includes(key))) throw Error(`${name} timer ${index + 1} needs only every and actions.`);
        const ms = durationMs(timer.every);
        if (ms === null || ms < 250 || ms > 3600000) throw Error(`${name} timer ${index + 1}: every must be 0.25 to 3600 seconds ("5s", "500ms").`);
        if (!Array.isArray(timer.actions) || !timer.actions.length) throw Error(`${name} timer ${index + 1} needs at least one action.`);
        validateActions(timer.actions, `${name} timer ${index + 1}`);
      });
    }
    if (k === 'on-change') {
      if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length > 64) throw Error(`${name} on-change must map variable names to actions.`);
      const seen = new Set();
      for (const [variable, actions] of Object.entries(value)) {
        const bare = variable.replace(/^[*$]/, '');
        if (!/^[*$]?[a-z_][a-z0-9_]{0,23}$/.test(variable) || seen.has(bare)) throw Error(`${name} on-change: invalid or repeated variable ${variable}.`);
        seen.add(bare); validateActions(actions, `${name} on-change ${variable}`);
      }
    }
    if (k === 'tooltip' && (typeof value !== 'string' || !ID.test(value))) throw Error(`${name} tooltip must be an element id.`);
    if (k === 'keyframes') validateKeyframes(value, name);
    if (k === 'animation') {
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error(`${name} animation must be an object.`);
      for (const key of Object.keys(value)) if (!['open', 'close', 'close-reversed'].includes(key)) throw Error(`${name} animation: unknown key ${key}.`);
      if (value.open !== undefined) validateTransition(value.open, `${name} open animation`, false);
      if (value.close !== undefined) validateTransition(value.close, `${name} close animation`, true);
    }
    if (k === 'functions') for (const [fn, body] of Object.entries(value ?? {})) {
      if (!/^[a-z][a-z0-9_-]{0,31}$/.test(fn)) throw Error(`${name}: invalid function name ${fn}.`);
      validateActions(body?.actions, `${name} function ${fn}`);
    }
    if (k === 'keybinds') for (const bind of Array.isArray(value) ? value : Object.entries(value ?? {}).map(([key, actions]) => ({ key, actions }))) validateActions(bind?.actions, `${name} keybind ${bind?.key}`);
    if (k === 'list') validateActions(value?.['on-select'] ?? value?.onSelect, `${name} list on-select`);
    if (['hidden', 'draggable', 'open-on-join'].includes(k) && typeof value !== 'boolean') throw Error(`${name} ${k} must be true or false.`);
  }
  const keys = Object.keys(logic).map(kebab);
  if (element && keys.includes('on-cooldown') && !keys.includes('cooldown')) throw Error(`${name}: on-cooldown needs a cooldown.`);
  return logic;
}
export function effectiveActionArea(element) { const { x, y, width, height } = element.actionArea ?? element; return { x, y, width, height }; }
export function orderedElements(screen) { return screen.elements.map((element, index) => ({ element, index })).sort((a, b) => (a.element.layer ?? 0) - (b.element.layer ?? 0)); }
export function areasOverlap(a, b) { return Math.abs(a.x - b.x) < (a.width + b.width) / 2 && Math.abs(a.y - b.y) < (a.height + b.height) / 2; }
export function validateActionAreas(screen) {
  const buttons = screen.elements.filter(e => e.kind === 'button');
  for (let i = 0; i < buttons.length; i++) for (let j = i + 1; j < buttons.length; j++) if (areasOverlap(effectiveActionArea(buttons[i]), effectiveActionArea(buttons[j]))) throw Error(`Button action areas overlap: ${buttons[i].id} and ${buttons[j].id}. Move or resize the action area before export.`);
  return screen;
}
// Translation candidates touch an occupied edge. A complete group keeps its relative positions.
export function placeElements(screen, elements, preferredX = .04, preferredY = -.04) {
  const moving = elements.filter(e => e.kind === 'button').map(effectiveActionArea), fixed = screen.elements.filter(e => e.kind === 'button').map(effectiveActionArea);
  const bounds = elements.flatMap(e => e.actionArea ? [e, e.actionArea] : [e]);
  const minX = Math.max(...bounds.map(e => -1 - e.x)), maxX = Math.min(...bounds.map(e => 1 - e.x));
  const minY = Math.max(...bounds.map(e => -1 - e.y)), maxY = Math.min(...bounds.map(e => 1 - e.y));
  const bounded = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
  const dx = bounded(preferredX, minX, maxX), dy = bounded(preferredY, minY, maxY);
  const xs = new Set([dx, minX, maxX, 0]), ys = new Set([dy, minY, maxY, 0]);
  for (const a of moving) for (const b of fixed) { xs.add(b.x - a.x - (a.width + b.width) / 2); xs.add(b.x - a.x + (a.width + b.width) / 2); ys.add(b.y - a.y - (a.height + b.height) / 2); ys.add(b.y - a.y + (a.height + b.height) / 2); }
  const candidates = (set, target, lo, hi) => [...set].filter(v => v >= lo && v <= hi).sort((a, b) => Math.abs(a - target) - Math.abs(b - target));
  let attempts = 0;
  for (const x of candidates(xs, dx, minX, maxX)) for (const y of candidates(ys, dy, minY, maxY)) {
    if (++attempts > 50000) throw Error('No free action space found within the bounded placement search. Move existing buttons and try again.');
    if (moving.some(a => fixed.some(b => areasOverlap({ ...a, x: a.x + x, y: a.y + y }, b)))) continue;
    const placed = elements.map(e => ({ ...e, x: e.x + x, y: e.y + y, ...(e.actionArea ? { actionArea: { ...e.actionArea, x: e.actionArea.x + x, y: e.actionArea.y + y } } : {}) }));
    const actualAreas = placed.filter(e => e.kind === 'button').map(effectiveActionArea);
    if (actualAreas.some(a => fixed.some(b => areasOverlap(a, b)))) continue;
    if (actualAreas.some((a, i) => actualAreas.slice(i + 1).some(b => areasOverlap(a, b)))) continue;
    return placed;
  }
  throw Error('No free room for these button action areas. Move or resize existing buttons and try again.');
}
const ID = /^[a-z][a-z0-9_-]{0,31}$/;
// Cache only immutable PNG strings present in the document being validated.
const structurallyValidImages = new Set();
const ASCII = /^[\x20-\x7e]*$/;
export const MAX_BYTES = 134217728;
export function promoteScreen(value) {
  const next = cloneScreen(validateScreen(value));
  if (next.version === 1) {
    next.elements = next.elements.map(e => ({ ...e, shape: 'rectangle', border: true, messageCondition: null }));
  }
  if (next.version < 3) {
    next.version = 3;
    next.elements = next.elements.map(e => ({ ...e, background: false, image: null, style: { ...DEFAULT_STYLE }, layer: 0, actionArea: null }));
  }
  if (next.version < 4) {
    next.version = 4; next.quality = { preset: 'custom', width: 1024, height: 576 };
    next.elements = next.elements.map(e => ({ ...e, style: { ...DEFAULT_STYLE }, layer: 0, actionArea: null }));
  }
  return validateScreen(next);
}

export function upgradeAppearance(value) {
  const next = promoteScreen(value);
  if (next.version >= 6) return next;
  next.version = 5;
  next.elements = next.elements.map(element => ({ ...element, style: { ...DEFAULT_APPEARANCE_STYLE, ...element.style } }));
  return validateScreen(next);
}

export function upgradePresentation(value) {
  const next = upgradeAppearance(value);
  if (next.version >= 7) return next;
  next.version = 6;
  next.presentation = { ...DEFAULT_PRESENTATION, ...next.presentation };
  next.elements = next.elements.map(element => ({ ...element, style: { ...DEFAULT_PRESENTATION_STYLE, ...element.style } }));
  return validateScreen(next);
}

export function upgradeTextStyle(value) {
  const next = upgradePresentation(value);
  if (next.version >= 8) return next;
  next.version = 7;
  next.elements = next.elements.map(element => ({ ...element, style: { ...DEFAULT_TEXT_STYLE, ...element.style } }));
  return validateScreen(next);
}

export function upgradeGradient(value) {
  const next = upgradeTextStyle(value);
  if (next.version >= 9) return next;
  next.version = 8;
  next.elements = next.elements.map(element => ({ ...element, style: { ...DEFAULT_GRADIENT_STYLE, ...element.style } }));
  return validateScreen(next);
}

/** Schema 9 keeps every schema 8 field and adds optional logic. */
export function upgradeLogic(value) {
  const next = upgradeGradient(value);
  if (next.version >= 9) return next;
  // Word colour tags are schema 9: a "<gold>" the page had as text stays text (RichText.escape, as the plugin reads it).
  next.elements = next.elements.map(element => ({ ...element, text: escapeTags(element.text) }));
  next.version = 9;
  return validateScreen(next);
}
/** Sets (or with null removes) the screen's logic; upgrades the screen to schema 9. */
export function setScreenLogic(value, logic) {
  const next = upgradeLogic(value);
  if (logic === null) delete next.logic; else next.logic = validateLogic(cloneLogic(logic), false, 'Screen');
  return validateScreen(next);
}
/** Sets (or with null removes) an element's logic; upgrades the screen to schema 9. */
export function setElementLogic(value, selected, logic) {
  const next = upgradeLogic(value), index = next.elements.findIndex(e => e.id === selected);
  if (index < 0) throw new Error(`Unknown element: ${selected}.`);
  const element = { ...next.elements[index] };
  if (logic === null) delete element.logic; else element.logic = validateLogic(cloneLogic(logic), true, element.id);
  next.elements[index] = element;
  return validateScreen(next);
}
function optionalKeys(value, required, optional, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !required.every(key => Object.hasOwn(value, key))
      || Object.keys(value).some(key => !required.includes(key) && !optional.includes(key)))
    throw new Error(`${name} keys must be: ${required.join(', ')} (optional: ${optional.join(', ')}).`);
}
function keys(value, expected, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)
      || Object.keys(value).length !== expected.length || !expected.every(key => Object.hasOwn(value, key)))
    throw new Error(`${name} keys must be exactly: ${expected.join(', ')}.`);
}
function id(value, name) { if (typeof value !== 'string' || !ID.test(value)) throw new Error(`${name} must be a lowercase ID (1-32 characters).`); }
function ascii(value, min, max, name) {
  if (typeof value !== 'string' || value.length < min || value.length > max || !ASCII.test(value))
    throw new Error(`${name} must contain ${min}-${max} printable ASCII characters.`);
}
function number(value, min, max, name, exclusive = false) {
  if (typeof value !== 'number' || !Number.isFinite(value) || (exclusive ? value <= min : value < min) || value > max)
    throw new Error(`${name} must be a finite number ${exclusive ? 'greater than' : 'from'} ${min} and at most ${max}.`);
}
export function validateScreen(value) {
  if (!value || ![1, 2, 3, 4, 5, 6, 7, 8, 9].includes(value.version)) throw new Error('Screen version must be 1 to 9.');
  if (value.version >= 9) { optionalKeys(value, V6_ROOT_KEYS, ['logic'], 'Screen'); if (value.logic !== undefined) validateLogic(value.logic, false, 'Screen'); }
  else keys(value, value.version >= 6 ? V6_ROOT_KEYS : value.version >= 4 ? V4_ROOT_KEYS : ROOT_KEYS, 'Screen');
  if (value.version >= 4) validateQuality(value.quality);
  if (value.version >= 6) {
    if (value.version >= 9) {
      optionalKeys(value.presentation, Object.keys(DEFAULT_PRESENTATION), ['cursorSize', 'cursorSpeed', 'stack'], 'Presentation');
      // A HUD page that stacks shows together with the HUD pages already on screen instead of replacing them.
      const stack = value.presentation.stack;
      if (stack !== undefined && stack !== null && typeof stack !== 'boolean') throw Error('Presentation stack must be true or false.');
      if (stack === true && value.presentation.mode !== 'overlay') throw Error('Presentation stack is for HUD pages (mode overlay).');
      if (value.presentation.cursorSize !== undefined && value.presentation.cursorSize !== null) number(value.presentation.cursorSize, .25, 1.5, 'Presentation cursorSize');
      if (value.presentation.cursorSpeed !== undefined) number(value.presentation.cursorSpeed, .25, 4, 'Presentation cursorSpeed');
    } else keys(value.presentation, Object.keys(DEFAULT_PRESENTATION), 'Presentation');
    if (!['menu', 'overlay'].includes(value.presentation.mode) || !['cross', 'block', 'text', 'arrow'].includes(value.presentation.cursor)
        || !Number.isInteger(value.presentation.scale) || value.presentation.scale < 1 || value.presentation.scale > 3) throw Error('Presentation requires mode menu/overlay, scale integer1-3 and cursor cross/block/text/arrow.');
  }
  id(value.id, 'Screen id');
  if (value.version >= 9) { if (typeof value.title !== 'string' || value.title.length < 1 || value.title.length > 40 || value.title.includes('\n') || !drawableText(value.title)) throw new Error('Screen title must contain 1-40 characters the game font draws (ASCII, accents, symbols).'); }
  else ascii(value.title, 1, 40, 'Screen title');
  const limit = value.version >= 4 ? 128 : 24;
  if (!Array.isArray(value.elements) || value.elements.length > limit) throw new Error(`Screen elements must be an array with at most ${limit} entries.`);
  const currentImages = new Set(value.elements.flatMap(e => [e?.image, e?.hoverImage]).filter(image => typeof image === 'string'));
  for (const image of structurallyValidImages) if (!currentImages.has(image)) structurallyValidImages.delete(image);
  const seen = new Set(), validatedImages = new Set();
  for (const e of value.elements) {
    // Schema 9: optional logic, and hoverImage (a button's image while the cursor is on it, like Ultimate UI's hover image).
    if (value.version >= 9) optionalKeys(e, V4_ELEMENT_KEYS, ['logic', 'hoverImage', 'item'], 'Element');
    else keys(e, value.version === 1 ? ELEMENT_KEYS : value.version === 2 ? V2_ELEMENT_KEYS : value.version === 3 ? V3_ELEMENT_KEYS : V4_ELEMENT_KEYS, 'Element');
    id(e.id, 'Element id');
    if (e.logic !== undefined) {
      validateLogic(e.logic, true, e.id);
      const interactive = ['actions', 'right-actions', 'on-drop', 'draggable', 'rightActions', 'onDrop', 'on-hover', 'on-leave', 'onHover', 'onLeave', 'tooltip'].some(key => key in e.logic && !(Array.isArray(e.logic[key]) && e.logic[key].length === 0) && e.logic[key] !== false)
        || e.logic.keyframes?.trigger === 'hover';
      if (interactive && e.kind !== 'button') throw Error(`${e.id}: actions, right actions, hover actions, tooltips and drag and drop need a button.`);
      if (e.logic.tooltip === e.id) throw Error(`${e.id} cannot be its own tooltip.`);
      if (e.logic.keyframes && e.logic.frames) throw Error(`${e.id}: animate with PNG frames or with keyframes, not both.`);
      if (e.logic.list && (e.logic.frames || e.logic.keyframes)) throw Error(`${e.id}: a data list cannot also use frames or keyframes.`);
      if (e.logic.list) {
        const list = e.logic.list, rows = list.rows ?? 5, spacing = list.spacing ?? 16;
        if (typeof list !== 'object' || Array.isArray(list) || !Number.isInteger(rows) || rows < 1 || rows > 16 || !Number.isInteger(spacing) || spacing < 1 || spacing > 72) throw Error(`${e.id}: list rows must be 1–16 and spacing 1–72 logical pixels.`);
        // Match ListRows.fits in Java: the last row's centre must remain on the canvas.
        if (e.y - (rows - 1) * spacing / 72 < -1) throw Error(`${e.id}: list runs off the bottom of the screen; use fewer rows, less spacing, or move it up.`);
      }
    }
    if (seen.has(e.id)) throw new Error(`Duplicate element id: ${e.id}.`);
    seen.add(e.id);
    if (value.version >= 9) {
      if (typeof e.text !== 'string' || e.text.length > 512 || !drawableText(e.text)) throw Error(`${e.id} text requires at most 512 characters the game font draws (ASCII, line feeds, accents, symbols).`);
      if (value.version >= 9 && wordColors(e.text).length > MAX_COLORS) throw Error(`${e.id} text uses more than ${MAX_COLORS} word colours.`);
    } else if (value.version >= 5) {
      if (typeof e.text !== 'string' || e.text.length > 512 || !/^[\x20-\x7e\n]*$/.test(e.text)) throw Error(`${e.id} text requires at most512 printableASCII characters or line feeds.`);
    } else ascii(e.text, 0, 64, `${e.id} text`);
    number(e.x, -1, 1, `${e.id} x`); number(e.y, -1, 1, `${e.id} y`);
    number(e.width, 0, 2, `${e.id} width`, true); number(e.height, 0, 2, `${e.id} height`, true);
    if (e.kind !== 'label' && e.kind !== 'button' && !(value.version >= 4 && e.kind === 'decoration')) throw new Error(`${e.id} kind must be label, button or v4+ decoration.`);
    if (value.version >= 2) {
      if (!['rectangle', 'rounded', 'ellipse'].includes(e.shape)) throw new Error(`${e.id} shape must be rectangle, rounded or ellipse.`);
      if (typeof e.border !== 'boolean') throw new Error(`${e.id} border must be true or false.`);
      if (e.action === 'conditional-message') {
        if (e.kind !== 'button') throw new Error(`${e.id} conditional message requires a button.`);
        keys(e.messageCondition, ['permission', 'ifTrue', 'ifFalse'], `${e.id} message condition`);
        if (typeof e.messageCondition.permission !== 'string' || !/^[a-z0-9_.-]{1,128}$/.test(e.messageCondition.permission))
          throw new Error(`${e.id} condition permission must contain 1-128 lowercase permission characters.`);
        ascii(e.messageCondition.ifTrue, 0, 100, `${e.id} condition ifTrue`);
        ascii(e.messageCondition.ifFalse, 0, 100, `${e.id} condition ifFalse`);
      } else if (e.messageCondition !== null) throw new Error(`${e.id} message condition must be null for this action.`);
    }
    if (value.version >= 3) {
      if (typeof e.background !== 'boolean') throw new Error(`${e.id} background must be true or false.`);
      if (e.image !== null && !validatedImages.has(e.image)) {
        try { if (!structurallyValidImages.has(e.image)) { validatePngBase64(e.image); structurallyValidImages.add(e.image); } validatedImages.add(e.image); } catch (error) { throw new Error(`${e.id} image: ${error.message}`); }
      }
      if (e.hoverImage !== undefined) {
        if (e.kind !== 'button') throw new Error(`${e.id}: a hover image needs a button (only buttons are hovered).`);
        if (typeof e.hoverImage !== 'string') throw new Error(`${e.id} hover image must be PNG Base64.`);
        if (!validatedImages.has(e.hoverImage)) {
          try { if (!structurallyValidImages.has(e.hoverImage)) { validatePngBase64(e.hoverImage); structurallyValidImages.add(e.hoverImage); } validatedImages.add(e.hoverImage); } catch (error) { throw new Error(`${e.id} hover image: ${error.message}`); }
        }
      }
    }
    if (value.version >= 9 && e.item !== undefined) validateItemIcon(e);
    if (value.version >= 4) {
      if (value.version >= 9) {
        optionalKeys(e.style, Object.keys(DEFAULT_GRADIENT_STYLE), ['textCase', ...BLUR_FIELDS, ...SLICE_FIELDS, 'imageClip'], `${e.id} style`);
        if (e.style.imageClip !== undefined && typeof e.style.imageClip !== 'boolean') throw Error(`${e.id} style imageClip must be true or false.`);
        if (e.style.textCase !== undefined && !TEXT_CASES.includes(e.style.textCase)) throw Error(`${e.id} textCase requires upper or original.`);
        for (const field of BLUR_FIELDS) if (e.style[field] !== undefined && (!Number.isInteger(e.style[field]) || e.style[field] < 0 || e.style[field] > MAX_BLUR))
          throw Error(`${e.id} ${field} requires an integer 0-${MAX_BLUR}.`);
        if (e.style.slice !== undefined && (!Number.isInteger(e.style.slice) || e.style.slice < 0 || e.style.slice > 64)) throw Error(`${e.id} style slice must be an integer 0-64.`);
        if (e.style.sliceScale !== undefined && (!Number.isInteger(e.style.sliceScale) || e.style.sliceScale < 1 || e.style.sliceScale > 4)) throw Error(`${e.id} style sliceScale must be an integer 1-4.`);
        if (e.style.slice > 0) for (const picture of [e.image, e.hoverImage]) {
          if (typeof picture !== 'string') continue;
          const { width, height } = validatePngBase64(picture);
          if (2 * e.style.slice >= Math.min(width, height)) throw Error(`${e.id}: the 9-slice border (${e.style.slice} px) must leave the image a middle (it is ${width} x ${height}).`);
        }
      } else keys(e.style, value.version >= 8 ? Object.keys(DEFAULT_GRADIENT_STYLE) : value.version === 7 ? Object.keys(DEFAULT_TEXT_STYLE) : value.version === 6 ? Object.keys(DEFAULT_PRESENTATION_STYLE) : value.version === 5 ? Object.keys(DEFAULT_APPEARANCE_STYLE) : Object.keys(DEFAULT_STYLE), `${e.id} style`);
      number(e.style.opacity, 0, 1, `${e.id} style opacity`);
      for (const field of ['fillColor', 'borderColor', 'textColor']) if (typeof e.style[field] !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(e.style[field])) throw Error(`${e.id} style ${field} requires #RRGGBB.`);
      if (typeof e.style.shadow !== 'boolean') throw Error(`${e.id} style shadow must be boolean.`);
      if (value.version >= 5) {
        number(e.style.rotation, -180, 180, `${e.id} rotation`); number(e.style.radius, 0, .5, `${e.id} radius`);
        if (!Number.isInteger(e.style.outlineWidth) || e.style.outlineWidth < 0 || e.style.outlineWidth > 8) throw Error(`${e.id} outlineWidth requires an integer0-8.`);
        if (!Number.isInteger(e.style.wrapWidth) || e.style.wrapWidth < 0 || e.style.wrapWidth > 256) throw Error(`${e.id} wrapWidth requires an integer0-256.`);
        if (e.style.hoverColor !== null && (typeof e.style.hoverColor !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(e.style.hoverColor))) throw Error(`${e.id} hoverColor requires null or#RRGGBB.`);
        if (!HOVER_EFFECTS.includes(e.style.hoverEffect)) throw Error(`${e.id} hoverEffect requires none, translate, scale or glow.`);
        if (!['left', 'center', 'right'].includes(e.style.textAlign)) throw Error(`${e.id} textAlign requires left, center or right.`);
      }
      if (value.version >= 6) number(e.style.brightness, 0, 2, `${e.id} brightness`);
      if (value.version >= 8) {
        for (const field of ['gradientColor', 'textGradient'])
          if (e.style[field] !== null && (typeof e.style[field] !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(e.style[field]))) throw Error(`${e.id} ${field} requires null or #RRGGBB.`);
        if (!GRADIENT_DIRECTIONS.includes(e.style.gradientDirection)) throw Error(`${e.id} gradientDirection requires vertical, horizontal, diagonal or radial.`);
      }
      if (value.version >= 7) {
        if (!Number.isInteger(e.style.textScale) || e.style.textScale < 1 || e.style.textScale > 4) throw Error(`${e.id} textScale requires an integer 1-4.`);
        if (e.style.textOutline !== null && (typeof e.style.textOutline !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(e.style.textOutline))) throw Error(`${e.id} textOutline requires null or #RRGGBB.`);
        if (typeof e.style.textShadow !== 'boolean') throw Error(`${e.id} textShadow must be boolean.`);
      }
      if (!Number.isInteger(e.layer) || e.layer < -128 || e.layer > 128) throw Error(`${e.id} layer requires an integer from -128 to 128.`);
      if (e.actionArea !== null) {
        keys(e.actionArea, ['x', 'y', 'width', 'height'], `${e.id} action area`);
        number(e.actionArea.x, -1, 1, `${e.id} action area x`); number(e.actionArea.y, -1, 1, `${e.id} action area y`);
        number(e.actionArea.width, 0, 2, `${e.id} action area width`, true); number(e.actionArea.height, 0, 2, `${e.id} action area height`, true);
      }
      if (e.kind === 'decoration' && (e.action !== '' || e.messageCondition !== null)) throw Error(`${e.id} decoration must have no action or condition.`);
    }
    if (typeof e.action !== 'string' || !(e.action === '' || e.action === 'close'
        || (value.version >= 2 && e.action === 'conditional-message')
        || /^open:[a-z][a-z0-9_-]{0,31}$/.test(e.action) || /^message:[\x20-\x7e]{0,100}$/.test(e.action)
        || /^command:\/?[a-z0-9_.:-]{1,32}( [\x20-\x7e]{0,96})?$/.test(e.action)))
      throw new Error(`${e.id} action must be empty, close, open:id, message:text, or command:/name args (run as the player).`);
  }
  if (value.version >= 4) validateActionAreas(value);
  return value;
}

function exactVersion(raw) {
  const match = raw.match(/^(-?)(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/);
  if (!match || match[1]) return false;
  let digits = (match[2] + (match[3] ?? '')).replace(/^0+/, '');
  let exponent = Number(match[4] ?? 0) - (match[3]?.length ?? 0);
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); exponent++; }
  return ['1', '2', '3', '4', '5', '6', '7', '8', '9'].includes(digits) && exponent === 0;
}

function exactIntegerToken(raw) {
  const match = raw.match(/^-?(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/);
  if (!match) return false;
  let digits = (match[1] + (match[2] ?? '')).replace(/^0+/, '');
  let exponent = Number(match[3] ?? 0) - (match[2]?.length ?? 0);
  while (digits.endsWith('0')) { digits = digits.slice(0, -1); exponent++; }
  return !digits || exponent >= 0;
}

// A small JSON reader preserves keys until duplicates can be checked. JSON.parse
// alone silently drops duplicate keys and rounds high-precision version tokens.
export function parseScreen(raw) {
  if (typeof raw !== 'string') throw new Error('Import must be JSON text.');
  if (new TextEncoder().encode(raw).length > MAX_BYTES) throw new Error('JSON exceeds the 128 MiB limit.');
  let cursor = 0;
  const fail = message => { throw new Error(`${message} At character ${cursor + 1}.`); };
  const whitespace = () => { while (/[\x20\t\r\n]/.test(raw[cursor] ?? '') && cursor < raw.length) cursor++; };
  function string() {
    const start = cursor++;
    while (cursor < raw.length) {
      const ch = raw[cursor++];
      if (ch === '\\') cursor++;
      else if (ch === '"') {
        try { return JSON.parse(raw.slice(start, cursor)); } catch { fail('Invalid JSON string.'); }
      }
    }
    fail('Unterminated JSON string.');
  }
  function value(depth, path) {
    if (depth > 24) fail('JSON nesting exceeds 24 levels.');
    whitespace();
    if (raw[cursor] === '"') return string();
    if (raw[cursor] === '{') {
      cursor++; whitespace(); const pairs = [], seen = new Set();
      if (raw[cursor] === '}') { cursor++; return {}; }
      while (cursor < raw.length) {
        if (raw[cursor] !== '"') fail('Expected a JSON key.');
        const key = string();
        if (seen.has(key)) fail(`Duplicate JSON key: ${key}.`);
        seen.add(key); whitespace();
        if (raw[cursor++] !== ':') fail('Expected a colon.');
        pairs.push([key, value(depth + 1, `${path}/${key}`)]); whitespace();
        const separator = raw[cursor++];
        if (separator === '}') return Object.fromEntries(pairs);
        if (separator !== ',') fail('Expected a comma or closing brace.');
        whitespace();
      }
      fail('Unterminated JSON object.');
    }
    if (raw[cursor] === '[') {
      cursor++; whitespace(); const items = [];
      if (raw[cursor] === ']') { cursor++; return items; }
      while (cursor < raw.length) {
        items.push(value(depth + 1, `${path}/${items.length}`)); whitespace();
        const separator = raw[cursor++];
        if (separator === ']') return items;
        if (separator !== ',') fail('Expected a comma or closing bracket.');
      }
      fail('Unterminated JSON array.');
    }
    for (const [token, result] of [['true', true], ['false', false], ['null', null]]) {
      if (raw.startsWith(token, cursor)) { cursor += token.length; return result; }
    }
    const match = raw.slice(cursor).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/);
    if (match) {
      cursor += match[0].length;
      if (path === '/version' && !exactVersion(match[0])) fail('Screen version must be exactly 1 to 9.');
      if ((path === '/presentation/scale' || /^\/quality\/(?:width|height)$/.test(path) || /^\/elements\/\d+\/layer$/.test(path) || /^\/elements\/\d+\/style\/(?:outlineWidth|wrapWidth|textScale)$/.test(path)) && !exactIntegerToken(match[0])) fail('Quality dimensions, layers and appearance widths must be exact integers.');
      return Number(match[0]);
    }
    fail('Expected a JSON value.');
  }
  const result = value(1, ''); whitespace();
  if (cursor !== raw.length) fail('Unexpected content after JSON.');
  return validateScreen(result);
}

// Base64 is immutable ASCII and JSON does not escape its alphabet. Count its
// bytes separately so mouse edits do not stringify up to 128 MiB on each event.
export function screenByteLength(value) {
  const small = JSON.stringify(value, (key, item) => key === 'image' && typeof item === 'string' ? '' : item, 2) + '\n';
  return new TextEncoder().encode(small).length + value.elements.reduce((sum, e) => sum + (typeof e.image === 'string' ? e.image.length : 0), 0);
}
export function validateScreenBudget(value) {
  validateScreen(value);
  if (screenByteLength(value) > MAX_BYTES) throw Error('JSON exceeds the 128 MiB limit.');
  return value;
}
/** Element "item" (schema 9), as ItemIcons.java: the box's smaller side in whole steps of 16 logical px, 16 to 64. */
export function iconSize(e) { return Math.max(16, Math.min(64, Math.floor(Math.min(e.width * 128, e.height * 72) / 16 + 1e-9) * 16)); }
/** The title cannot draw a glyph whose bottom edge sits above y 64 of 144, so icons live in the lower part of a menu. */
export const ICON_MIN_BOTTOM = 64;
export function iconFits(e) { return (1 - e.y) * 72 + iconSize(e) / 2 >= ICON_MIN_BOTTOM - 1e-9; }
function validateItemIcon(e) {
  if (typeof e.item !== 'string' || !/^[a-z0-9_]{1,64}$/.test(e.item)) throw new Error(`${e.id} item must be a game item id such as diamond.`);
  if (e.logic?.list || e.logic?.frames || e.logic?.keyframes) throw new Error(`${e.id}: an item icon cannot go on a list, a PNG/GIF animation or keyframes.`);
  if (!iconFits(e)) throw new Error(`${e.id}: an item icon must sit in the lower part of the menu (its bottom edge at y 64 or lower of 144); the game cannot draw it higher.`);
  if (!iconFollowsStyle(e.style)) throw new Error(`${e.id}: an item icon is the game's own texture and cannot follow the element's look: use opacity 1 (or 0 to hide it), no rotation, blur, shadow or brightness change, and a hover effect of none or glow.`);
}
/** As ItemIcons.followsStyle: the game draws the texture as it is (opacity 0 hides element and icon alike). */
export function iconFollowsStyle(style = {}) {
  const opacity = style.opacity ?? 1;
  return (opacity === 0 || opacity === 1) && (style.rotation ?? 0) === 0 && (style.blur ?? 0) === 0 && (style.brightness ?? 1) === 1
    && style.shadow !== true && ['none', 'glow'].includes(style.hoverEffect ?? 'none');
}
export function serializeScreen(value) {
  validateScreenBudget(value);
  return JSON.stringify(value, null, 2) + '\n';
}
export async function parseScreenForPublication(raw, decode) {
  const screen = promoteScreen(parseScreen(raw));
  await validateScreenImages(screen, decode);
  validateScreenBudget(screen);
  return screen;
}
export function createScreen() {
  return { version: 4, id: 'menu', title: 'TIDEFRAME', quality: { ...QUALITY_PRESETS.low }, elements: [
    { id: 'heading', text: 'TIDEFRAME', x: 0, y: 0.55, width: 1.4, height: 0.18, kind: 'label', action: '' },
    { id: 'start', text: 'START', x: 0, y: 0.05, width: 0.9, height: 0.22, kind: 'button', action: 'message:Welcome to Tideframe.' },
    { id: 'close', text: 'CLOSE', x: 0, y: -0.35, width: 0.9, height: 0.22, kind: 'button', action: 'close' }
  ].map(e => ({ ...e, shape: 'rectangle', border: true, messageCondition: null, background: false, image: null, style: { ...DEFAULT_STYLE }, layer: 0, actionArea: null })) };
}
function copy(value) { return cloneScreen(validateScreen(value)); }
export function addElement(value, kind) {
  const next = promoteScreen(value);
  let n = 1; while (next.elements.some(e => e.id === `${kind}${n}`)) n++;
  const element = { id: `${kind}${n}`, text: kind === 'button' ? 'BUTTON' : kind === 'decoration' ? '' : 'LABEL', x: 0, y: 0, width: 0.8, height: 0.18, kind, action: '',
    shape: 'rectangle', border: kind !== 'decoration', messageCondition: null, background: false, image: null, style: { ...(next.version >= 8 ? DEFAULT_GRADIENT_STYLE : next.version === 7 ? DEFAULT_TEXT_STYLE : next.version === 6 ? DEFAULT_PRESENTATION_STYLE : next.version === 5 ? DEFAULT_APPEARANCE_STYLE : DEFAULT_STYLE) }, layer: 0, actionArea: null };
  next.elements.push(...placeElements(next, [element], 0, 0));
  return validateScreen(next);
}
export function replaceElement(value, selected, replacement) {
  const next = copy(value), index = next.elements.findIndex(e => e.id === selected);
  if (index < 0) throw new Error(`Unknown element: ${selected}.`);
  next.elements[index] = cloneElement(replacement);
  return validateScreen(next);
}
export function moveElement(value, selected, x, y) {
  const e = validateScreen(value).elements.find(item => item.id === selected);
  if (!e) throw new Error(`Unknown element: ${selected}.`);
  return replaceElement(value, selected, { ...e, x, y });
}
export function deleteElement(value, selected) {
  const next = copy(value);
  next.elements = next.elements.filter(e => e.id !== selected);
  return validateScreen(next);
}
