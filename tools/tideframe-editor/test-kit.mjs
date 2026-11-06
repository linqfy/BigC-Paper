// The in-game test kit (plan 46-75, phase 0): /tideframe prueba opens a menu with one button per new function and a
// showcase (word colours, calculated text, a picture cut to a circle, game item icons, a 9-slice frame), plus the HUD
// pages and the toast those buttons use. Every page is built with the editor's own modules, so it validates exactly as
// in the editor, and every id starts with "prueba" so it never meets the operator's pages.
// Run: node tools/tideframe-editor/test-kit.mjs OUT_FOLDER   (writes prueba*.json)
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';
import { createScreen, upgradeLogic, addElement, setElementLogic, setScreenLogic, validateScreen, serializeScreen } from './model.mjs';
import { pageFromTemplate } from './page-templates.mjs';
import { setPresentation } from './presentation-tools.mjs';

// ----- original pictures, drawn here -----
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc = bytes => { let c = 0xffffffff; for (const b of bytes) c = crcTable[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function chunk(type, data) {
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]), out = Buffer.alloc(body.length + 8);
  out.writeUInt32BE(data.length, 0); body.copy(out, 4); out.writeUInt32BE(crc(body), 4 + body.length); return out;
}
/** Base64 PNG of an RGBA picture; {@code paint(x, y)} gives [r, g, b, a] for each pixel. */
function png(width, height, paint) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) raw.set(paint(x, y), y * (width * 4 + 1) + 1 + x * 4);
  const header = Buffer.alloc(13); header.writeUInt32BE(width, 0); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]).toString('base64');
}
const rgb = hex => [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16));
const mix = (a, b, t) => { const k = Math.max(0, Math.min(1, t)); return rgb(a).map((v, i) => Math.round(v + (rgb(b)[i] - v) * k)); };
/** A square landscape (sky, sun, two hills): a photo with corners, so cutting it to a circle shows. */
const LANDSCAPE = png(64, 64, (x, y) => {
  const hill = (cx, top, width) => y >= top + ((x - cx) / width) ** 2 * 24;
  if (hill(46, 40, 20)) return [...mix('#4e7a3a', '#33572a', (y - 40) / 24), 255];
  if (hill(16, 34, 24)) return [...mix('#6b8f4e', '#4e7a3a', (y - 34) / 30), 255];
  if ((x - 44) ** 2 + (y - 18) ** 2 <= 64) return [...rgb('#ffd966'), 255];
  return [...mix('#87b8e8', '#d8ecff', y / 40), 255];
});
/** A 9-slice frame: dark outline, gold band lit from above, dark inner line and a glass middle (16 px border). */
const FRAME = png(48, 48, (x, y) => {
  const radius = 14, cx = Math.min(Math.max(x + .5, radius), 48 - radius), cy = Math.min(Math.max(y + .5, radius), 48 - radius);
  const inside = radius - Math.hypot(x + .5 - cx, y + .5 - cy), edge = Math.min(x + .5, y + .5, 47.5 - x, 47.5 - y);
  const d = Math.min(inside, edge);
  if (d < 0) return [0, 0, 0, 0];
  if (d < 3) return [...rgb('#16121d'), 255];
  if (d < 8) return [...mix('#f7e6ae', '#9a6a2d', y / 47), 255];
  if (d < 10) return [...rgb('#1a1020'), 255];
  return [...rgb('#0c1824'), 220];
});

// ----- pages -----
const BUTTON = { fillColor: '#173b48', borderColor: '#8bdfc9', textColor: '#edf8f4', radius: .14, hoverColor: '#53dbc1', hoverEffect: 'glow', textCase: 'original' };
const TEXT = { textColor: '#ffffff', textCase: 'original', textAlign: 'left' }, NOTE = { ...TEXT, textColor: '#bfe9ff' };
function menu() {
  const base = upgradeLogic(createScreen()), proto = kind => addElement({ ...base, elements: [] }, kind).elements[0];
  const make = (kind, id, text, x, y, width, height, style = {}, extra = {}) => ({ ...proto(kind), id, text, x, y, width, height,
    border: false, background: false, action: '', ...extra, style: { ...proto(kind).style, ...style } });
  const button = (id, text, x, y, extra = {}) => make('button', id, text, x, y, .42, .16, BUTTON, { background: true, border: true, shape: 'rounded', layer: 2, ...extra });
  const elements = [
    make('decoration', 'fondo', '', 0, 0, 2, 2, { fillColor: '#0d1b26', gradientColor: '#163447', gradientDirection: 'vertical' }, { background: true, layer: -10 }),
    make('label', 'titulo', 'Prueba 0.23', 0, .76, 1.2, .2, { ...TEXT, textAlign: 'center', textScale: 2 }, { layer: 2 }),
    make('label', 'ayuda', 'Cada botón prueba una función', 0, .6, 1.5, .12, { ...NOTE, textAlign: 'center' }, { layer: 2 }),
    // The showcase: a 9-slice frame holding word colours, live text, calculated text, a cut picture and item icons. The
    // frame stays inside the safe area (its left edge at x 14 of 256), so no screen cuts it.
    make('decoration', 'vitrina', '', -.4625, -.16, .855, 1.36, { slice: 16, sliceScale: 4 }, { image: FRAME, layer: 0 }),
    make('label', 'colores', '<gold>Oro</gold>, <aqua>agua</aqua> y <#ff6ad5>rosa</#ff6ad5>', -.46, .39, .78, .12, TEXT, { layer: 2 }),
    make('label', 'hola', 'Hola <aqua>{player}</aqua>', -.46, .265, .78, .12, { ...TEXT, textOutline: '#05080c' }, { layer: 2 }),
    make('label', 'saldo', 'Saldo: <gold>{balance}</gold>', -.46, .14, .78, .12, { ...TEXT, textOutline: '#05080c' }, { layer: 2 }),
    make('label', 'cuenta', '21 × 2 = {21 * 2}', -.46, .015, .78, .12, NOTE, { layer: 2 }),
    make('decoration', 'foto', '', -.69, -.3, .25, 32 / 72, { imageClip: true }, { image: LANDSCAPE, shape: 'ellipse', layer: 2 }),
    make('label', 'foto_texto', 'Recorte', -.34, -.3, .4, .12, NOTE, { layer: 2 }),
    ...[['espada', 'diamond_sword', -.78], ['manzana', 'golden_apple', -.6], ['perla', 'ender_pearl', -.42]]
      .map(([id, item, x]) => ({ ...make('decoration', `item_${id}`, '', x, -.655, .125, .2222, {}, { layer: 2 }), item })),
    make('label', 'items_texto', 'Ítems', -.195, -.655, .25, .12, NOTE, { layer: 2 })
  ];
  const actions = {
    aviso: [{ type: 'toast', value: 'prueba_logro', seconds: '4', values: { titulo: '¡Logro!', texto: 'Hola {player}' } }],
    sumar: [{ type: 'hud', value: 'prueba_hud_dinero' }],
    vida: [{ type: 'hud', value: 'prueba_hud_vida' }],
    sacar: [{ type: 'hud', value: 'prueba_hud_dinero', mode: 'remove' }, { type: 'hud', value: 'prueba_hud_vida', mode: 'remove' }],
    jefe: [{ type: 'command', value: 'tideframe boss' }],
    golpe: [{ type: 'command', value: 'tideframe boss hit' }],
    sonido: [{ type: 'sound', value: 'tideframe:prueba' }, { type: 'actionbar', value: 'Sonido tideframe:prueba (plugins/BigCasares/tideframe/engine/sounds/prueba.ogg)' }],
    seguro: [{ type: 'confirm', title: '¿Seguro?', value: 'Es solo una prueba.', yes: 'Sí', no: 'No', then: [{ type: 'message', value: '<green>Dijiste que sí.' }], else: [{ type: 'message', value: '<yellow>Dijiste que no.' }] }],
    comprar: [{ type: 'charge', amount: '10', then: [{ type: 'message', value: '<green>Cobrado: 10.' }], else: [{ type: 'message', value: '<red>No alcanza o no hay economía (Vault).' }] }],
    cerrar: [{ type: 'close' }]
  };
  const grid = [['aviso', 'Aviso'], ['sumar', 'Más HUD'], ['vida', 'HUD vida'], ['sacar', 'Sin HUD'], ['jefe', 'Jefe'], ['golpe', 'Golpe'],
    ['sonido', 'Sonido'], ['seguro', '¿Seguro?'], ['espera', 'Espera'], ['comprar', 'Comprar']];
  grid.forEach(([id, text], i) => elements.push(button(`b_${id}`, text, i % 2 ? .68 : .22, .4 - Math.floor(i / 2) * .23)));
  elements.push(button('b_cerrar', 'Cerrar', .45, .4 - 5 * .23));
  let screen = validateScreen({ ...base, id: 'prueba', title: 'Prueba 0.23', elements });
  for (const [id, list] of Object.entries(actions)) screen = setElementLogic(screen, `b_${id}`, { actions: list });
  screen = setElementLogic(screen, 'b_espera', { actions: [{ type: 'message', value: '<green>Listo. Volvé a tocar antes de 3 s.' }], cooldown: '3s',
    'on-cooldown': [{ type: 'actionbar', value: 'Esperá {cooldown} s' }] });
  return setScreenLogic(screen, { behavior: { 'keyboard-navigation': true } });
}
/** A page template under a kit id. */
function renamed(template, id, title) { return validateScreen({ ...pageFromTemplate(template), id, title }); }

export function kitPages() {
  return [menu(), renamed('hud_vida', 'prueba_hud_vida', 'Prueba vida'), setPresentation(renamed('hud_dinero', 'prueba_hud_dinero', 'Prueba dinero'), { stack: true }),
    renamed('logro', 'prueba_logro', 'Prueba logro')];
}

// Only when run as a program: importing the module (tests, other tools) writes nothing.
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url) && process.argv[2]) {
  mkdirSync(process.argv[2], { recursive: true });
  for (const screen of kitPages()) writeFileSync(join(process.argv[2], `${screen.id}.json`), serializeScreen(screen));
  console.log(`wrote ${kitPages().length} pages to ${process.argv[2]}`);
}
