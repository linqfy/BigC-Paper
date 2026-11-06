// 0.23, ideas 21-45 phase 2: layers panel, project map, copy/paste style, icons, HUD templates, new components and the
// variables panel's run functions.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, upgradeLogic, setElementLogic, setScreenLogic, orderedElements, validateScreen } from './model.mjs';
import { layerOrder, moveLayer, renameElement, renameInLogic } from './layers.mjs';
import { projectGraph, mapSvg } from './project-map.mjs';
import { copyStyle, pasteStyle } from './style-clipboard.mjs';
import { ICONS, iconPixels, iconRgba, insertIcon } from './icon-library.mjs';
import { PAGE_TEMPLATES, pageFromTemplate } from './page-templates.mjs';
import { addTemplate } from './templates.mjs';
import { reviewScreen } from './checker.mjs';
import { startPlay, click, resolve, test as condition, variables, setVariableFromPanel, setSample, setPlaceholders } from './play.mjs';

const base = () => upgradeLogic(createScreen());

test('the layers panel lists the top first and a move changes only what it must', () => {
  let screen = base();
  screen = { ...screen, elements: screen.elements.map((e, i) => ({ ...e, layer: [0, 4, 8][i] })) };
  assert.deepEqual(layerOrder(screen).map(e => e.id), ['close', 'start', 'heading']);
  const moved = moveLayer(screen, 'heading', 'close', 'above');
  assert.deepEqual(layerOrder(moved).map(e => e.id), ['heading', 'close', 'start']);
  assert.deepEqual(moved.elements.map(e => e.layer), [9, 4, 8], 'only the moved element got a new number, just above the old top');
  assert.equal(moveLayer(screen, 'close', 'heading', 'below').elements.find(e => e.id === 'close').layer, -1, 'just below the old bottom');
  const between = moveLayer(screen, 'close', 'start', 'below');
  assert.deepEqual(layerOrder(between).map(e => e.id), ['start', 'close', 'heading']);
  assert.equal(between.elements.find(e => e.id === 'close').layer, 2);
  // Equal layers leave no number in between: every element is renumbered, the order everyone sees stays.
  const flat = { ...screen, elements: screen.elements.map(e => ({ ...e, layer: 0 })) };
  const renumbered = moveLayer(flat, 'heading', 'close', 'above');
  assert.deepEqual(layerOrder(renumbered).map(e => e.id), ['heading', 'close', 'start']);
  assert.deepEqual(orderedElements(renumbered).map(entry => entry.element.id), ['start', 'close', 'heading']);
});
test('renaming an element renames every reference to it', () => {
  let screen = setElementLogic(base(), 'start', { actions: [{ type: 'toggle', elements: ['heading'] }, { type: 'show', value: 'heading, close' },
    { type: 'animation', value: 'heading' }, { type: 'pause', element: 'heading' }, { type: 'visibility', elements: ['heading'], value: 'show' }], tooltip: 'heading' });
  screen = setScreenLogic(screen, { keybinds: [{ key: 'Q', actions: [{ type: 'hide', elements: ['heading'] }] }], timers: [{ every: '1s', actions: [{ type: 'offset', element: 'heading', dx: '4' }] }] });
  const renamed = renameElement(screen, 'heading', 'titulo');
  assert.ok(renamed.elements.some(e => e.id === 'titulo') && !renamed.elements.some(e => e.id === 'heading'));
  const json = JSON.stringify(renamed);
  assert.ok(!json.includes('"heading"') && !json.includes('heading,'), json);
  assert.match(json, /"value":"titulo,close"/);
  assert.match(json, /"visibility","elements":\["titulo"\],"value":"show"/, 'a visibility mode is not an id');
  assert.throws(() => renameElement(screen, 'heading', 'start'), /already called start/);
  assert.throws(() => renameElement(screen, 'heading', 'Bad Id'), /a-z, 0-9/);
  assert.deepEqual(renameInLogic({ type: 'message', value: 'heading' }, new Map([['heading', 'x']])), { type: 'message', value: 'heading' }, 'text is not an id');
});
test('the project map finds links, entries, lost pages and missing targets', () => {
  const pages = ['tienda', 'perfil', 'ajustes', 'confirmar'].map(id => ({ screen: pageFromTemplate(id) }));
  pages[0].screen = setElementLogic(pages[0].screen, 'cerrar', { actions: [{ type: 'redirect', value: 'perfil' }] });
  pages[1].screen = setElementLogic(pages[1].screen, 'volver', { actions: [{ type: 'condition', value: '1 == 1', then: [{ type: 'redirect', value: 'nadie' }] }] });
  pages[2].screen = setScreenLogic(pages[2].screen, { command: 'ajustes' });
  pages[3].screen = setScreenLogic(pages[3].screen, { timers: [{ every: '5s', actions: [{ type: 'open', value: 'tienda' }] }] });
  const graph = projectGraph({ pages });
  assert.deepEqual(graph.links.map(l => `${l.from}>${l.to}:${l.via.join('+')}`), ['tienda>perfil:cerrar', 'perfil>nadie:volver', 'confirmar>tienda:página']);
  assert.deepEqual(graph.missing, ['nadie']);
  assert.deepEqual(graph.pages.map(p => `${p.id}:${p.entry ? 'entry' : p.reached ? 'reached' : 'lost'}`), ['tienda:entry', 'perfil:reached', 'ajustes:entry', 'confirmar:lost']);
  const hud = projectGraph({ pages: [pages[0], { screen: pageFromTemplate('hud_vida') }] });
  assert.equal(hud.pages[1].reached, true, 'a HUD page is opened by command or API, not from a button');
  const { svg } = mapSvg({ pages });
  for (const id of ['tienda', 'perfil', 'ajustes', 'confirmar']) assert.match(svg, new RegExp(`data-page="${id}"`));
  assert.match(svg, /map-node-missing/);
  assert.match(svg, /no se llega/);
});
test('copy and paste style carries the look, never the content', () => {
  let screen = base();
  screen = { ...screen, elements: screen.elements.map(e => e.id === 'start' ? { ...e, background: true, shape: 'rounded', style: { ...e.style, fillColor: '#123456', textColor: '#abcdef', radius: .3 } } : e) };
  const look = copyStyle(screen.elements.find(e => e.id === 'start'));
  const pasted = pasteStyle(screen, ['heading', 'close'], look);
  for (const id of ['heading', 'close']) {
    const e = pasted.elements.find(entry => entry.id === id), before = screen.elements.find(entry => entry.id === id);
    assert.equal(e.style.fillColor, '#123456'); assert.equal(e.shape, 'rounded'); assert.equal(e.background, true);
    assert.equal(e.text, before.text); assert.equal(e.x, before.x); assert.equal(e.action, before.action);
  }
  assert.throws(() => pasteStyle(screen, ['heading'], null), /Copy a style first/);
  const old = createScreen(), upgraded = pasteStyle(old, ['heading'], copyStyle(base().elements[0]));
  assert.equal(upgraded.version, 9, 'a look with newer fields upgrades an older page');
});
test('every icon is a 16 x 16 outlined picture and goes in as a 32-pixel image', () => {
  assert.ok(ICONS.length >= 25);
  for (const icon of ICONS) {
    const pixels = iconPixels(icon.id);
    assert.equal(pixels.length, 16); assert.ok(pixels.every(row => row.length === 16));
    const colours = new Set(pixels.flat().filter(Boolean));
    assert.ok(colours.has('#16121d'), `${icon.id} has its outline`);
    assert.ok(colours.size >= 3, `${icon.id} is shaded`);
    assert.equal(pixels[0].every(Boolean) || pixels[15].every(Boolean), false, `${icon.id} stays inside its square`);
  }
  assert.deepEqual(iconPixels('flecha_izquierda')[8], [...iconPixels('flecha_derecha')[7]].reverse(), 'arrows are one drawing turned');
  const { width, data } = iconRgba('moneda', 8);
  assert.equal(width, 128); assert.equal(data.length, 128 * 128 * 4);
  const result = insertIcon(base(), 'moneda', 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPgUbLwAwABWQC1RssqBwAAAABJRU5ErkJggg==');
  const e = result.screen.elements.find(entry => entry.id === result.id);
  assert.equal(result.id, 'icono_moneda'); assert.equal(e.kind, 'decoration'); assert.equal(Math.round(e.width * 128), 32); assert.equal(Math.round(e.height * 72), 32);
  const second = insertIcon(result.screen, 'moneda', e.image), placed = second.screen.elements.find(entry => entry.id === second.id);
  assert.equal(second.id, 'icono_moneda_2');
  assert.notDeepEqual([placed.x, placed.y], [e.x, e.y], 'a second icon does not land on the first');
});
test('HUD templates are passive pages that fit and stay in the safe area', () => {
  for (const id of ['hud_vida', 'hud_coordenadas', 'hud_dinero', 'hud_objetivo']) {
    assert.ok(PAGE_TEMPLATES.some(t => t.id === id));
    const screen = pageFromTemplate(id);
    assert.equal(screen.presentation.mode, 'overlay');
    assert.ok(screen.elements.every(e => e.kind !== 'button'), 'a HUD has no buttons');
    assert.deepEqual(reviewScreen(screen), [], id);
  }
  assert.equal(pageFromTemplate('hud_objetivo').logic.variables['*progreso'], '3');
});
test('the confirmation and tabs components work in Probar', () => {
  const confirm = addTemplate(base(), 'confirm-box');
  assert.deepEqual(reviewScreen(confirm.screen), []);
  let session = startPlay({ pages: [{ screen: confirm.screen }] }, 'menu', { events: [] });
  const shown = id => !session.view.hidden.has(id);
  assert.equal(shown('own_confirm_panel'), false);
  click(session, 'own_confirm_open');
  assert.equal(shown('own_confirm_panel'), true);
  click(session, 'own_confirm_yes');
  assert.equal(shown('own_confirm_panel'), false);
  assert.deepEqual(session.events, ['message <green>¡Hecho!']);
  const tabs = addTemplate(base(), 'tabs');
  validateScreen(tabs.screen);
  session = startPlay({ pages: [{ screen: tabs.screen }] }, 'menu');
  click(session, 'own_tab_3');
  assert.deepEqual(['own_tab_content_1', 'own_tab_content_2', 'own_tab_content_3'].map(id => !session.view.hidden.has(id)), [false, false, true]);
  assert.equal(session.view.offsets.get('own_tab_marker'), 113, 'the marker slides under the third tab');
});
test('the variables panel changes the run as an action would', () => {
  let screen = setScreenLogic(base(), { variables: { coins: '5', '*gems': '1' }, 'on-change': { coins: [{ type: 'message', value: '{old}>{new}' }] } });
  screen = setElementLogic(screen, 'start', { actions: [{ type: 'condition', value: '%luckperms_primary_group_name% == vip', then: [{ type: 'message', value: 'VIP {balance}' }], else: [{ type: 'message', value: 'normal' }] }] });
  const session = startPlay({ pages: [{ screen }] }, 'menu', { events: [] });
  assert.deepEqual(variables(session).map(v => `${v.global ? '*' : ''}${v.name}=${v.value}`), ['coins=5', '*gems=1']);
  setVariableFromPanel(session, 'coins', '9');
  assert.deepEqual(session.events, ['message 5>9'], 'on-change runs');
  setSample(session, 'health', '7'); setSample(session, 'balance', '99.5');
  assert.equal(resolve(session, '{health} {balance}'), '7 99.5');
  assert.equal(setPlaceholders(session, '%luckperms_primary_group_name% = vip\nnot a line\n%x% = 2'), 2);
  click(session, 'start');
  assert.equal(session.events.at(-1), 'message VIP 99.5');
  assert.equal(resolve(session, '{%x% * 3}'), '6', 'placeholders work in calculated text');
  assert.equal(condition(session, '%x% >= 2'), true);
  assert.throws(() => setVariableFromPanel(session, 'Bad Name', '1'), /Variable names/);
  assert.throws(() => setSample(session, 'balance', 'mucho'), /must be a number/);
});
