// "Revisar" (idea 11): what would look or behave wrong in the game, found before publishing. Text is measured with the
// plugin's own rules (HudRaster/TextMask): a monospaced 5x7 font with a 6-pixel advance per character times the text
// size, character wrapping at the wrap width, 8-pixel lines, and the element's box as the clip. Nothing here blocks a
// publication: the server validates the format itself; these are warnings for the author.

import { hasExpression } from './expressions.mjs';
import { plain } from './richtext.mjs';
import { FULL, HALF, UNBOUNDED, allowed, elementMaxChars, lines as fitLines, longest, sizeFor, widest } from './textfit.mjs';

/** The canvas rectangle menus keep text and buttons in (logical pixels; ViewportFit.SAFE_*). */
const SAFE = { left: 12, right: 244, top: 8, bottom: 133 };
const kebab = key => key.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase().replaceAll('_', '-');
function field(object, key) {
  if (!object || typeof object !== 'object') return undefined;
  const found = Object.keys(object).find(k => kebab(k) === key);
  return found === undefined ? undefined : object[found];
}

/** HudRaster.textLines: explicit lines, each cut every (wrapWidth + scale) / (6 x scale) characters when wrapping. */
export function textLines(text, wrapWidth = 0, scale = 1) {
  const columns = wrapWidth === 0 ? Infinity : Math.max(1, Math.floor((wrapWidth + scale) / (6 * scale)));
  const lines = [];
  for (const line of String(text).split('\n')) {
    if (!line.length) { lines.push(''); continue; }
    for (let start = 0; start < line.length; start += Math.min(columns, line.length)) lines.push(line.slice(start, start + columns));
  }
  return lines;
}
/** The text block of an element in logical pixels (without outline or shadow, which may spill past the box). */
export function textBlock(text, style = {}) {
  const scale = style.textScale ?? 1, lines = textLines(text, style.wrapWidth ?? 0, scale);
  const width = Math.max(0, ...lines.map(line => Math.max(0, (line.length * 6 - 1) * scale)));
  return { width, height: lines.length * 8 * scale - scale, lines: lines.length };
}
/** The element's box in logical canvas pixels. */
export function box(e) {
  const width = Math.round(e.width * 128), height = Math.round(e.height * 72), cx = Math.round((e.x + 1) * 128), cy = Math.round((1 - e.y) * 72);
  return { width, height, left: cx - Math.floor(width / 2), top: cy - Math.floor(height / 2), right: cx - Math.floor(width / 2) + width, bottom: cy - Math.floor(height / 2) + height };
}
/** Text that changes in the game ({player}, %placeholders%, bars, calculated {a * 2}) cannot be measured here. */
const live = text => /\{[a-z_*$][a-z0-9_.:\/]*\}|%[A-Za-z0-9_:.,=-]+%/.test(text) || hasExpression(text);

/** Every action list of a logic object, nested branches included. */
export function actionLists(logic) {
  const out = [];
  const visit = list => {
    if (!Array.isArray(list)) list = field(list, 'actions');
    if (!Array.isArray(list)) return;
    out.push(list);
    for (const action of list) for (const branch of ['then', 'else', 'on-submit', 'on-cancel']) visit(field(action, branch));
  };
  for (const key of ['actions', 'right-actions', 'on-hover', 'on-leave', 'on-drop', 'on-open', 'on-close', 'on-cooldown', 'on-damage', 'on-world-change']) visit(field(logic, key));
  for (const timer of [].concat(field(logic, 'timers') ?? [])) visit(field(timer, 'actions'));
  for (const actions of Object.values(field(logic, 'on-change') ?? {})) visit(actions);
  visit(field(field(logic, 'list'), 'on-select'));
  for (const bind of [].concat(field(logic, 'keybinds') ?? []).flatMap(b => Array.isArray(b) ? b : [b])) visit(field(bind, 'actions'));
  if (field(logic, 'keybinds') && !Array.isArray(field(logic, 'keybinds'))) for (const actions of Object.values(field(logic, 'keybinds'))) visit(actions);
  for (const fn of Object.values(field(logic, 'functions') ?? {})) visit(field(fn, 'actions'));
  return out;
}
const actionsOf = logic => actionLists(logic).flat();
/** Pages an action list or a legacy action opens. */
function targets(screen) {
  const out = new Set();
  const lists = [...actionLists(screen.logic), ...screen.elements.flatMap(e => actionLists(e.logic))];
  for (const action of lists.flat()) {
    const type = String(field(action, 'type') ?? '').toLowerCase();
    if (type === 'redirect' || type === 'open') out.add(String(field(action, 'screen') ?? field(action, 'page') ?? field(action, 'value') ?? ''));
  }
  for (const e of screen.elements) if (typeof e.action === 'string' && e.action.startsWith('open:')) out.add(e.action.slice(5));
  return out;
}
/** HUD pages the page's hud (add) and toast actions show: [{ type, target }], once each. */
function hudTargets(screen) {
  const out = new Map();
  for (const action of [...actionsOf(screen.logic), ...screen.elements.flatMap(e => actionsOf(e.logic))]) {
    const type = String(field(action, 'type') ?? '').toLowerCase();
    if (type !== 'toast' && (type !== 'hud' || String(field(action, 'mode') ?? 'add').toLowerCase() === 'remove')) continue;
    const target = String(field(action, 'screen') ?? field(action, 'page') ?? field(action, 'value') ?? '').trim();
    out.set(`${type}:${target}`, { type, target });
  }
  return [...out.values()];
}
/** Element ids an action list shows or toggles. */
function shownIds(screen) {
  const out = new Set();
  for (const action of [...actionsOf(screen.logic), ...screen.elements.flatMap(e => actionsOf(e.logic))]) {
    const type = String(field(action, 'type') ?? '').toLowerCase();
    if (!['show', 'toggle', 'visibility'].includes(type)) continue;
    const list = field(action, 'elements'), one = field(action, 'element'), show = field(action, 'show');
    for (const id of [...(Array.isArray(list) ? list : []), ...(one ? [one] : []), ...(Array.isArray(show) ? show : [])]) out.add(String(id));
    if (!list && !one && typeof field(action, 'value') === 'string' && type !== 'visibility') for (const id of field(action, 'value').split(',')) out.add(id.trim());
  }
  return out;
}

/**
 * Findings for one page: { level: 'warning' | 'info', page, element, message }. {@code pages}: ids of every page of the
 * project; {@code frames}: when known, the animation folders the server has (a Set), else null; {@code icons}: when known,
 * the item icon table ({ item: { block, tinted } }), else null.
 */
export function reviewScreen(screen, { pages = [screen.id], frames = null, icons = null, overlays = null } = {}) {
  const found = [], add = (level, element, message) => found.push({ level, page: screen.id, element, message });
  const overlay = screen.presentation?.mode === 'overlay', shown = shownIds(screen);
  const tooltips = new Set(screen.elements.map(e => field(e.logic, 'tooltip')).filter(Boolean));
  for (const e of screen.elements) {
    const b = box(e), list = field(e.logic, 'list');
    if (e.kind !== 'decoration' && e.text && !live(e.text) && !list) {
      const drawn = screen.version >= 9 ? plain(e.text) : e.text, text = textBlock(drawn, e.style);
      // Schema 9: a line wider than its box is drawn smaller (3/4, 1/2), past half cut with "…" (textfit.mjs).
      const fitting = allowed(screen.version) ? fitLines(e, drawn) : [], longestLine = widest(fitting);
      if (fitting.length && longestLine > elementMaxChars(e, FULL)) {
        const size = sizeFor(e, longestLine), height = Math.max(0, fitting.length * 8 * (e.style.textScale ?? 1) - (e.style.textScale ?? 1)) * size / 4;
        if (longestLine > elementMaxChars(e, size))
          add('warning', e.id, `El texto de ${e.id} no entra en su caja ni a la mitad de su tamaño: en el juego se corta con "…". Agrandá la caja o acortá el texto.`);
        else add('info', e.id, `El texto de ${e.id} no entra en su caja a su tamaño: en el juego se ve a ${size === 3 ? '3/4' : '1/2'} de su tamaño.`);
        if (height > b.height) add('warning', e.id, `El texto de ${e.id} es más alto que su caja (${height} px, la caja mide ${b.height}): en el juego se cortan arriba y abajo.`);
      } else if (text.width > b.width || text.height > b.height)
        add('warning', e.id, `El texto de ${e.id} no entra en su caja: ocupa ${text.width} × ${text.height} px y la caja mide ${b.width} × ${b.height}. En el juego se corta; agrandá la caja, achicá el texto o usá "Ancho de ajuste".`);
    }
    // Live text: a long name (16 letters) or the longest value a built-in token can have; unknown values cannot be judged.
    if (e.kind !== 'decoration' && e.text && live(e.text) && !list && !/\{bar:/.test(e.text)) {
      const most = longest(e.text), size = allowed(screen.version) ? HALF : FULL;
      if (most < UNBOUNDED && most > elementMaxChars(e, size))
        add('warning', e.id, `${e.id} puede mostrar hasta ${most} letras (por ejemplo, un nombre de 16) y su caja tiene lugar para ${elementMaxChars(e, size)}${size === HALF ? ' aun a la mitad de su tamaño' : ''}: en el juego se corta. Agrandá la caja.`);
    }
    if (e.kind === 'button' && !overlay) {
      const clicks = Boolean(e.action) || actionsOf({ actions: field(e.logic, 'actions'), 'right-actions': field(e.logic, 'right-actions') }).length > 0
        || Boolean(field(list, 'on-select')) || field(e.logic, 'draggable') === true;
      if (!clicks) add('warning', e.id, `El botón ${e.id} no hace nada al hacer clic. Poné una acción o cambialo a texto.`);
    }
    const outside = (b.left < SAFE.left || b.right > SAFE.right || b.top < SAFE.top || b.bottom > SAFE.bottom) && !(e.width >= 1.9 && e.height >= 1.9);
    if (e.kind !== 'decoration' && outside)
      add('info', e.id, `${e.id} sale de la zona segura: en algunas pantallas puede quedar cortado. Probalo con "Ver como".`);
    // A decoration with a visible frame (border or 9-slice) loses part of it past the safe area; plain art may bleed.
    else if (outside && ((e.border && (e.style.outlineWidth ?? 1) > 0) || (e.style.slice ?? 0) > 0))
      add('info', e.id, `El marco de ${e.id} sale de la zona segura: en algunas pantallas se corta su borde. Probalo con "Ver como".`);
    if (field(e.logic, 'hidden') === true && !shown.has(e.id) && !tooltips.has(e.id))
      add('warning', e.id, `${e.id} empieza oculto y ninguna acción de esta página lo muestra.`);
    const folder = field(field(e.logic, 'frames'), 'folder');
    if (folder && frames && !frames.has(String(folder)))
          add('warning', e.id, `La animación "${folder}" de ${e.id} no está en plugins/BigCasares/tideframe/engine/images del servidor: en el juego se verá su imagen fija.`);
    if (e.item && icons) {
      const icon = icons[e.item];
      if (!icon) add('warning', e.id, `El juego no tiene un ítem "${e.item}": el ícono de ${e.id} no se va a ver.`);
      else if (icon.tinted) add('info', e.id, `El ícono de ${e.id} (${e.item}) se ve sin el color que le pone el juego.`);
      else if (icon.block) add('info', e.id, `El ícono de ${e.id} (${e.item}) es un bloque: en el menú se ve una cara plana, no el bloque en 3D.`);
    }
    if (typeof e.action === 'string' && e.action.startsWith('open:') && !pages.includes(e.action.slice(5)))
      add('info', e.id, `${e.id} abre la página "${e.action.slice(5)}", que no está en este proyecto (puede existir en el servidor).`);
  }
  for (const target of targets(screen)) if (target && !target.includes('{') && !pages.includes(target) && !screen.elements.some(e => e.action === `open:${target}`))
    add('info', null, `Una acción abre la página "${target}", que no está en este proyecto (puede existir en el servidor).`);
  // hud and toast show HUD pages only ({@code overlays}: the project's HUD page ids, when known).
  for (const { type, target } of hudTargets(screen)) {
    if (!target || target.includes('{')) continue;
    const what = type === 'toast' ? 'el aviso' : 'la página HUD';
    if (!pages.includes(target)) add('info', null, `Una acción muestra ${what} "${target}", que no está en este proyecto (puede existir en el servidor).`);
    else if (overlays && !overlays.has(target)) add('warning', null, `Una acción muestra ${what} "${target}", pero esa página es un menú: el juego no la muestra. Cambiala a "HUD - pasivo".`);
  }
  return found;
}

/** Findings for every page, plus pages nothing opens (no link, command or open-on-join). */
export function reviewProject(project, { frames = null, icons = null } = {}) {
  const screens = project.pages.map(entry => entry.screen), pages = screens.map(s => s.id), found = [];
  const overlays = new Set(screens.filter(s => s.presentation?.mode === 'overlay').map(s => s.id));
  for (const screen of screens) found.push(...reviewScreen(screen, { pages, frames, icons, overlays }));
  const linked = new Set(screens.flatMap(s => [...targets(s), ...hudTargets(s).map(h => h.target)]));
  for (const screen of screens.slice(1)) {
    const reachable = linked.has(screen.id) || field(screen.logic, 'command') || field(screen.logic, 'open-on-join') === true;
    if (!reachable) found.push({ level: 'info', page: screen.id, element: null, message: `La página ${screen.id} no se abre desde ninguna otra y no tiene comando: solo con /tideframe ${screen.id}.` });
  }
  return found;
}
