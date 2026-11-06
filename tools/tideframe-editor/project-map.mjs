// "Mapa del proyecto" (idea 31): every page as a box and an arrow for each button (or page action) that opens another
// page, laid out in columns from the pages a player can open directly. Pages nobody reaches and links to pages that do
// not exist are marked. Pure: builds the graph and the SVG text; the page shows it (app.mjs).
import { actionLists } from './checker.mjs';

const kebab = key => key.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase().replaceAll('_', '-');
function field(object, key) {
  if (!object || typeof object !== 'object') return undefined;
  const found = Object.keys(object).find(k => kebab(k) === key);
  return found === undefined ? undefined : object[found];
}
const escape = text => String(text).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

/** Pages, links ({ from, to, via: [element ids or "página"] }) and how each page is entered. */
export function projectGraph(project) {
  const screens = project.pages.map(entry => entry.screen), ids = screens.map(s => s.id), links = new Map();
  const link = (from, to, via) => {
    if (!to) return;
    const key = `${from}\u0000${to}`;
    if (!links.has(key)) links.set(key, { from, to, via: [] });
    if (!links.get(key).via.includes(via)) links.get(key).via.push(via);
  };
  const opens = list => list.filter(action => ['redirect', 'open'].includes(String(field(action, 'type') ?? '').toLowerCase()))
    .map(action => String(field(action, 'screen') ?? field(action, 'page') ?? field(action, 'value') ?? '').trim());
  for (const screen of screens) {
    for (const to of actionLists(screen.logic).flatMap(opens)) link(screen.id, to, 'página');
    for (const e of screen.elements) {
      for (const to of actionLists(e.logic).flatMap(opens)) link(screen.id, to, e.id);
      if (typeof e.action === 'string' && e.action.startsWith('open:')) link(screen.id, e.action.slice(5).trim(), e.id);
    }
  }
  // A player opens these directly: the first page, a page with its own command or opened on join, and HUD pages
  // (shown by /tideframe, the API or the world's HUD list, never from a button).
  const entries = screens.filter((s, index) => index === 0 || field(s.logic, 'command') || field(s.logic, 'open-on-join') === true || s.presentation?.mode === 'overlay').map(s => s.id);
  const reached = new Set(entries), queue = [...entries];
  while (queue.length) { const at = queue.shift(); for (const l of links.values()) if (l.from === at && ids.includes(l.to) && !reached.has(l.to)) { reached.add(l.to); queue.push(l.to); } }
  const pages = screens.map(s => ({ id: s.id, title: s.title, entry: entries.includes(s.id), command: field(s.logic, 'command') ?? null,
    openOnJoin: field(s.logic, 'open-on-join') === true, overlay: s.presentation?.mode === 'overlay', reached: reached.has(s.id) }));
  const missing = [...new Set([...links.values()].map(l => l.to).filter(to => !ids.includes(to)))];
  return { pages, links: [...links.values()], missing };
}

const BOX = { width: 176, height: 58 }, GAP = { x: 96, y: 40 }, MARGIN = { x: 60, top: 44, bottom: 28 };
/** Column (depth from the entries) and row of every page and missing target: entries first, unreached pages last. */
export function layout(graph) {
  const depth = new Map(), order = [];
  const queue = graph.pages.filter(p => p.entry).map(p => p.id);
  for (const id of queue) depth.set(id, 0);
  while (queue.length) {
    const at = queue.shift(); order.push(at);
    for (const l of graph.links) if (l.from === at && !depth.has(l.to) && graph.pages.some(p => p.id === l.to)) { depth.set(l.to, depth.get(at) + 1); queue.push(l.to); }
  }
  const deepest = Math.max(0, ...depth.values());
  for (const p of graph.pages) if (!depth.has(p.id)) { depth.set(p.id, deepest + 1); order.push(p.id); }
  // A missing page sits one column after the first page that links to it.
  for (const id of graph.missing) {
    const from = graph.links.find(l => l.to === id).from;
    depth.set(id, depth.get(from) + 1); order.push(id);
  }
  const rows = new Map(), place = new Map();
  for (const id of order) { const column = depth.get(id), row = rows.get(column) ?? 0; rows.set(column, row + 1); place.set(id, { column, row }); }
  const columns = Math.max(...[...place.values()].map(p => p.column)) + 1, tallest = Math.max(...rows.values());
  const at = id => { const p = place.get(id); return { x: MARGIN.x + p.column * (BOX.width + GAP.x), y: MARGIN.top + p.row * (BOX.height + GAP.y), ...p }; };
  return { at, width: MARGIN.x * 2 + columns * BOX.width + (columns - 1) * GAP.x, height: MARGIN.top + MARGIN.bottom + tallest * BOX.height + (tallest - 1) * GAP.y };
}

/** An axis-aligned polyline as an SVG path with rounded corners. */
function rounded(points) {
  let d = `M ${points[0][0].toFixed(1)} ${points[0][1].toFixed(1)}`;
  for (let i = 1; i < points.length - 1; i++) {
    const [px, py] = points[i - 1], [x, y] = points[i], [nx, ny] = points[i + 1];
    const r = Math.min(8, Math.hypot(x - px, y - py) / 2, Math.hypot(nx - x, ny - y) / 2);
    const ax = x - Math.sign(x - px) * r, ay = y - Math.sign(y - py) * r, bx = x + Math.sign(nx - x) * r, by = y + Math.sign(ny - y) * r;
    d += ` L ${ax.toFixed(1)} ${ay.toFixed(1)} Q ${x.toFixed(1)} ${y.toFixed(1)} ${bx.toFixed(1)} ${by.toFixed(1)}`;
  }
  const [lx, ly] = points.at(-1);
  return `${d} L ${lx.toFixed(1)} ${ly.toFixed(1)}`;
}

/**
 * The map as SVG text: boxes that can be clicked (data-page) and labelled arrows. A link to the next column is a
 * curve across the gap between them; any other link runs along the gaps between columns and rows, so no arrow ever
 * crosses a box, and parallel ones keep apart.
 */
export function mapSvg(project) {
  const graph = projectGraph(project), { at, width, height } = layout(graph);
  const shorten = (text, max) => text.length > max ? `${text.slice(0, max - 1)}…` : text;
  const arrows = [], labels = [], boxes = [], placed = [];
  const lanes = new Map(), lane = key => { const n = lanes.get(key) ?? 0; lanes.set(key, n + 1); return n; };
  const label = (text, x, y, step = 16) => {
    const w = text.length * 6.4 + 10;
    let top = y - 11;
    // Labels never cover each other: the next free spot (below, or above for labels on a gap between rows; below
    // again when above would leave the picture).
    const taken = at => placed.some(p => x < p.x + p.w && x + w > p.x && at < p.y + 15 && at + 15 > p.y);
    for (let tries = 0; tries < 6 && taken(top); tries++) top += step;
    if (top < 2) { top = y - 11; for (let tries = 0; tries < 6 && taken(top); tries++) top += Math.abs(step); }
    placed.push({ x, y: top, w });
    labels.push(`<g class="map-label"><rect x="${x.toFixed(1)}" y="${top.toFixed(1)}" width="${w.toFixed(1)}" height="15" rx="3"/><text x="${(x + 5).toFixed(1)}" y="${(top + 11).toFixed(1)}">${escape(text)}</text></g>`);
  };
  const outgoing = new Map(), incoming = new Map();
  for (const l of graph.links) { outgoing.set(l.from, (outgoing.get(l.from) ?? 0) + 1); incoming.set(l.to, (incoming.get(l.to) ?? 0) + 1); }
  const exits = new Map(), entries = new Map();
  // Several links from (or into) one box leave (or arrive) at different heights of its side.
  const port = (map, total, id) => { const n = map.get(id) ?? 0; map.set(id, n + 1); const count = total.get(id) ?? 1; return BOX.height / 2 + (n - (count - 1) / 2) * Math.min(12, (BOX.height - 16) / Math.max(1, count - 1)); };
  for (const l of graph.links) {
    const a = at(l.from), b = at(l.to), missing = graph.missing.includes(l.to), text = shorten(l.via.join(', '), 24);
    const y1 = a.y + port(exits, outgoing, l.from), y2 = b.y + port(entries, incoming, l.to);
    let d, lx, ly, step = 16;
    if (l.from === l.to) {
      // A page that reopens itself: a small loop on its right.
      const x = a.x + BOX.width;
      d = `M ${x} ${y1 - 8} C ${x + 44} ${y1 - 34}, ${x + 44} ${y1 + 30}, ${x} ${y1 + 8}`; lx = x + 34; ly = y1 + 4;
    } else if (b.column === a.column + 1) {
      const x1 = a.x + BOX.width, x2 = b.x - 2, bend = (x2 - x1) / 2;
      d = `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`; lx = x1 + 8; ly = y1 - 6;
    } else {
      // Out of the source's side into a gap between columns, along the gap between rows just above the target, and in
      // from the target's left. Back links leave from the left, links that skip columns from the right.
      const back = b.column <= a.column, n = lane(back ? `back${b.row}` : `skip${b.row}`);
      const outX = back ? a.x - 16 - n * 6 : a.x + BOX.width + 16 + n * 6;
      const inX = b.x - (back ? 16 : 34) - n * 6, channel = b.y - GAP.y / 2 - n * 6;
      const startX = back ? a.x : a.x + BOX.width;
      d = rounded([[startX, y1], [outX, y1], [outX, channel], [inX, channel], [inX, y2], [b.x - 2, y2]]);
      lx = Math.min(outX, inX) + 12; ly = channel - 4; step = -16;
    }
    arrows.push(`<path class="map-link${missing ? ' map-link-missing' : ''}" d="${d}" marker-end="url(#map-arrow${missing ? '-missing' : ''})"/>`);
    label(text, lx, ly, step);
  }
  for (const p of graph.pages) {
    const { x, y } = at(p.id), kind = p.overlay ? 'HUD' : 'menú';
    const note = !p.reached ? 'no se llega' : p.command ? `/${p.command}` : p.openOnJoin ? 'al entrar' : p.overlay ? 'HUD' : p.entry ? 'primera página' : kind;
    boxes.push(`<g class="map-node${p.reached ? '' : ' map-node-lost'}${p.entry ? ' map-node-entry' : ''}" data-page="${escape(p.id)}" role="button" tabindex="0" aria-label="${escape(`Ir a ${p.id}: ${p.title}`)}">`
      + `<rect x="${x}" y="${y}" width="${BOX.width}" height="${BOX.height}" rx="7"/>`
      + `<text class="map-id" x="${x + 12}" y="${y + 22}">${escape(shorten(p.id, 22))}</text>`
      + `<text class="map-note" x="${x + 12}" y="${y + 42}">${escape(shorten(`${p.title} · ${note}`, 26))}</text></g>`);
  }
  for (const id of graph.missing) {
    const { x, y } = at(id);
    boxes.push(`<g class="map-node map-node-missing"><rect x="${x}" y="${y}" width="${BOX.width}" height="${BOX.height}" rx="7"/>`
      + `<text class="map-id" x="${x + 12}" y="${y + 22}">${escape(shorten(id, 22))}</text><text class="map-note" x="${x + 12}" y="${y + 42}">no existe en el proyecto</text></g>`);
  }
  const marker = (id, cls) => `<marker id="${id}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="${cls}" d="M0 0 L10 5 L0 10 z"/></marker>`;
  const tall = Math.max(height, ...placed.map(p => p.y + 24));
  return { graph, svg: `<svg class="project-map" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${tall}" width="${width}" height="${tall}" role="img" aria-label="Mapa de las páginas del proyecto">`
    + `<defs>${marker('map-arrow', 'map-head')}${marker('map-arrow-missing', 'map-head-missing')}</defs>${arrows.join('')}${boxes.join('')}${labels.join('')}</svg>` };
}
