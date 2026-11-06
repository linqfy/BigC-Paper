// Original pixel-art icons for menus (idea 39): drawn here from simple shapes, with no third-party artwork. Each icon is
// rasterised on a 16 x 16 grid; every shape gets light on its top and left edges and shade on its bottom and right
// ones, and the whole icon a dark one-pixel outline, so the set looks like one family. Pure: pixels only; the page
// turns them into PNG (app.mjs) and the tests and tools/icon-sheet.mjs into a contact sheet.
import { addElement, validateScreenBudget } from './model.mjs';

const OUTLINE = '#16121d';
const hex = value => [1, 3, 5].map(i => parseInt(value.slice(i, i + 2), 16));
const toHex = rgb => `#${rgb.map(v => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, '0')).join('')}`;
const mix = (a, b, t) => toHex(hex(a).map((v, i) => v + (hex(b)[i] - v) * t));
/** A colour's three tones: lit edge, body, shaded edge. */
const tones = base => ({ light: mix(base, '#ffffff', .42), base, dark: mix(base, '#000000', .34) });

// ----- shapes (a 16 x 16 space, y down) -----
const poly = (points, color) => ({ kind: 'poly', points, color });
const circle = (cx, cy, r, color) => ({ kind: 'circle', cx, cy, r, color });
const rect = (x, y, w, h, color) => ({ kind: 'rect', x, y, w, h, color });
/** A thick segment from (x1, y1) to (x2, y2). */
function line(x1, y1, x2, y2, width, color) {
  const dx = x2 - x1, dy = y2 - y1, length = Math.hypot(dx, dy) || 1, nx = -dy / length * width / 2, ny = dx / length * width / 2;
  return poly([[x1 + nx, y1 + ny], [x2 + nx, y2 + ny], [x2 - nx, y2 - ny], [x1 - nx, y1 - ny]], color);
}
/** A star or a gear rim: {@code points} tips between radius {@code inner} and {@code outer}, the first one up. */
function star(cx, cy, outer, inner, points, color, turn = 0) {
  const out = [];
  for (let i = 0; i < points * 2; i++) {
    const angle = -Math.PI / 2 + turn + i * Math.PI / points, r = i % 2 ? inner : outer;
    out.push([cx + Math.cos(angle) * r, cy + Math.sin(angle) * r]);
  }
  return poly(out, color);
}
/** A coin seen from the side, {@code width} long, its left end at ({@code x}, {@code y}): a rim with a lighter face. */
function coin(x, y, width) {
  return [circle(x + 1.4, y + 1.4, 1.4, '#e2951a'), circle(x + width - 1.4, y + 1.4, 1.4, '#e2951a'), rect(x + 1.4, y, width - 2.8, 2.8, '#ffc933')];
}
/** Clears what the shapes before it drew (holes): color null. */
const hole = shape => ({ ...shape, color: null });

function inside(shape, x, y) {
  switch (shape.kind) {
    case 'circle': return (x - shape.cx) ** 2 + (y - shape.cy) ** 2 <= shape.r ** 2;
    case 'rect': return x >= shape.x && x <= shape.x + shape.w && y >= shape.y && y <= shape.y + shape.h;
    default: {
      let hit = false;
      const p = shape.points;
      for (let i = 0, j = p.length - 1; i < p.length; j = i++)
        if ((p[i][1] > y) !== (p[j][1] > y) && x < (p[j][0] - p[i][0]) * (y - p[i][1]) / (p[j][1] - p[i][1]) + p[i][0]) hit = !hit;
      return hit;
    }
  }
}

const P = { burlap: '#d9b27c', gold: '#ffc933', goldDeep: '#e2951a', steel: '#cfdbe6', iron: '#8796a8', wood: '#b9773d', woodDeep: '#7c4a22', red: '#ec3450', green: '#3cc04f',
  blue: '#3d82e6', cyan: '#46c8f0', ice: '#9bf2ff', purple: '#a35cf0', glass: '#d8eef8', orange: '#ff8a2e', cream: '#f4ead2', white: '#f3f7fb', ink: '#283044' };

/** Every icon: id, name in Spanish, and its shapes from back to front. */
export const ICONS = Object.freeze([
  { id: 'moneda', name: 'Moneda', shapes: [circle(8, 8, 6.45, P.gold), circle(8, 8, 4.3, P.goldDeep), circle(8, 8, 3.2, P.gold), circle(6.6, 6.6, .75, '#fff1b8')] },
  { id: 'monedas', name: 'Pila de monedas', shapes: [...coin(2.6, 11.4, 9.4), ...coin(4, 8.4, 9.4), ...coin(2.6, 5.4, 9.4), ...coin(3.8, 2.4, 9.4)] },
  { id: 'bolsa', name: 'Bolsa de monedas', shapes: [circle(8, 10.3, 5.3, P.burlap), poly([[6.1, 6.6], [9.9, 6.6], [10.8, 3.6], [12.2, 2.2], [3.8, 2.2], [5.2, 3.6]], P.burlap),
    rect(5.4, 5.6, 5.2, 1.3, P.woodDeep), circle(8, 10.6, 2.3, P.gold)] },
  { id: 'gema', name: 'Gema', shapes: [poly([[2, 5.5], [8, 5.5], [8, 14]], '#46c4ef'), poly([[8, 5.5], [14, 5.5], [8, 14]], '#1f86cc'),
    poly([[2, 5.5], [4.6, 2.4], [6, 5.5]], P.ice), poly([[4.6, 2.4], [11.4, 2.4], [10, 5.5], [6, 5.5]], '#c8fcff'), poly([[11.4, 2.4], [14, 5.5], [10, 5.5]], '#62dcf6')] },
  { id: 'corazon', name: 'Corazón', shapes: [circle(5.3, 6.1, 3.4, P.red), circle(10.7, 6.1, 3.4, P.red), poly([[2, 7], [14, 7], [8, 13.9]], P.red), circle(4.6, 5, .9, '#ffb3bd')] },
  { id: 'estrella', name: 'Estrella', shapes: [star(8, 8.6, 7.3, 3, 5, P.gold)] },
  { id: 'rayo', name: 'Rayo', shapes: [poly([[9.6, 1.4], [3.4, 9.1], [7.4, 9.1], [6, 14.6], [12.6, 6.6], [8.6, 6.6], [10.6, 1.4]], '#ffd84a')] },
  { id: 'fuego', name: 'Fuego', shapes: [poly([[8, 1.5], [12.6, 7.4], [12.8, 10.8], [10.6, 14], [5.4, 14], [3.2, 10.8], [3.6, 7.6], [5.6, 9.2], [6.2, 5.4]], P.orange),
    poly([[8, 6.4], [10.4, 10], [10.2, 12.4], [8.8, 13.8], [7.2, 13.8], [5.8, 12.4], [5.8, 10.6], [7, 11.2]], '#ffd84a')] },
  { id: 'espada', name: 'Espada', shapes: [line(5.6, 10.4, 13.4, 2.6, 2.4, P.steel), poly([[12.4, 1.6], [14.4, 1.6], [14.4, 3.6]], P.steel), line(3.2, 8.6, 7.4, 12.8, 1.8, P.gold),
    line(2.6, 13.4, 5, 11, 1.5, P.woodDeep), circle(2.4, 13.6, 1.3, P.gold)] },
  { id: 'escudo', name: 'Escudo', shapes: [poly([[2.6, 2.2], [13.4, 2.2], [13.4, 7.8], [12, 10.6], [8, 14.2], [4, 10.6], [2.6, 7.8]], P.blue),
    rect(7.2, 3.6, 1.6, 8.2, P.gold), rect(4.2, 6.2, 7.6, 1.6, P.gold)] },
  { id: 'flecha_derecha', name: 'Flecha a la derecha', shapes: [rect(2, 6.4, 7.6, 3.2, P.green), poly([[8.4, 2.6], [14.4, 8], [8.4, 13.4]], P.green)] },
  { id: 'flecha_izquierda', name: 'Flecha a la izquierda', turn: 2, of: 'flecha_derecha' },
  { id: 'flecha_arriba', name: 'Flecha hacia arriba', turn: 3, of: 'flecha_derecha' },
  { id: 'flecha_abajo', name: 'Flecha hacia abajo', turn: 1, of: 'flecha_derecha' },
  { id: 'tilde', name: 'Tilde (sí)', shapes: [line(2.4, 8.4, 6.4, 12.4, 2.8, P.green), line(5.4, 12.4, 13.8, 3.6, 2.8, P.green)] },
  { id: 'cruz', name: 'Cruz (no)', shapes: [line(3.4, 3.4, 12.6, 12.6, 2.9, P.red), line(12.6, 3.4, 3.4, 12.6, 2.9, P.red)] },
  { id: 'candado', name: 'Candado', shapes: [circle(8, 6.6, 4.3, P.iron), hole(circle(8, 6.6, 2.5, null)), rect(2.6, 6.8, 10.8, 7.6, P.gold), circle(8, 9.8, 1.2, P.ink), rect(7.4, 9.8, 1.2, 2.6, P.ink)] },
  { id: 'llave', name: 'Llave', shapes: [circle(4.8, 8, 3.4, P.gold), hole(circle(4.8, 8, 1.4, null)), rect(7.6, 7, 7, 2, P.gold), rect(11.4, 8.6, 1.2, 2.6, P.gold), rect(13.2, 8.6, 1.2, 1.8, P.gold)] },
  { id: 'cofre', name: 'Cofre', shapes: [rect(1.8, 7.4, 12.4, 6.6, P.woodDeep), poly([[1.8, 7.4], [1.8, 4.6], [4, 2.6], [12, 2.6], [14.2, 4.6], [14.2, 7.4]], P.wood),
    rect(3.8, 2.6, 1.4, 11.4, P.iron), rect(10.8, 2.6, 1.4, 11.4, P.iron), rect(6.8, 6.4, 2.4, 3.2, P.gold)] },
  { id: 'pocion', name: 'Poción', shapes: [circle(8, 10, 4.7, P.glass), rect(6.6, 3.2, 2.8, 3.4, P.glass), circle(8, 10.6, 3.6, P.purple), rect(6.2, 1.6, 3.6, 1.8, P.wood), circle(6.4, 8.4, .8, P.white)] },
  { id: 'corona', name: 'Corona', shapes: [poly([[2.4, 12.6], [2.4, 4.8], [5.3, 8], [8, 3.4], [10.7, 8], [13.6, 4.8], [13.6, 12.6]], P.gold),
    circle(2.4, 4.2, 1.1, P.gold), circle(8, 2.8, 1.1, P.gold), circle(13.6, 4.2, 1.1, P.gold), circle(8, 10.4, 1.2, P.red), circle(4.9, 10.6, .9, P.blue), circle(11.1, 10.6, .9, P.blue)] },
  { id: 'trofeo', name: 'Trofeo', shapes: [circle(3.6, 5.4, 2.3, P.gold), hole(circle(3.6, 5.4, 1, null)), circle(12.4, 5.4, 2.3, P.gold), hole(circle(12.4, 5.4, 1, null)),
    poly([[3.6, 2.2], [12.4, 2.2], [11.6, 7.6], [8, 9.6], [4.4, 7.6]], P.gold), rect(7.1, 9, 1.8, 2.6, P.goldDeep), rect(4.6, 11.4, 6.8, 2.8, P.gold)] },
  { id: 'engranaje', name: 'Engranaje', shapes: [star(8, 8, 7, 5.1, 8, P.iron, Math.PI / 8), circle(8, 8, 5.2, P.iron), hole(circle(8, 8, 2.1, null))] },
  { id: 'casa', name: 'Casa', shapes: [rect(3, 7.6, 10, 6.6, P.cream), poly([[1.2, 8.2], [8, 1.8], [14.8, 8.2]], P.red), rect(6.9, 9.8, 2.4, 4.4, P.woodDeep), rect(10, 9.2, 2, 2, P.cyan)] },
  { id: 'reloj', name: 'Reloj', shapes: [circle(8, 8, 6.45, P.blue), circle(8, 8, 5.1, P.white), line(8, 8.2, 8, 4, 1.2, P.ink), line(7.8, 8, 11, 9.8, 1.2, P.ink)] },
  { id: 'pergamino', name: 'Pergamino', shapes: [rect(3.4, 2.8, 9.2, 10.6, P.cream), rect(2, 1.8, 12, 2.2, P.wood), rect(2, 12.2, 12, 2.2, P.wood),
    rect(5, 5.6, 6, .9, '#a08a66'), rect(5, 7.6, 6, .9, '#a08a66'), rect(5, 9.6, 4.4, .9, '#a08a66')] },
  { id: 'info', name: 'Información', shapes: [circle(8, 8, 6.45, P.blue), rect(7.1, 6.8, 1.8, 5.4, P.white), circle(8, 4.6, 1.1, P.white)] },
  { id: 'regalo', name: 'Regalo', shapes: [rect(2.4, 7, 11.2, 7, P.red), rect(1.8, 4.6, 12.4, 2.8, P.red), rect(7, 4.6, 2, 9.4, P.gold), circle(5.8, 3.4, 1.7, P.gold), circle(10.2, 3.4, 1.7, P.gold)] },
  { id: 'jugador', name: 'Jugador', shapes: [poly([[2.6, 14.4], [3.6, 10], [6, 8.8], [10, 8.8], [12.4, 10], [13.4, 14.4]], P.blue), circle(8, 5.2, 3.2, '#f2c6a0')] }
]);

const SIZE = 16, SAMPLES = 4;
/** A 16 x 16 grid of colours (null = transparent) for icon {@code id}. */
export function iconPixels(id) {
  const icon = ICONS.find(entry => entry.id === id);
  if (!icon) throw Error(`Unknown icon ${id}.`);
  if (icon.of) return rotate(iconPixels(icon.of), icon.turn);
  const owner = Array.from({ length: SIZE }, () => Array(SIZE).fill(-1));
  icon.shapes.forEach((shape, index) => {
    for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
      let hits = 0;
      for (let sy = 0; sy < SAMPLES; sy++) for (let sx = 0; sx < SAMPLES; sx++) if (inside(shape, x + (sx + .5) / SAMPLES, y + (sy + .5) / SAMPLES)) hits++;
      if (hits * 2 >= SAMPLES * SAMPLES) owner[y][x] = shape.color === null ? -1 : index;
    }
  });
  const at = (x, y) => x < 0 || y < 0 || x >= SIZE || y >= SIZE ? -1 : owner[y][x];
  const pixels = Array.from({ length: SIZE }, () => Array(SIZE).fill(null));
  for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
    const index = owner[y][x];
    if (index < 0) { if ([at(x - 1, y), at(x + 1, y), at(x, y - 1), at(x, y + 1)].some(i => i >= 0)) pixels[y][x] = OUTLINE; continue; }
    const t = tones(icon.shapes[index].color), lit = at(x - 1, y) !== index || at(x, y - 1) !== index, shaded = at(x + 1, y) !== index || at(x, y + 1) !== index;
    pixels[y][x] = lit && !shaded ? t.light : shaded && !lit ? t.dark : t.base;
  }
  return pixels;
}
/** The grid turned {@code quarters} times clockwise. */
function rotate(pixels, quarters) {
  let out = pixels;
  for (let q = 0; q < quarters; q++) out = out[0].map((_, x) => out.map(row => row[x]).reverse());
  return out;
}
/** Where the next icon goes: the first spot of a grid around the middle that no other picture covers. */
function freeSpot(screen, width, height) {
  const pictures = screen.elements.filter(e => e.image);
  const overlaps = (x, y) => pictures.some(e => Math.abs(e.x - x) < (e.width + width) / 2 - .01 && Math.abs(e.y - y) < (e.height + height) / 2 - .01);
  for (const y of [0, -.5, .5]) for (const x of [0, .3, -.3, .6, -.6]) if (!overlaps(x, y)) return { x, y };
  return { x: 0, y: 0 };
}
/**
 * {@code screen} with icon {@code id} added as a picture ({@code png}: its Base64 PNG): a 32-pixel square near the middle,
 * where no other picture is, named after the icon. Whole steps of 16 pixels keep the pixel art sharp in the game.
 */
export function insertIcon(screen, id, png) {
  const icon = ICONS.find(entry => entry.id === id);
  if (!icon) throw Error(`Unknown icon ${id}.`);
  const next = addElement(screen, 'decoration'), used = new Set(next.elements.map(e => e.id)), base = `icono_${icon.id}`.slice(0, 32);
  let name = base, n = 2;
  while (used.has(name)) { const suffix = `_${n++}`; name = base.slice(0, 32 - suffix.length) + suffix; }
  const added = next.elements.at(-1), width = .25, height = 32 / 72, { x, y } = freeSpot(screen, width, height);
  next.elements[next.elements.length - 1] = { ...added, id: name, text: '', x, y, width, height, border: false, background: false, image: png };
  return { screen: validateScreenBudget(next), id: name };
}
/** RGBA bytes of an icon scaled up {@code scale} times with hard pixel edges (so the game's smoothing keeps them sharp). */
export function iconRgba(id, scale = 8) {
  const pixels = iconPixels(id), side = SIZE * scale, out = new Uint8ClampedArray(side * side * 4);
  for (let y = 0; y < side; y++) for (let x = 0; x < side; x++) {
    const color = pixels[Math.floor(y / scale)][Math.floor(x / scale)];
    if (!color) continue;
    const [r, g, b] = hex(color), i = (y * side + x) * 4;
    out[i] = r; out[i + 1] = g; out[i + 2] = b; out[i + 3] = 255;
  }
  return { width: side, height: side, data: out };
}
