// "Probar": an in-browser run of a project's pages, following the plugin's own rules where a browser can (navigation,
// visibility, variables, conditions, functions, offsets, tints, keyframe triggers, list rows, cooldowns, confirm
// questions, timers, damage and world triggers, on-change, calculated text, GIF playback). Everything that only
// exists in the game (chat, commands, sounds, items, teleports, dialogs, other plugins' data) is written to a log
// instead of being pretended. Pure: no DOM. The page drives it (play-ui.mjs).
import { renderExpressions, arithmetic, roundText, decimal } from './expressions.mjs';

const MAX_STEPS = 2048, MAX_DEPTH = 8, MAX_CHANGE_DEPTH = 8;
const kebab = key => key.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase().replaceAll('_', '-');
/** {@code object[key]} accepting the camelCase and snake_case spellings the plugin also reads. */
function field(object, key) {
  if (!object || typeof object !== 'object') return undefined;
  const found = Object.keys(object).find(k => kebab(k) === key);
  return found === undefined ? undefined : object[found];
}
const text = value => value === undefined || value === null ? '' : String(value);

/** Player sample values, as the editor preview shows them (the plugin's built-in {tokens}). */
export const SAMPLES = Object.freeze({ player: 'Steve', health: '18', max_health: '20', food: '17', level: '12', xp: '45%', balance: '1250', online: '7',
  max_players: '20', ping: '42', world: 'world', x: '120', y: '64', z: '-35', time: '12:30' });

/**
 * A new run of {@code project}'s page {@code pageId}. {@code permissions}: whether the simulated player has every permission;
 * {@code events}: a list that records what the game would do, in the plugin's words; {@code confirm}: answers yes/no questions.
 */
export function startPlay(project, pageId, { permissions = true, now = 0, events = undefined, confirm = undefined, ask = undefined } = {}) {
  // The dialogs come in with the session: a page's on-open actions may already ask.
  const session = { project, pageId: null, history: [], global: new Map(), locals: new Map(), permissions, closed: false, log: [], view: null, transition: null, now,
    balance: Number(SAMPLES.balance), samples: { ...SAMPLES }, placeholders: new Map(), cooldowns: new Map(), seen: new Map(), changing: new Set(), changeDepth: 0, events, confirm, ask };
  open(session, pageId, 'open');
  return session;
}
export function page(session, id = session.pageId) { return session.project.pages.find(entry => entry.screen.id === id)?.screen ?? null; }
function elements(session) { return page(session)?.elements ?? []; }
function element(session, id) { return elements(session).find(e => e.id === id) ?? null; }
// The log only grows during a run (the page shows its last 200 lines); a restart begins a new one.
function note(session, kind, message) { session.log.push({ kind, text: message }); }
/** What the game would do, in the words the plugin's own tests use ("message <text>", "open shop"), when a test asks for it. */
function emit(session, event) { session.events?.push(event); }

function open(session, id, how) {
  const screen = page(session, id);
  if (!screen) { note(session, 'error', `No hay una página "${id}" en este proyecto: en el juego se abriría si existe en el servidor.`); return false; }
  session.pageId = id; session.closed = false; session.locals = new Map();
  for (const [name, value] of Object.entries(field(screen.logic, 'variables') ?? {})) {
    if (/^[*$]/.test(name)) { if (!session.global.has(name.slice(1))) session.global.set(name.slice(1), text(value)); }
    else session.locals.set(name, text(value));
  }
  const hidden = new Set(), plays = new Map();
  for (const e of screen.elements) {
    if (field(e.logic, 'hidden') === true) hidden.add(e.id);
    const tip = field(e.logic, 'tooltip'); if (tip && tip !== e.id) hidden.add(tip);
    const trigger = field(field(e.logic, 'keyframes'), 'trigger') ?? 'open';
    if (field(e.logic, 'keyframes') && (trigger === 'open' || trigger === 'loop')) plays.set(e.id, { at: session.now, mode: trigger });
  }
  session.view = { hidden, offsets: new Map(), tints: new Map(), plays, scroll: new Map(), hover: null, playback: new Map() };
  session.transition = { phase: 'open', ...transition(screen, 'open'), at: session.now, how };
  session.seen = snapshot(session);
  run(session, field(screen.logic, 'on-open') ?? [], 'al abrir');
  return true;
}
/** The page's open or close animation, as the plugin plays it. */
export function transition(screen, phase) {
  const animation = field(screen?.logic, 'animation') ?? {}, raw = field(animation, phase);
  if (phase === 'close' && field(animation, 'close-reversed') === true && !raw) return { effect: 'fade', duration: 200, easing: 'ease-out', from: 'left', distance: 48 };
  const value = raw ?? {};
  const effect = field(value, 'effect') ?? (phase === 'open' ? 'fade' : raw ? 'fade' : 'none');
  return { effect, duration: Number(field(value, 'duration-ms') ?? 200), easing: field(value, 'easing') ?? 'ease-out', from: field(value, 'from') ?? 'left', distance: Number(field(value, 'distance') ?? 48) };
}

/** 1250 -> 1250, 25300 -> 25.3K: the plugin's {balance_short} (LivePlaceholders.compact). */
export function compact(plainText) {
  let value = Number(plainText);
  if (!/^-?\d+(\.\d+)?$/.test(String(plainText).trim()) || !Number.isFinite(value)) return String(plainText);
  const negative = value < 0, units = ['', 'K', 'M', 'B', 'T', 'Q'];
  value = Math.abs(value);
  let unit = 0;
  while ((value >= 10000 || unit > 0 && value >= 1000) && unit < units.length - 1) { value /= 1000; unit++; }
  const decimals = unit === 0 ? 0 : value >= 100 ? 0 : value >= 10 ? 1 : 2;
  const cut = Math.trunc(value * 10 ** decimals) / 10 ** decimals;
  return (negative ? '-' : '') + String(Number(cut.toFixed(decimals))) + units[unit];
}
/** A built-in {token} of the simulated player, or null (LivePlaceholders.builtIn). */
function builtIn(session, name) {
  if (name === 'balance') return String(session.balance);
  if (name === 'balance_short') return compact(String(session.balance));
  return Object.hasOwn(session.samples, name) ? String(session.samples[name]) : null;
}
function variable(session, name) {
  const bare = name.replace(/^[*$]/, '');
  if (!/^[*$]/.test(name) && session.locals.has(bare)) return session.locals.get(bare);
  return session.global.has(bare) ? session.global.get(bare) : null;
}
/**
 * The plugin's token pass: built-in values first ({player}, even inside ${player}), then {name} takes the scope
 * (function parameters, list rows) and variables, ${name} only variables; unknown tokens stay as written.
 */
function tokens(session, value, extra) {
  // Then PlaceholderAPI: the %placeholders% the variables panel simulates (unknown ones stay as written, as without PAPI).
  const placed = text(value).replace(/\{([a-z_]{1,24})\}/g, (token, name) => builtIn(session, name) ?? token)
    .replace(/%[A-Za-z0-9_:.-]{1,64}%/g, token => session.placeholders.get(token) ?? token);
  return placed.replace(/(\$?)\{([a-z_][a-z0-9_.]{0,47})\}/g, (token, dollar, name) => {
    let found = null;
    if (!dollar && Object.hasOwn(extra, name)) found = extra[name];
    if (found === null && !name.includes('.')) found = variable(session, name);
    return found ?? token;
  });
}
/** An operand of a calculated token (ActionRunner.name): built-in, scope, then variable; $name only the global variable. */
function operand(session, name, extra) {
  if (name.startsWith('%')) return session.placeholders.get(name) ?? null;
  if (name.startsWith('$')) return name.includes('.') ? null : session.global.get(name.slice(1)) ?? null;
  if (/^[a-z_]{1,24}$/.test(name)) { const value = builtIn(session, name); if (value !== null) return value; }
  if (Object.hasOwn(extra, name)) return extra[name];
  return name.includes('.') ? null : variable(session, name);
}
/** Text as the game shows it: calculated tokens ({coins * 2}), built-in values, scope and variables. */
export function resolve(session, value, extra = {}) {
  // A schema 1-8 page has no calculated tokens: "{1+1}" stays text, as in the plugin (Screen.legacyText).
  if ((page(session)?.version ?? 9) < 9) return tokens(session, text(value), extra);
  return renderExpressions(text(value), part => tokens(session, part, extra), name => operand(session, name, extra));
}
const number = value => { const n = Number(String(value).trim().replace(',', '.')); return String(value).trim() !== '' && Number.isFinite(n) ? n : null; };
const plain = value => { let v = text(value).replace(/(?:[&§][0-9a-fk-orx]|<[^<>]{1,32}>)/gi, '').trim(); if (v.length >= 2 && /^(["']).*\1$/.test(v)) v = v.slice(1, -1); return v; };
const truthy = value => !['', 'false', 'no', '0'].includes(value.toLowerCase());
// Conditions.java: placeholders, {tokens}, calculated {a * 2} and quoted text are opaque, so operators inside them do not split.
const OPAQUE = /%[^%\s]{1,128}%|\$?\{[^{}]{1,256}\}|"[^"]*"|'[^']*'/g;
const OPERATORS = ['==', '!=', '>=', '<=', '=', '>', '<'];
function split(masked, separator, from, to) {
  const parts = []; let start = from, at;
  while ((at = masked.indexOf(separator, start)) >= 0 && at < to) { parts.push([start, at]); start = at + separator.length; }
  parts.push([start, to]); return parts;
}
/** The plugin's condition language: ||, &&, == != >= <= = > <, numbers compared as numbers, text by equality. */
export function test(session, expression, extra = {}) {
  const template = text(expression), masked = template.replace(OPAQUE, token => 'x'.repeat(token.length));
  const resolved = token => plain(resolve(session, token.trim(), extra));
  return split(masked, '||', 0, masked.length).some(([from, to]) => split(masked, '&&', from, to).every(([start, end]) => {
    let at = -1, op = null;
    for (let i = start; i < end && at < 0; i++) for (const candidate of OPERATORS) if (i + candidate.length <= end && masked.startsWith(candidate, i)) { at = i; op = candidate; break; }
    if (at < 0) return truthy(resolved(template.slice(start, end)));
    const left = resolved(template.slice(start, at)), right = resolved(template.slice(at + op.length, end));
    const a = number(left), b = number(right);
    if (a !== null && b !== null) return { '==': a === b, '=': a === b, '!=': a !== b, '>=': a >= b, '<=': a <= b, '>': a > b, '<': a < b }[op];
    return op === '!=' ? left !== right : ['==', '='].includes(op) ? left === right : false;
  }));
}
/** Element ids of an action: "elements" list, "element" or a comma list in "value". */
function ids(action) {
  const list = field(action, 'elements'), one = field(action, 'element'), value = field(action, 'value');
  if (Array.isArray(list)) return list.map(text);
  if (one !== undefined) return [text(one)];
  return text(value).split(',').map(s => s.trim()).filter(Boolean);
}
/** Variables.apply: true when the value changed. Arithmetic needs numbers (a missing variable counts as 0); dividing by 0 changes nothing. */
function setVariable(session, name, operation, value) {
  const key = text(name).replace(/^[*$]/, ''), shared = /^[*$]/.test(text(name)) || session.global.has(key) && !session.locals.has(key);
  const store = shared ? session.global : session.locals, before = store.get(key);
  let after;
  if (operation === 'set') after = text(value).slice(0, 256);
  else {
    if (!['add', 'subtract', 'multiply', 'divide'].includes(operation)) { note(session, 'error', `Operación de variable desconocida: ${operation}.`); return false; }
    after = arithmetic(operation, before, value);
    if (after === null || after === undefined) { note(session, 'logic', `${key}: "${value}" no es un número, así que no cambia.`); return false; }
  }
  if (after === before) return false;
  store.set(key, after);
  return true;
}
/** Variables as the game shows them: locals over globals. */
function snapshot(session) { return new Map([...session.global, ...session.locals]); }
/**
 * ScreenRuntime.changes: on-change runs for each variable whose value differs from the last look, with {old} and {new};
 * a list never sets itself off again and chains stop after 8. Returns false when one closed the menu or left the page.
 */
function changes(session) {
  const now = snapshot(session), before = session.seen, page0 = session.pageId;
  session.seen = now;
  const handlers = field(page(session)?.logic, 'on-change') ?? {};
  for (const name of new Set([...before.keys(), ...now.keys()])) {
    const old = before.get(name), next = now.get(name);
    if (old === next) continue;
    const key = Object.keys(handlers).find(k => k.replace(/^[*$]/, '') === name);
    if (key === undefined || session.closed || session.changing.has(name)) continue;
    if (session.changeDepth >= MAX_CHANGE_DEPTH) { note(session, 'error', `on-change se cortó después de ${MAX_CHANGE_DEPTH} cambios seguidos (¿variables que se cambian entre sí?).`); continue; }
    session.changing.add(name); session.changeDepth++;
    try { run(session, handlers[key], `al cambiar ${name}`, 0, { steps: 0 }, { old: old ?? '', new: next ?? '' }); }
    finally { session.changeDepth--; session.changing.delete(name); }
    if (session.closed || session.pageId !== page0) return false;
  }
  return true;
}
/** ActionParser.millis: "2s", "500ms", "20t" or a plain number of ticks; 0 when there is no (valid) cooldown. */
export function cooldownMs(value) {
  if (value === undefined || value === null) return 0;
  const t = String(value).trim().toLowerCase();
  const scale = t.endsWith('ms') ? [t.slice(0, -2), 1] : t.endsWith('s') ? [t.slice(0, -1), 1000] : t.endsWith('t') ? [t.slice(0, -1), 50] : [t, 50];
  const n = Number(scale[0]);
  return scale[0].trim() !== '' && Number.isFinite(n) && n >= 0 ? Math.min(3600000, Math.round(n * scale[1])) : 0;
}
/** Toast seconds as the plugin reads them (Double.parseDouble): a decimal number kept to 1-60; anything else is 4. */
export function toastSeconds(given) {
  const raw = String(given ?? '').trim();
  if (!/^[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?[fFdD]?$/.test(raw)) return 4;
  const seconds = Number(raw.replace(/[fFdD]$/, ''));
  return Number.isFinite(seconds) ? Math.max(1, Math.min(60, seconds)) : 4;
}
/** UiAction.Input.accepts: max-length (50), allow-empty (yes) and the filter (any, digits, signed-digits, decimal). */
function inputAccepts(action, typed) {
  const max = Number(field(action, 'max-length') ?? 50), filter = text(field(action, 'filter') ?? 'any');
  if (typed.length > max || (typed === '' && field(action, 'allow-empty') === false)) return false;
  return filter === 'digits' ? /^[0-9]*$/.test(typed) : filter === 'signed-digits' ? /^-?[0-9]*$/.test(typed) : filter === 'decimal' ? /^-?[0-9]*(\.[0-9]*)?$/.test(typed) : true;
}
/** A positive amount of money in whole cents, cut down to two decimals (BigDecimal setScale(2, DOWN)), at most a trillion; else null. */
function cents(value) {
  const amount = decimal(value);
  if (!amount || amount.u <= 0n) return null;
  const tooMuch = amount.s >= 0 ? amount.u > 10n ** 12n * 10n ** BigInt(amount.s) : amount.u * 10n ** BigInt(-amount.s) > 10n ** 12n;
  if (tooMuch) return null;
  return amount.s >= 2 ? amount.u / 10n ** BigInt(amount.s - 2) : amount.u * 10n ** BigInt(2 - amount.s);
}
/** Seconds left, rounded up to a tenth: "2", "0.4" (ScreenRuntime.secondsLeft). */
export function secondsLeft(ms) {
  const tenths = Math.max(1, Math.ceil(ms / 100 - 1e-9));
  return tenths % 10 === 0 ? String(tenths / 10) : `${Math.floor(tenths / 10)}.${tenths % 10}`;
}
/** True when {@code e} still waits (its on-cooldown actions run instead); otherwise its wait starts now. */
function coolingDown(session, e, extra) {
  const ms = cooldownMs(field(e.logic, 'cooldown'));
  if (!ms) return false;
  const key = `${session.pageId}/${e.id}`, until = session.cooldowns.get(key);
  if (until !== undefined && session.now < until) {
    const tooSoon = field(e.logic, 'on-cooldown'), left = secondsLeft(until - session.now);
    if (Array.isArray(tooSoon) && tooSoon.length) run(session, tooSoon, 'clic antes de tiempo', 0, { steps: 0 }, { ...extra, cooldown: left });
    else note(session, 'logic', `${e.id} todavía espera ${left} s: el clic no hace nada.`);
    return true;
  }
  session.cooldowns.set(key, session.now + ms);
  return false;
}

/** Runs {@code actions}; returns false when a redirect, back or close ended the run. */
function run(session, actions, where, depth = 0, budget = { steps: 0 }, extra = {}) {
  if (!Array.isArray(actions)) actions = field(actions, 'actions') ?? [];
  if (depth > MAX_DEPTH) { note(session, 'error', 'Acciones anidadas en más de 8 niveles: el juego las rechaza.'); return false; }
  for (const action of actions) {
    if (++budget.steps > MAX_STEPS) { note(session, 'error', 'Demasiados pasos seguidos: el juego corta la ejecución.'); return false; }
    const type = text(field(action, 'type')).toLowerCase(), value = resolve(session, field(action, 'value'), extra);
    // While a page goes away (ScreenRuntime's ClosingEffects): navigation, server, a wait or a text input end the list there.
    if (session.closing && ['redirect', 'open', 'back', 'close', 'server', 'connect', 'delay', 'wait', 'input'].includes(type)) {
      if (['server', 'connect'].includes(type)) note(session, 'error', 'server: se ignora en las acciones al cerrar.');
      return false;
    }
    switch (type) {
      case 'message': note(session, 'chat', `Chat: ${plain(value)}`); emit(session, `message ${value}`); break;
      case 'actionbar': case 'action-bar': note(session, 'chat', `Sobre la barra: ${plain(value)}`); emit(session, `actionbar ${value}`); break;
      case 'command': case 'player': note(session, 'game', `Comando del jugador: /${value.replace(/^\//, '')}`); emit(session, `command ${value.replace(/^\//, '')}`); break;
      case 'console': note(session, 'game', `Comando de consola: ${value.replace(/^\//, '')}`); emit(session, `console ${value.replace(/^\//, '')}`); break;
      case 'sound': note(session, 'game', `Sonido: ${value}`); break;
      case 'teleport': note(session, 'game', `Teletransporte: ${value}`); break;
      case 'give': note(session, 'game', `Daría: ${value}`); break;
      case 'delay': case 'wait': note(session, 'game', `Esperaría ${value || resolve(session, field(action, 'ticks')) + ' ticks'} (Probar sigue sin esperar).`); break;
      case 'redirect': case 'open': goTo(session, resolve(session, field(action, 'screen') ?? field(action, 'page') ?? field(action, 'value'), extra), 'redirect'); return false;
      case 'back': {
        const previous = session.history.pop();
        if (!previous) { note(session, 'game', 'Volver: no hay pantalla anterior, así que se cerraría.'); close(session); return false; }
        goTo(session, previous, 'back'); return false;
      }
      case 'close': close(session); return false;
      case 'show': for (const id of ids(action)) session.view.hidden.delete(id); break;
      case 'hide': for (const id of ids(action)) session.view.hidden.add(id); break;
      case 'toggle': {
        const show = field(action, 'show'), hide = field(action, 'hide');
        if (Array.isArray(show) || Array.isArray(hide)) { for (const id of show ?? []) session.view.hidden.delete(id); for (const id of hide ?? []) session.view.hidden.add(id); }
        if (field(action, 'elements') !== undefined || field(action, 'element') !== undefined || value || !show && !hide)
          for (const id of ids(action)) session.view.hidden.has(id) ? session.view.hidden.delete(id) : session.view.hidden.add(id);
        break;
      }
      case 'visibility': {
        const mode = value.toLowerCase().trim(), list = ids({ ...action, value: '' });
        for (const id of list) if (['show', 'true', 'visible'].includes(mode)) session.view.hidden.delete(id); else if (['hide', 'false', 'hidden'].includes(mode)) session.view.hidden.add(id);
          else session.view.hidden.has(id) ? session.view.hidden.delete(id) : session.view.hidden.add(id);
        break;
      }
      case 'variable': case 'set': {
        if (setVariable(session, field(action, 'variable') ?? field(action, 'name'), text(field(action, 'operation') ?? 'set').toLowerCase(), value) && !changes(session)) return false;
        break;
      }
      case 'condition': case 'if': {
        const condition = text(field(action, 'value') ?? field(action, 'condition')), passed = test(session, condition, extra);
        note(session, 'logic', `Condición ${condition}: ${passed ? 'se cumple' : 'no se cumple'}.`);
        if (!run(session, field(action, passed ? 'then' : 'else') ?? [], where, depth + 1, budget, extra)) return false;
        break;
      }
      case 'permission': {
        const permission = text(field(action, 'permission') ?? field(action, 'value'));
        note(session, 'logic', `Permiso ${permission}: ${session.permissions ? 'lo tiene' : 'no lo tiene'} (elegido en Probar).`);
        if (!run(session, field(action, session.permissions ? 'then' : 'else') ?? [], where, depth + 1, budget, extra)) return false;
        break;
      }
      case 'function': case 'call': {
        const name = text(field(action, 'function') ?? field(action, 'name') ?? field(action, 'value')), functions = field(page(session)?.logic, 'functions') ?? {};
        const fn = Object.hasOwn(functions, name) ? functions[name] : null;
        if (!fn) { note(session, 'error', `La función ${name} no existe en esta página.`); break; }
        // As ActionRunner.call: defaults, then every argument given (declared or not), each kept to a variable's length.
        const params = field(fn, 'params') ?? {}, args = field(action, 'arguments') ?? field(action, 'args') ?? {}, scope = { ...extra };
        for (const [param, fallback] of Array.isArray(params) ? params.map(p => [p, '']) : Object.entries(params)) scope[param] = resolve(session, fallback, extra).slice(0, 256);
        for (const [argument, given] of Object.entries(args)) scope[argument] = resolve(session, given, extra).slice(0, 256);
        if (!run(session, field(fn, 'actions') ?? [], where, depth + 1, budget, scope)) return false;
        break;
      }
      case 'input': {
        const variable = text(field(action, 'variable') ?? field(action, 'id')), answer = session.ask?.({ title: resolve(session, field(action, 'title')), label: resolve(session, field(action, 'label')), value: resolve(session, field(action, 'default')) });
        if (answer === null || answer === undefined || !inputAccepts(action, answer)) {
          note(session, 'game', answer === null || answer === undefined ? `Diálogo de texto "${resolve(session, field(action, 'title'))}": cancelado.` : `Diálogo de texto: "${answer}" no cumple el formato pedido; cuenta como cancelado.`);
          if (!run(session, field(action, 'on-cancel') ?? [], where, depth + 1, budget, extra)) return false;
        } else {
          const changed = setVariable(session, variable, 'set', answer);
          note(session, 'game', `Diálogo de texto: ${variable} = ${answer}`);
          if (changed && !changes(session)) return false;
          if (!run(session, field(action, 'on-submit') ?? field(action, 'actions') ?? [], where, depth + 1, budget, extra)) return false;
        }
        break;
      }
      // 0.23: a yes/no question in the game's own dialog; without an answer (no dialog) it goes to "else".
      case 'confirm': {
        const title = resolve(session, field(action, 'title') ?? 'Are you sure?', extra), message = resolve(session, field(action, 'value') ?? field(action, 'message') ?? '', extra);
        const yes = resolve(session, field(action, 'yes') ?? 'Yes', extra), no = resolve(session, field(action, 'no') ?? 'No', extra);
        const answer = !session.closing && session.confirm?.({ title, message, yes, no }) === true;
        if (!session.closing) emit(session, `confirm ${message}`);
        note(session, 'game', `Pregunta "${title}"${message ? `: ${message}` : ''} → ${answer ? yes : no}.`);
        if (!run(session, field(action, answer ? 'then' : 'else') ?? [], where, depth + 1, budget, extra)) return false;
        break;
      }
      // 0.23: GIF and PNG-folder playback (the page shows it when the editor is connected to the server).
      case 'pause': case 'resume': case 'frame': {
        const id = text(type === 'frame' ? field(action, 'element') : field(action, 'element') ?? field(action, 'value')), target = element(session, id);
        if (!target || !field(target.logic, 'frames')) { note(session, 'error', `${type}: ${id || '(sin elemento)'} no es una animación PNG o GIF.`); break; }
        const state = { ...(session.view.playback.get(id) ?? { paused: false, seek: null, version: 0 }) };
        if (type === 'pause') { state.paused = true; state.seek = null; }
        else if (type === 'resume') { state.paused = false; state.seek = null; }
        else state.seek = Math.max(1, Math.min(100000, Math.round(number(resolve(session, field(action, 'frame') ?? field(action, 'value') ?? '1', extra)) ?? 1)));
        state.version++; session.view.playback.set(id, state);
        note(session, 'logic', type === 'pause' ? `${id}: pausada.` : type === 'resume' ? `${id}: sigue.` : `${id}: salta al cuadro ${state.seek}.`);
        break;
      }
      case 'offset': case 'move': {
        const dx = number(resolve(session, field(action, 'dx') ?? field(action, 'value'), extra)) ?? 0;
        const id = text(field(action, 'element')); if (dx === 0) session.view.offsets.delete(id); else session.view.offsets.set(id, Math.max(-512, Math.min(512, Math.round(dx)))); break;
      }
      case 'tint': case 'color': {
        const color = text(value || field(action, 'color') || 'none'), id = text(field(action, 'element'));
        if (color === 'none') session.view.tints.delete(id); else if (/^#[0-9a-f]{6}$/i.test(color)) session.view.tints.set(id, color.toLowerCase()); else note(session, 'error', `Color inválido ${color}: tiene que ser #RRGGBB.`);
        break;
      }
      case 'animation': case 'animate': { const id = text(field(action, 'element') ?? field(action, 'value')); session.view.plays.set(id, { at: session.now, mode: 'action' }); break; }
      // 0.23 money and proxy actions. Probar keeps a simulated balance (it starts at the {balance} sample, 1250).
      case 'charge': case 'withdraw': {
        const given = resolve(session, field(action, 'amount') ?? field(action, 'value'), extra), price = cents(given);
        if (price === null) { note(session, 'error', `Cobro inválido: "${given}" no es un monto positivo; no se cobra nada.`); if (!run(session, field(action, 'else') ?? [], where, depth + 1, budget, extra)) return false; break; }
        const balance = BigInt(Math.round(session.balance * 100)), enough = balance >= price;
        if (enough) session.balance = Number(balance - price) / 100;
        note(session, 'game', enough ? `Cobraría ${Number(price) / 100} con Vault (saldo simulado: ${session.balance}).` : `No alcanza: cuesta ${Number(price) / 100} y el saldo simulado es ${session.balance}; no se cobra nada.`);
        if (!run(session, field(action, enough ? 'then' : 'else') ?? [], where, depth + 1, budget, extra)) return false;
        break;
      }
      case 'pay': case 'deposit': {
        const paid = cents(resolve(session, field(action, 'amount') ?? field(action, 'value'), extra));
        if (paid === null) { note(session, 'error', 'Pago inválido: tiene que ser un monto positivo; no se paga nada.'); break; }
        session.balance = Number(BigInt(Math.round(session.balance * 100)) + paid) / 100;
        note(session, 'game', `Pagaría ${Number(paid) / 100} con Vault (saldo simulado: ${session.balance}).`);
        break;
      }
      case 'server': case 'connect': {
        const server = resolve(session, field(action, 'server') ?? field(action, 'value'), extra).trim();
        if (!/^[A-Za-z0-9_.-]{1,64}$/.test(server)) { note(session, 'error', `"${server}" no es un nombre de servidor (A-Z, 0-9, _ . -).`); break; }
        note(session, 'game', `Mandaría al jugador al servidor ${server} (necesita BungeeCord o Velocity).`);
        close(session); return false;
      }
      // 0.23: HUD pages added to or taken from what the player sees, and toasts (a HUD page for a few seconds, one after another).
      case 'hud': {
        const id = resolve(session, field(action, 'screen') ?? field(action, 'page') ?? field(action, 'value'), extra).trim();
        const add = text(field(action, 'mode') ?? 'add').toLowerCase() !== 'remove';
        note(session, 'game', add ? `Sumaría la página HUD ${id} a las que ve el jugador.` : `Quitaría la página HUD ${id} de las que ve el jugador.`);
        emit(session, `hud ${add ? 'add' : 'remove'} ${id}`); break;
      }
      case 'toast': {
        const id = resolve(session, field(action, 'screen') ?? field(action, 'page') ?? field(action, 'value'), extra).trim();
        const seconds = toastSeconds(resolve(session, field(action, 'seconds') ?? '4', extra));
        const values = Object.entries(field(action, 'values') ?? {}).map(([name, given]) => `${name} = ${resolve(session, given, extra)}`);
        const covered = page(session)?.presentation?.mode !== 'overlay' && !session.closing;
        note(session, 'game', `Mostraría el aviso ${id} durante ${seconds} s${values.length ? ` (${values.join(', ')})` : ''}${covered ? '; en el juego entra cuando se cierra el menú, que tapa el HUD' : ''}.`);
        emit(session, `toast ${id}`); break;
      }
      case 'scroll': {
        const id = text(field(action, 'element')), rows = Math.trunc(number(resolve(session, field(action, 'rows') ?? field(action, 'value'), extra)) ?? 1);
        const current = session.view.scroll.get(id) ?? 0; session.view.scroll.set(id, Math.max(0, field(action, 'absolute') === true ? rows : current + rows)); break;
      }
      default: note(session, 'error', `Tipo de acción desconocido: ${type || '(sin tipo)'}.`);
    }
  }
  return true;
}
/** The page goes away (closed or replaced): its on-close actions, with the closing rules (no dialogs, no navigation). */
function leave(session, where) {
  session.closing = true;
  try { run(session, field(page(session)?.logic, 'on-close') ?? [], where); }
  finally { session.closing = false; }
}
/**
 * Menu to menu, as the plugin opens a page from another: the old page's on-close runs, the history remembers it
 * (not for back, nor for the same page) and the new page replays its entrance. A page that does not exist changes nothing.
 */
// As LogicController.MAX_OPEN_CHAIN: pages opening pages from their on-open actions, 8 in a row at most (a loop).
const MAX_OPEN_CHAIN = 8;
function goTo(session, target, how) {
  if (!page(session, target)) { note(session, 'error', `No hay una página "${target}" en este proyecto: en el juego se abriría si existe en el servidor.`); return false; }
  const needed = field(page(session, target).logic, 'permission');
  if (needed && !session.permissions) { note(session, 'game', `No abriría ${target}: hace falta el permiso ${needed} (desmarcaste "Tener los permisos").`); return false; }
  if ((session.opening ?? 0) >= MAX_OPEN_CHAIN) { note(session, 'error', `Las páginas se abren unas a otras al abrirse: el juego corta la cadena después de ${MAX_OPEN_CHAIN} (¿una página que se abre a sí misma?).`); return false; }
  const from = session.pageId;
  emit(session, how === "back" ? "back" : `open ${target}`);
  leave(session, 'al dejar la página');
  if (how !== 'back' && target !== from) session.history.push(from);
  session.opening = (session.opening ?? 0) + 1;
  try { return open(session, target, how); } finally { session.opening--; }
}
// Menu to menu keeps the camera and replays the new page's entrance (the plugin's navigate); only closing plays the exit.
function close(session) {
  const screen = page(session);
  session.transition = { phase: 'close', ...transition(screen, 'close'), at: session.now, how: 'close' }; session.closed = true;
  emit(session, 'close');
  leave(session, 'al cerrar');
  note(session, 'game', 'El menú se cerró.');
}

/** True when the element is drawn now (not hidden, and its visible-if holds). */
export function shown(session, e) {
  if (session.view.hidden.has(e.id)) return false;
  const condition = field(e.logic, 'visible-if');
  return condition ? test(session, condition) : true;
}
/** Rows of a list element: its data (a variable, comma separated) after the filter and the scroll. */
export function listRows(session, e) {
  const spec = field(e.logic, 'list'); if (!spec) return null;
  const source = text(field(spec, 'source')), raw = session.locals.get(source) ?? session.global.get(source) ?? '';
  let items = raw.split(/[,\n]/).map(s => s.trim()).filter(Boolean);
  const filter = field(spec, 'filter'); if (filter) { const wanted = resolve(session, `{${filter}}`).toLowerCase(); if (wanted && wanted !== `{${filter}}`.toLowerCase()) items = items.filter(item => item.toLowerCase().includes(wanted)); }
  const rows = Number(field(spec, 'rows') ?? 5), first = Math.min(session.view.scroll.get(e.id) ?? 0, Math.max(0, items.length - rows));
  return { rows, spacing: Number(field(spec, 'spacing') ?? 16), items: items.slice(first, first + rows).map((item, i) => ({ item, index: first + i + 1 })) };
}

/** The element under the cursor changed: tooltips, on-leave / on-hover actions and hover keyframes, like the game. */
export function hover(session, id, now = session.now) {
  if (session.closed) return;
  session.now = now; const before = session.view.hover; if (before === id) return;
  session.view.hover = id;
  const leaving = before ? element(session, before) : null, entering = id ? element(session, id) : null;
  const pageBefore = session.pageId;
  if (leaving?.kind === 'button') {
    const tip = field(leaving.logic, 'tooltip'); if (tip) session.view.hidden.add(tip);
    run(session, field(leaving.logic, 'on-leave') ?? [], 'al salir el cursor');
  }
  // An on-leave that closed the menu or opened another page ends here: the new page starts with no hover.
  if (session.closed || session.pageId !== pageBefore) { session.view.hover = null; return; }
  if (entering?.kind === 'button') {
    const tip = field(entering.logic, 'tooltip'); if (tip) session.view.hidden.delete(tip);
    run(session, field(entering.logic, 'on-hover') ?? [], 'al entrar el cursor');
  }
}
const listOf = value => Array.isArray(value) ? value : Array.isArray(value?.actions) ? value.actions : [];
/**
 * A click on {@code id} ({@code row}: the list row, 1-based in the list's data), as ScreenRuntime.click: a click that
 * would run something first waits for the element's cooldown. Returns the page id shown afterwards.
 */
export function click(session, id, { right = false, row = null, now = session.now } = {}) {
  session.now = now;
  const e = element(session, id);
  if (session.closed || !e || e.kind !== 'button' || !shown(session, e)) return session.pageId;
  const extra = row ? { item: row.item, index: String(row.index) } : {};
  if (right) {
    const actions = listOf(field(e.logic, 'right-actions'));
    if (actions.length && !coolingDown(session, e, extra)) run(session, actions, 'clic derecho', 0, { steps: 0 }, extra);
    return session.pageId;
  }
  const select = listOf(field(field(e.logic, 'list'), 'on-select'));
  if (row && select.length) { if (!coolingDown(session, e, extra)) run(session, select, 'al elegir una fila', 0, { steps: 0 }, extra); return session.pageId; }
  const actions = listOf(field(e.logic, 'actions'));
  if (actions.length) { if (!coolingDown(session, e, extra)) run(session, actions, 'clic', 0, { steps: 0 }, extra); return session.pageId; }
  if (text(e.action) && coolingDown(session, e, extra)) return session.pageId;
  legacy(session, e);
  return session.pageId;
}
/**
 * GifFrames.decode's timeline: the GIF is sampled every {@code interval} ms (its shortest delay in whole ticks, or the
 * element's interval-ms; longer when it would need more than 64 frames), and {@code sources[k]} is the GIF frame
 * shown at sample k. Delays of 0 or 10 ms count as 100 ms, like browsers.
 */
export function gifTimeline(rawDelays, requestedMs = 0, maxFrames = 64) {
  const delays = rawDelays.map(ms => ms <= 10 ? 100 : ms), duration = delays.reduce((a, b) => a + b, 0);
  let interval = requestedMs || Math.max(50, Math.round((delays.length ? Math.min(...delays) : 100) / 50) * 50);
  if (delays.length > 1 && Math.ceil(duration / interval) > maxFrames) interval = Math.min(60000, Math.ceil(Math.ceil(duration / maxFrames) / 50) * 50);
  const count = delays.length === 1 ? 1 : Math.max(1, Math.min(maxFrames, Math.ceil(duration / interval)));
  const sources = [];
  for (let step = 0; step < count; step++) {
    let elapsed = 0, at = delays.length - 1;
    for (let i = 0; i < delays.length; i++) { elapsed += delays[i]; if (step * interval < elapsed) { at = i; break; } }
    sources.push(at);
  }
  return { interval, sources };
}
// ----- the variables panel (idea 21) -----
/** Every variable the run knows: locals, then the player's globals not hidden by a local, each with its value. */
export function variables(session) {
  const out = [...session.locals].map(([name, value]) => ({ name, value, global: false }));
  for (const [name, value] of session.global) if (!session.locals.has(name)) out.push({ name, value, global: true });
  return out.sort((a, b) => a.name.localeCompare(b.name));
}
/** Sets a variable from the panel (*name: the global one) as an action would: lists, conditions and on-change follow. */
export function setVariableFromPanel(session, name, value) {
  const key = text(name).trim();
  if (!/^[*$]?[a-z_][a-z0-9_]{0,23}$/.test(key)) throw Error('Variable names use a-z, 0-9 and _, up to 24 characters (*name: global).');
  if (session.closed) return false;
  const changed = setVariable(session, key, 'set', text(value));
  if (changed) { note(session, 'logic', `Variable ${key.replace(/^[*$]/, '')} = ${text(value)} (desde el panel).`); changes(session); }
  return changed;
}
/** A simulated player value ({health}, {balance}...): text shows it at once; {balance} is also what charge and pay use. */
export function setSample(session, name, value) {
  if (name === 'balance') {
    const amount = Number(String(value).trim().replace(',', '.'));
    if (!Number.isFinite(amount)) throw Error('The balance must be a number.');
    session.balance = Math.round(amount * 100) / 100;
  } else if (Object.hasOwn(SAMPLES, name)) session.samples[name] = text(value);
  else throw Error(`Unknown player value ${name}.`);
}
/** Simulated PlaceholderAPI values: "%name% = value" lines (blank lines and lines without = are ignored). */
export function setPlaceholders(session, lines) {
  session.placeholders = new Map();
  for (const line of text(lines).split('\n')) {
    const at = line.indexOf('=');
    if (at < 0) continue;
    const key = line.slice(0, at).trim(), value = line.slice(at + 1).trim();
    if (/^%[A-Za-z0-9_:.-]{1,64}%$/.test(key)) session.placeholders.set(key, value);
  }
  return session.placeholders.size;
}
/** The page's timers: { everyMs, actions } (ActionParser: "5s", "500ms", "20t" or ticks; 0.25 s to an hour). */
export function timers(session) {
  return (field(page(session)?.logic, 'timers') ?? []).map(timer => ({ everyMs: cooldownMs(field(timer, 'every')), actions: listOf(field(timer, 'actions')) }))
    .filter(timer => timer.everyMs >= 250 && timer.everyMs <= 3600000 && timer.actions.length);
}
/** One run of timer {@code index} of the page (the page calls it every period while it stays open). */
export function tick(session, index, now = session.now) {
  if (session.closed) return;
  session.now = now;
  const timer = timers(session)[index];
  if (timer) run(session, timer.actions, `cada ${timer.everyMs / 1000} s`, 0, { steps: 0 });
}
/** The simulated player takes {@code amount} damage: on-damage ({damage}, {cause}), then close-on-damage closes. */
export function damage(session, amount = 4, cause = 'entity_attack') {
  if (session.closed) return;
  const logic = page(session)?.logic, before = session.pageId;
  note(session, 'game', `El jugador recibe ${amount} de daño (${cause}).`);
  // {damage} as the plugin writes it: two decimals at most.
  run(session, listOf(field(logic, 'on-damage')), 'al recibir daño', 0, { steps: 0 }, { damage: roundText(amount, 2), cause });
  if (!session.closed && session.pageId === before && field(field(logic, 'behavior'), 'close-on-damage') === true) close(session);
}
/** The simulated player changes world: on-world-change ({world}, {from}); {world} shows the new one afterwards. */
export function changeWorld(session, world) {
  if (session.closed) return;
  const from = session.samples.world;
  session.samples.world = world;
  note(session, 'game', `El jugador pasa del mundo ${from} a ${world}.`);
  run(session, listOf(field(page(session)?.logic, 'on-world-change')), 'al cambiar de mundo', 0, { steps: 0 }, { world, from });
}
/** Schema 1-8 single actions. */
function legacy(session, e) {
  const action = text(e.action);
  if (action) emit(session, `legacy ${action}`);
  if (action === 'close') close(session);
  else if (action.startsWith('open:')) goTo(session, action.slice(5), 'redirect');
  else if (action.startsWith('message:')) note(session, 'chat', `Chat: ${resolve(session, action.slice(8))}`);
  else if (action.startsWith('command:')) note(session, 'game', `Comando del jugador: ${resolve(session, action.slice(8))}`);
  else if (action === 'conditional-message') {
    const c = e.messageCondition ?? {};
    note(session, 'chat', `Chat (permiso ${c.permission}: ${session.permissions ? 'lo tiene' : 'no lo tiene'}): ${session.permissions ? c.ifTrue : c.ifFalse}`);
  } else if (!action) note(session, 'game', `${e.id} no tiene acción.`);
}
/** A key pressed while the menu is open (W A S D SPACE Q F L 1-9, with SHIFT_). */
export function key(session, name) {
  if (session.closed) return;
  const binds = field(page(session)?.logic, 'keybinds') ?? [];
  const list = Array.isArray(binds) ? binds : Object.entries(binds).map(([k, actions]) => ({ key: k, actions }));
  const bind = list.find(b => text(b.key).toUpperCase() === name.toUpperCase());
  if (bind) run(session, field(bind, 'actions') ?? [], `tecla ${name}`);
  else if (name === 'SNEAK' && field(field(page(session)?.logic, 'behavior'), 'close-on-sneak') !== false) close(session);
  else if (field(field(page(session)?.logic, 'behavior'), 'keyboard-navigation') === true) navigate(session, name);
}
const DIRECTIONS = { W: [0, 1], S: [0, -1], A: [-1, 0], D: [1, 0] };
/** behavior.keyboard-navigation, as the plugin does it (KeyboardFocus.java): W A S D focus the nearest button that way, Space clicks. */
function navigate(session, name) {
  if (name === 'SPACE') { if (session.view.hover) click(session, session.view.hover); return; }
  const direction = DIRECTIONS[name]; if (!direction) return;
  const buttons = (page(session)?.elements ?? []).filter(e => e.kind === 'button' && shown(session, e) && !field(e.logic, 'list'));
  const next = nextFocus(buttons, buttons.find(e => e.id === session.view.hover) ?? null, direction);
  if (next) hover(session, next.id);
}
/** Same rules as KeyboardFocus.java: boxes in logical pixels, the centre beyond the current box, the gap that way at least the gap across. */
export function nextFocus(buttons, current, [dx, dy], cursor = current ? { x: current.x, y: current.y } : { x: 0, y: 0 }) {
  const box = e => [(e.x - e.width / 2) * 128, (e.y - e.height / 2) * 72, (e.x + e.width / 2) * 128, (e.y + e.height / 2) * 72];
  const cx = cursor.x * 128, cy = cursor.y * 72, from = current ? box(current) : [cx, cy, cx, cy];
  let best = null, bestScore = Infinity, bestDistance = Infinity, nearest = null, nearestDistance = Infinity;
  for (const e of buttons) {
    if (e === current) continue;
    const to = box(e), distance = Math.hypot(e.x * 128 - cx, e.y * 72 - cy);
    if (distance < nearestDistance) { nearest = e; nearestDistance = distance; }
    const beyond = ((to[0] + to[2]) - (from[0] + from[2])) / 2 * dx + ((to[1] + to[3]) - (from[1] + from[3])) / 2 * dy;
    const along = Math.max(0, dx > 0 ? to[0] - from[2] : dx < 0 ? from[0] - to[2] : dy > 0 ? to[1] - from[3] : from[1] - to[3]);
    const across = dx !== 0 ? Math.max(0, to[1] - from[3], from[1] - to[3]) : Math.max(0, to[0] - from[2], from[0] - to[2]);
    if (beyond <= 0 || along < across) continue;
    const score = along + 2 * across;
    if (score < bestScore - 1e-9 || Math.abs(score - bestScore) <= 1e-9 && distance < bestDistance) { best = e; bestScore = score; bestDistance = distance; }
  }
  return best ?? (current ? null : nearest);
}
