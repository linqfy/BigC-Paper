// Full pages ready to edit (idea 14): shop, profile, settings, server selector, quests and confirmation; and passive
// HUD pages (idea 23): health and hunger, coordinates, money and an objective, in the corners the game's own HUD
// leaves free. Original layouts in one style (dark gradient, frosted-glass panels, glowing buttons); every text fits
// its box by the plugin's own measure (checker.mjs), stays in the safe area, and every page validates as schema 9.
import { createScreen, upgradeLogic, addElement, setScreenLogic, setElementLogic, validateScreen } from './model.mjs';

export const PAGE_TEMPLATES = Object.freeze([
  { id: 'tienda', name: 'Tienda (cobra con Vault)' },
  { id: 'perfil', name: 'Perfil del jugador' },
  { id: 'ajustes', name: 'Ajustes con interruptores' },
  { id: 'servidores', name: 'Selector de servidores' },
  { id: 'misiones', name: 'Lista de misiones' },
  { id: 'confirmar', name: 'Confirmación (¿Seguro?)' },
  { id: 'hud_vida', name: 'HUD: vida y hambre' },
  { id: 'hud_coordenadas', name: 'HUD: coordenadas y mundo' },
  { id: 'hud_dinero', name: 'HUD: dinero' },
  { id: 'hud_objetivo', name: 'HUD: objetivo con progreso' },
  { id: 'aviso', name: 'Aviso que entra y se va (toast)' },
  { id: 'logro', name: 'Logro desbloqueado (toast)' }
].map(Object.freeze));

const GLASS = { fillColor: '#0c1824', borderColor: '#bfe9ff', textColor: '#ffffff', opacity: .82, backdropBlur: 6, radius: .14, textCase: 'original' };
const BUTTON = { fillColor: '#173b48', borderColor: '#8bdfc9', textColor: '#edf8f4', radius: .14, hoverColor: '#53dbc1', hoverEffect: 'glow', textCase: 'original' };
const TEXT = { textColor: '#ffffff', textCase: 'original' }, NOTE = { textColor: '#bfe9ff', textCase: 'original' };

/** A fresh schema 9 page with the template's elements (prototypes from the model, so every style field exists). */
function page(id, title, build, logic = null, presentation = null) {
  const base = upgradeLogic(createScreen()), proto = kind => addElement({ ...base, elements: [] }, kind).elements[0];
  const make = (kind, eid, text, x, y, width, height, style = {}, extra = {}) => ({ ...proto(kind), id: eid, text, x, y, width, height,
    border: false, background: false, action: '', ...extra, style: { ...proto(kind).style, ...style } });
  const parts = {
    background: () => make('decoration', 'fondo', '', 0, 0, 2, 2, { fillColor: '#0d1b26', gradientColor: '#163447', gradientDirection: 'vertical' }, { background: true, layer: -10 }),
    title: text => make('label', 'titulo', text, 0, .76, 1.2, .2, { ...TEXT, textScale: 2 }, { layer: 2 }),
    glass: (eid, x, y, w, h) => make('decoration', eid, '', x, y, w, h, GLASS, { background: true, border: true, shape: 'rounded', layer: 0 }),
    label: (eid, text, x, y, w, h, style = TEXT) => make('label', eid, text, x, y, w, h, style, { layer: 1 }),
    button: (eid, text, x, y, w, h, extra = {}) => make('button', eid, text, x, y, w, h, BUTTON, { background: true, border: true, shape: 'rounded', layer: 1, ...extra }),
    // A live bar: the box is the track, the text colour fills it.
    bar: (eid, text, x, y, w, h, style) => make('label', eid, text, x, y, w, h, { radius: .5, ...style }, { background: true, border: true, shape: 'rounded', layer: 1 })
  };
  let screen = { ...base, id, title, elements: build(parts), ...(presentation ? { presentation: { ...base.presentation, ...presentation } } : {}) };
  for (const e of screen.elements) if (e.logic) screen = setElementLogic(screen, e.id, e.logic);
  if (logic) screen = setScreenLogic(screen, logic);
  return validateScreen(screen);
}
const withLogic = (e, logic) => ({ ...e, logic });
const footer = (p, back = true) => back
  ? [p.button('volver', 'Volver', -.35, -.74, .5, .16, { action: '' , logic: { actions: [{ type: 'back' }] } }), p.button('cerrar', 'Cerrar', .35, -.74, .5, .16, { action: 'close' })]
  : [p.button('cerrar', 'Cerrar', 0, -.74, .5, .16, { action: 'close' })];

const BUILDERS = {
  tienda: () => page('tienda', 'Tienda', p => [
    p.background(), p.title('Tienda'), p.label('saldo', 'Saldo: {balance}', 0, .58, 1, .12, NOTE),
    ...[['espada', 'Espada', '$100', '100', 'DIAMOND_SWORD 1', '-.6'], ['manzanas', 'Manzanas', '$20', '20', 'GOLDEN_APPLE 4', '0'], ['cohetes', 'Cohetes', '$50', '50', 'FIREWORK_ROCKET 16', '.6']]
      .flatMap(([id, name, price, cost, item, x]) => [
        p.glass(`tarjeta_${id}`, Number(x), .02, .52, .86),
        p.label(`nombre_${id}`, name, Number(x), .3, .48, .12),
        p.label(`precio_${id}`, price, Number(x), .14, .48, .12, NOTE),
        withLogic(p.button(`comprar_${id}`, 'Comprar', Number(x), -.22, .42, .16), { actions: [{ type: 'charge', amount: cost,
          then: [{ type: 'give', value: item }, { type: 'message', value: `<green>¡Compraste ${name.toLowerCase()}!` }],
          else: [{ type: 'message', value: '<red>No te alcanza la plata.' }] }] })
      ]),
    ...footer(p, false)
  ]),
  perfil: () => page('perfil', 'Perfil', p => [
    p.background(), p.title('Perfil'), p.glass('panel', 0, .02, 1.3, .9),
    p.label('nombre', '{player}', 0, .32, 1.2, .16, { ...TEXT, textScale: 2 }),
    p.label('nivel', 'Nivel {level}', 0, .12, 1.2, .12, NOTE),
    p.label('vida', 'Vida {health}/{max_health}', 0, -.02, 1.2, .12),
    p.label('barra', '{bar:health}', 0, -.15, 1, .08, { textColor: '#e83a48', fillColor: '#2a0c10', borderColor: '#000000', radius: .5 }),
    p.label('mundo', 'Mundo: {world} · Ping {ping}', 0, -.3, 1.2, .12, NOTE),
    ...footer(p)
  ]),
  ajustes: () => page('ajustes', 'Ajustes', p => [
    p.background(), p.title('Ajustes'), p.glass('panel', 0, .04, 1.5, .8),
    ...[['musica', 'Música', .3], ['particulas', 'Partículas', .06]].flatMap(([id, name, y]) => [
      p.label(`texto_${id}`, name, -.38, y, .6, .12, { ...TEXT, textAlign: 'left' }),
      withLogic(p.button(`cambiar_${id}`, `{${id}}`, .42, y, .44, .16), { actions: [{ type: 'condition', value: `{${id}} == Sí`,
        then: [{ type: 'variable', variable: id, value: 'No' }], else: [{ type: 'variable', variable: id, value: 'Sí' }] }] })
    ]),
    p.label('texto_idioma', 'Idioma', -.38, -.18, .6, .12, { ...TEXT, textAlign: 'left' }),
    withLogic(p.button('cambiar_idioma', '< {idioma} >', .42, -.18, .44, .16), { actions: [{ type: 'condition', value: '{idioma} == Español',
      then: [{ type: 'variable', variable: 'idioma', value: 'English' }],
      else: [{ type: 'condition', value: '{idioma} == English', then: [{ type: 'variable', variable: 'idioma', value: 'Português' }], else: [{ type: 'variable', variable: 'idioma', value: 'Español' }] }] }] }),
    ...footer(p)
  ], { variables: { '*musica': 'Sí', '*particulas': 'Sí', '*idioma': 'Español' } }),
  servidores: () => page('servidores', 'Servidores', p => [
    p.background(), p.title('Servidores'), p.label('ayuda', 'Elegí a dónde ir', 0, .58, 1, .12, NOTE),
    ...[['survival', 'Survival', 'Clásico', '-.6'], ['skyblock', 'Skyblock', 'Islas', '0'], ['creativo', 'Creativo', 'Libre', '.6']].flatMap(([id, name, note, x]) => [
      p.glass(`tarjeta_${id}`, Number(x), .02, .52, .86),
      p.label(`nombre_${id}`, name, Number(x), .3, .48, .12),
      p.label(`modo_${id}`, note, Number(x), .14, .48, .12, NOTE),
      withLogic(p.button(`entrar_${id}`, 'Entrar', Number(x), -.22, .42, .16), { actions: [{ type: 'server', value: id }] })
    ]),
    ...footer(p, false)
  ]),
  misiones: () => page('misiones', 'Misiones', p => [
    p.background(), p.title('Misiones'), p.glass('panel', 0, .005, 1.6, .95),
    withLogic(p.button('mision', '{index}. {item}', 0, .38, 1.4, .16, { logic: undefined }), { list: { source: 'misiones', rows: 4, spacing: 18,
      'on-select': [{ type: 'message', value: '<yellow>Misión elegida: {item}' }] } }),
    ...footer(p)
  ], { variables: { misiones: 'Juntar 10 troncos,Matar 5 zombis,Pescar 3 peces,Minar 20 piedras' } }),
  // Passive HUD pages: no background and no buttons, readable over any world (glass behind the text, outlined letters).
  // Their glass panels stay inside the safe area (x 12..244, y 8..133), so no screen cuts the frame.
  hud_vida: () => hud('hud_vida', 'Vida', p => [
    p.glass('panel', -.59, .665, .62, .44),
    p.label('vida', '❤ {health}/{max_health}', -.59, .79, .54, .12, HUD_TEXT),
    p.bar('barra_vida', '{bar:health}', -.59, .69, .54, .07, { textColor: '#e83a48', fillColor: '#2a0c10', borderColor: '#05080c' }),
    p.label('hambre', 'Hambre {food}/20', -.59, .595, .54, .12, HUD_TEXT),
    p.bar('barra_hambre', '{bar:food}', -.59, .495, .54, .07, { textColor: '#e8a33a', fillColor: '#2a1a08', borderColor: '#05080c' })
  ]),
  hud_coordenadas: () => hud('hud_coordenadas', 'Coordenadas', p => [
    p.glass('panel', -.4, .73, 1, .3),
    p.label('posicion', 'X {x} Y {y} Z {z}', -.4, .79, .94, .12, HUD_TEXT),
    p.label('mundo', 'Mundo: {world}', -.4, .66, .94, .12, { ...HUD_TEXT, textColor: '#bfe9ff' })
  ]),
  hud_dinero: () => hud('hud_dinero', 'Dinero', p => [
    p.glass('panel', .64, .72, .5, .32),
    p.label('monedas', '$ {balance_short}', .64, .76, .44, .12, { ...HUD_TEXT, textColor: '#ffd966', textAlign: 'right' }),
    p.label('nota', 'Monedas', .64, .63, .44, .12, { ...HUD_TEXT, textColor: '#bfe9ff', textAlign: 'right' })
  ]),
  hud_objetivo: () => hud('hud_objetivo', 'Objetivo', p => [
    p.glass('panel', .48, .27, .84, .56),
    p.label('titulo', 'Objetivo', .48, .45, .78, .12, { ...HUD_TEXT, textColor: '#53dbc1' }),
    p.label('objetivo', '{objetivo}', .48, .32, .78, .12, HUD_TEXT),
    p.bar('barra', '{bar:progreso/10}', .48, .2, .78, .07, { textColor: '#53dbc1', fillColor: '#0c2a26', borderColor: '#05080c' }),
    p.label('cuenta', '{progreso} de 10', .48, .1, .78, .12, { ...HUD_TEXT, textColor: '#bfe9ff' })
  ], { variables: { '*objetivo': 'Juntá 10 troncos', '*progreso': '3' } }),
  // Toasts (idea 22): HUD pages that join the ones on screen, slide in from the left and leave the same way. The toast
  // action fills {titulo} and {texto}; these variables only give the editor and /tideframe something to show.
  aviso: () => toast('aviso', 'Aviso', '#53dbc1', p => [
    p.glass('panel', -.4, .26, 1, .3),
    p.accent(),
    p.label('titulo', '{titulo}', -.36, .31, .86, .12, { ...HUD_TEXT, textColor: '#53dbc1' }),
    p.label('texto', '{texto}', -.36, .19, .86, .12, { ...HUD_TEXT, textColor: '#e6f3f8' })
  ], { titulo: 'Aviso', texto: 'Progreso guardado' }),
  logro: () => toast('logro', 'Logro', '#ffd966', p => [
    p.glass('panel', -.4, .26, 1, .3),
    p.label('estrella', '★', -.75, .26, .16, .24, { ...HUD_TEXT, textColor: '#ffd966', textScale: 2, textAlign: 'center' }),
    p.label('titulo', '{titulo}', -.27, .31, .74, .12, { ...HUD_TEXT, textColor: '#ffd966' }),
    p.label('texto', '{texto}', -.27, .19, .74, .12, { ...HUD_TEXT, textColor: '#e6f3f8' })
  ], { titulo: '¡Logro!', texto: 'Primera compra' }),
  confirmar: () => page('confirmar', 'Confirmar', p => [
    p.background(), p.glass('panel', 0, .02, 1.1, .7),
    p.label('pregunta', '¿Seguro?', 0, .2, 1, .2, { ...TEXT, textScale: 2 }),
    p.label('detalle', 'No se puede deshacer.', 0, .02, 1, .12, NOTE),
    withLogic(p.button('si', 'Sí', -.25, -.18, .36, .16), { actions: [{ type: 'message', value: '<green>Hecho.' }, { type: 'back' }] }),
    withLogic(p.button('no', 'No', .25, -.18, .36, .16), { actions: [{ type: 'back' }] })
  ])
};

/** HUD text: left-aligned, as written, with a dark outline so it reads over a bright sky or snow. */
const HUD_TEXT = { textColor: '#ffffff', textCase: 'original', textAlign: 'left', textOutline: '#05080c' };
/** A passive HUD page (the player keeps moving; no cursor, no clicks). */
function hud(id, title, build, logic = null) { return page(id, title, build, logic, { mode: 'overlay', cursor: 'text' }); }
/** A toast: a HUD page that stacks, with an accent colour (the panel's border and a strip by its left edge). */
function toast(id, title, accent, build, variables) {
  const slide = { effect: 'slide', from: 'left', distance: 48, 'duration-ms': 300 };
  return page(id, title, p => build({ ...p,
    glass: (eid, x, y, w, h) => ({ ...p.glass(eid, x, y, w, h), style: { ...p.glass(eid, x, y, w, h).style, borderColor: accent } }),
    accent: () => ({ ...p.glass('franja', -.855, .26, .03, .2), border: false, layer: 1, style: { ...p.glass('franja', 0, 0, 1, 1).style, fillColor: accent, opacity: 1, backdropBlur: 0, radius: .5 } })
  }), { variables, animation: { open: { ...slide, easing: 'ease-out' }, close: { ...slide, easing: 'ease-in' } } }, { mode: 'overlay', cursor: 'text', stack: true });
}

/** A new page from template {@code key}; {@code taken} ids get a numbered id instead. */
export function pageFromTemplate(key, taken = []) {
  const build = BUILDERS[key];
  if (!build) throw Error('Unknown original template.');
  const screen = build(), used = new Set(taken);
  let id = screen.id, n = 2;
  while (used.has(id)) id = `${screen.id}_${n++}`;
  return id === screen.id ? screen : validateScreen({ ...screen, id });
}
