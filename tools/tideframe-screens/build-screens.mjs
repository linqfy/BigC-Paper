// BigCasares' Tideframe screens (bigc_misiones, bigc_tienda), built and validated with Tideframe's own editor model, so
// they read exactly as the editor writes them and stay editable there. The data comes from BigCasares at run time
// (MissionTideframeMenu, ShopTideframeMenu): lists by setList and texts as player-global variables, so the screens
// declare none of those variables (a local one would hide the value BigCasares sends). Buttons carry no Tideframe
// action, BigCasares handles their clicks (Reclamar claims on the screen itself). The only exception is the X, which
// closes the menu with Tideframe's "close".
// Everything stays in the safe area (x 12..244, y 8..133 of 256 x 144).
//
//   node tools/tideframe-screens/build-screens.mjs <Tideframe folder>
// writes src/main/resources/tideframe/screens/*.json.
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const editor = process.argv[2] ? join(resolve(process.argv[2]), 'editor')
  : join(dirname(fileURLToPath(import.meta.url)), '..', 'tideframe-editor');
const { createScreen, upgradeLogic, addElement, setElementLogic, validateScreen, serializeScreen } =
  await import(pathToFileURL(join(editor, 'model.mjs')).href);

// ----- look -----
const GOLD = '#ffd966', WHITE = '#f2f6f8', SOFT = '#bfe9ff', MUTED = '#8fb3c4', GREEN = '#7ee08a', INK = '#05080c';
const TEXT = { textColor: WHITE, textCase: 'original', textAlign: 'left' };
const BUTTON = { fillColor: '#173b48', borderColor: '#8bdfc9', textColor: '#edf8f4', radius: .16, hoverColor: '#53dbc1', hoverEffect: 'glow', textCase: 'original', textAlign: 'center' };
/** Logical pixel row (0..144 from the top) to the canvas y (-1..1, up). */
const y = row => Math.round((72 - row) / 72 * 1000) / 1000;
/** Logical pixel columns (left, right) to the canvas x and width. */
const span = (left, right) => ({ x: Math.round(((left + right) / 2 - 128) / 128 * 1000) / 1000, width: Math.round((right - left) / 128 * 1000) / 1000 });

function page(id, title, build) {
  const base = upgradeLogic(createScreen());
  const proto = kind => addElement({ ...base, elements: [] }, kind).elements[0];
  const make = (kind, elementId, text, [left, right], row, height, style = {}, extra = {}) => ({
    ...proto(kind), id: elementId, text, ...span(left, right), y: y(row), height: Math.round(height / 72 * 1000) / 1000,
    border: false, background: false, action: '', layer: 2, ...extra, style: { ...proto(kind).style, ...style }
  });
  const parts = build(make);
  let screen = validateScreen({ ...base, id, title, presentation: { mode: 'menu', scale: 1, cursor: 'arrow' }, elements: parts.elements });
  for (const [elementId, logic] of Object.entries(parts.logic ?? {})) screen = setElementLogic(screen, elementId, logic);
  return validateScreen(screen);
}

/** The backdrop every BigCasares screen shares: a dark gradient and a framed glass panel inside the safe area. */
const backdrop = make => [
  make('decoration', 'fondo', '', [0, 256], 72, 144, { fillColor: '#0d1b26', gradientColor: '#163447', gradientDirection: 'vertical' }, { background: true, layer: -10 }),
  make('decoration', 'panel', '', [14, 242], 71, 124, { fillColor: '#0b1620', borderColor: '#f2c46b', radius: .05, outlineWidth: 1, opacity: .92 },
    { background: true, border: true, shape: 'rounded', layer: -5 })
];
/** One list row repeated {@code rows} times, {@code spacing} px apart. */
const list = (source, rows, spacing) => ({ list: { source, rows, spacing } });
/**
 * The close X inside the panel's corner. Tideframe's own "close" action (it only closes the menu, BigCasares then hears
 * the close event); with it Tideframe adds no X of its own over the panel's border.
 */
const closeX = make => make('button', 'cerrar', 'X', [226, 240], 17, 11, { ...BUTTON, borderColor: '#e08a7a' },
  { background: true, border: true, shape: 'rounded', action: 'close' });

// ----- bigc_misiones: read only -----
const missions = page('bigc_misiones', 'Misiones', make => ({
  elements: [
    ...backdrop(make),
    closeX(make),
    make('label', 'titulo', 'Misiones', [40, 216], 19, 16, { ...TEXT, textAlign: 'center', textScale: 2, textColor: GOLD, textOutline: INK }),
    make('label', 'reinicio', '{bigc_titulo} · {bigc_reinicio}', [24, 232], 32, 9, { ...TEXT, textAlign: 'center', textColor: MUTED }),
    // Tabs on the left, over the list's first column; "Cambiar" on the right, as wide as "Reclamar" below it so its
    // longest text ("¿Seguro?") fits inside the border.
    make('button', 'diarias', 'Diarias', [22, 80], 43, 11, BUTTON, { background: true, border: true, shape: 'rounded' }),
    make('button', 'semanales', 'Semanales', [86, 148], 43, 11, BUTTON, { background: true, border: true, shape: 'rounded' }),
    // Changes the daily missions (once a day, never over a ready reward); the second tap confirms.
    make('button', 'cambiar', '{bigc_cambiar}', [178, 238], 43, 11, { ...BUTTON, borderColor: '#f2c46b' }, { background: true, border: true, shape: 'rounded' }),
    make('label', 'col_mision', 'Misión', [22, 136], 55, 8, { ...TEXT, textColor: MUTED }),
    make('label', 'col_progreso', 'Progreso', [136, 184], 55, 8, { ...TEXT, textAlign: 'center', textColor: MUTED }),
    make('label', 'col_premio', 'Premio', [184, 234], 55, 8, { ...TEXT, textAlign: 'right', textColor: MUTED }),
    make('label', 'mision', '{item.titulo}', [22, 136], 66, 9, TEXT),
    make('label', 'progreso', '{item.progreso}', [136, 184], 66, 9, { ...TEXT, textAlign: 'center', textColor: SOFT }),
    make('label', 'premio', '{item.premio}', [184, 234], 66, 9, { ...TEXT, textAlign: 'right', textColor: GREEN }),
    make('label', 'vacio', '{bigc_vacio}', [24, 232], 90, 9, { ...TEXT, textAlign: 'center', textColor: MUTED }),
    make('label', 'pendientes', '{bigc_pendientes}', [22, 150], 125, 9, { ...TEXT, textColor: GOLD }),
    // No action of its own: BigCasares claims every ready reward and redraws the list in place.
    make('button', 'reclamar', 'Reclamar', [178, 238], 125, 11, BUTTON, { background: true, border: true, shape: 'rounded' })
  ],
  logic: {
    mision: list('bigc_misiones', 6, 10), progreso: list('bigc_misiones', 6, 10), premio: list('bigc_misiones', 6, 10),
    // "Reclamar" claims the ready rewards: shown only when there is something to claim.
    reclamar: { 'visible-if': '{bigc_cobrables} > 0' },
    cambiar: { 'visible-if': '{bigc_puede_cambiar} = 1' }
  }
}));

// ----- bigc_tienda -----
const ROW = { fillColor: '#10222e', borderColor: '#2c5566', textColor: WHITE, radius: .1, hoverColor: '#1f4a58', hoverEffect: 'glow' };
const shop = page('bigc_tienda', 'Tienda', make => ({
  elements: [
    ...backdrop(make),
    closeX(make),
    make('label', 'titulo', 'Tienda', [40, 216], 19, 16, { ...TEXT, textAlign: 'center', textScale: 2, textColor: GOLD, textOutline: INK }),
    make('label', 'categoria', '{bigc_tienda_titulo}', [22, 150], 32, 9, { ...TEXT, textColor: SOFT }),
    make('label', 'saldo', 'Saldo: {bigc_tienda_saldo}', [140, 234], 32, 9, { ...TEXT, textAlign: 'right', textColor: GOLD }),
    make('label', 'ayuda', '{bigc_tienda_ayuda}', [22, 234], 43, 8, { ...TEXT, textAlign: 'center', textColor: MUTED }),
    // A row: the whole width is one button (no text, no action: BigCasares handles the click), its texts on top.
    make('button', 'fila', '', [18, 238], 55, 10, ROW, { background: true, border: true, shape: 'rounded', layer: 1 }),
    make('label', 'nombre', '{item.nombre}', [23, 160], 55, 9, TEXT),
    make('label', 'precio', '{item.precio}', [160, 233], 55, 9, { ...TEXT, textAlign: 'right', textColor: GOLD }),
    make('label', 'aviso', '{bigc_tienda_aviso}', [22, 234], 117, 9, { ...TEXT, textAlign: 'center', textColor: '#ffe08a' }),
    make('button', 'volver', 'Volver', [18, 58], 127, 11, BUTTON, { background: true, border: true, shape: 'rounded' }),
    make('button', 'anterior', '<', [92, 108], 127, 11, BUTTON, { background: true, border: true, shape: 'rounded' }),
    make('label', 'pagina', '{bigc_tienda_pagina}', [109, 147], 127, 9, { ...TEXT, textAlign: 'center', textColor: MUTED }),
    make('button', 'siguiente', '>', [148, 164], 127, 11, BUTTON, { background: true, border: true, shape: 'rounded' }),
    // How many lots a click buys or sells: ×1, ×4, ×16 in turn (the rows show the price of that many).
    make('button', 'cantidad', '{bigc_tienda_cantidad}', [178, 238], 127, 11, BUTTON, { background: true, border: true, shape: 'rounded' })
  ],
  logic: {
    fila: list('bigc_tienda', 6, 10), nombre: list('bigc_tienda', 6, 10), precio: list('bigc_tienda', 6, 10),
    // Shown only when they do something: the arrows with more than one page, "Volver" inside a category.
    anterior: { 'visible-if': '{bigc_tienda_paginas} > 1' }, siguiente: { 'visible-if': '{bigc_tienda_paginas} > 1' },
    volver: { 'visible-if': '{bigc_tienda_productos} = 1' }, cantidad: { 'visible-if': '{bigc_tienda_productos} = 1' }
  }
}));

const out = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'src', 'main', 'resources', 'tideframe', 'screens');
mkdirSync(out, { recursive: true });
for (const screen of [missions, shop]) {
  writeFileSync(join(out, `${screen.id}.json`), serializeScreen(screen));
  console.log(`wrote ${screen.id}.json (${screen.elements.length} elements)`);
}
