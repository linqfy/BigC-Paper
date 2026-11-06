import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, setElementLogic, setScreenLogic, upgradeLogic } from './model.mjs';
import { textLines, textBlock, box, reviewScreen, reviewProject } from './checker.mjs';

const edit = (screen, id, change) => ({ ...screen, elements: screen.elements.map(e => e.id === id ? { ...e, ...change } : e) });
const messages = found => found.map(f => `${f.level} ${f.element ?? '-'}: ${f.message}`);

test('text is measured like the plugin: 6 pixels per character, character wrapping, 8-pixel lines', () => {
  assert.deepEqual(textLines('ABCDEFGHIJ', 23, 1), ['ABCD', 'EFGH', 'IJ'], '(23 + 1) / 6 = 4 columns');
  assert.deepEqual(textLines('A\n\nB'), ['A', '', 'B'], 'empty lines are kept');
  assert.deepEqual(textBlock('HOLA'), { width: 23, height: 7, lines: 1 });
  assert.deepEqual(textBlock('HOLA', { textScale: 2 }), { width: 46, height: 14, lines: 1 });
  assert.deepEqual(textBlock('AB\nCD'), { width: 11, height: 15, lines: 2 });
  assert.deepEqual(box({ x: 0, y: 0, width: .5, height: .2 }), { width: 64, height: 14, left: 96, top: 65, right: 160, bottom: 79 });
});

test('a long text in a small box is reported; live text and the built-in menu are not', () => {
  const clean = createScreen();
  assert.deepEqual(reviewScreen(clean).filter(f => f.level === 'warning'), [], 'the default page has nothing to fix');
  const long = edit(clean, 'start', { text: 'EMPEZAR LA PARTIDA AHORA MISMO' });
  assert.match(messages(reviewScreen(long)).join('\n'), /El texto de start no entra en su caja: ocupa 179 × 7 px/);
  const live = edit(clean, 'start', { text: 'HOLA {player} {player} {player} {player}' });
  assert.ok(!messages(reviewScreen(live)).some(m => m.includes('no entra')), 'cannot be measured before the game fills it');
});

test('schema 9 text that shrinks to fit, a name too long even at half, and a frame past the safe area', () => {
  const clean = upgradeLogic(createScreen());
  const shrinks = messages(reviewScreen(edit(clean, 'start', { text: 'EMPEZAR LA PARTIDA AHORA MISMO' }))).join('\n');
  assert.match(shrinks, /El texto de start no entra en su caja a su tamaño: en el juego se ve a (3\/4|1\/2) de su tamaño/);
  const cut = messages(reviewScreen(edit(clean, 'start', { text: 'X'.repeat(80) }))).join('\n');
  assert.match(cut, /ni a la mitad de su tamaño: en el juego se corta con "…"/);
  const name = messages(reviewScreen(edit(clean, 'start', { text: 'BIENVENIDO DE NUEVO {player} {player}', width: .3 }))).join('\n');
  assert.match(name, /start puede mostrar hasta \d+ letras/);
  const framed = { ...clean, elements: [...clean.elements, { id: 'marco', kind: 'decoration', text: '', x: -.95, y: 0, width: .2, height: .4, border: true, background: false, image: null, shape: 'rectangle', action: '', style: { ...clean.elements[0].style, outlineWidth: 1 }, layer: 0 }] };
  assert.match(messages(reviewScreen(framed)).join('\n'), /El marco de marco sale de la zona segura/);
});

test('buttons that do nothing, hidden elements nothing shows, and missing animations are warned', () => {
  let screen = edit(createScreen(), 'start', { action: '' });
  screen = setElementLogic(screen, 'heading', { hidden: true });
  screen = setElementLogic(screen, 'close', { frames: { folder: 'fuego' } });
  const found = messages(reviewScreen(screen, { frames: new Set(['estrella']) })).join('\n');
  assert.match(found, /El botón start no hace nada/);
  assert.match(found, /heading empieza oculto y ninguna acción/);
  assert.match(found, /La animación "fuego" de close no está/);
  const fixed = setElementLogic(setElementLogic(screen, 'start', { actions: [{ type: 'toggle', elements: ['heading'] }] }), 'close', { frames: { folder: 'estrella' } });
  const after = messages(reviewScreen(fixed, { frames: new Set(['estrella']) })).join('\n');
  assert.doesNotMatch(after, /no hace nada|empieza oculto|no está en plugins/);
  assert.doesNotMatch(messages(reviewScreen(screen)).join('\n'), /animación/, 'without the server listing animations are not judged');
});

test('pages nothing opens and links to pages outside the project are noted', () => {
  const menu = edit(createScreen(), 'start', { action: 'open:tienda' });
  const shop = { ...createScreen(), id: 'tienda', title: 'TIENDA' }, lost = { ...createScreen(), id: 'perdida', title: 'PERDIDA' };
  const found = messages(reviewProject({ pages: [{ screen: menu }, { screen: shop }, { screen: lost }] }));
  assert.ok(found.some(m => m.includes('La página perdida no se abre desde ninguna otra')));
  assert.ok(!found.some(m => m.includes('La página tienda no se abre')), 'tienda is linked');
  const withCommand = setScreenLogic(lost, { command: 'perdida' });
  assert.ok(!messages(reviewProject({ pages: [{ screen: menu }, { screen: shop }, { screen: withCommand }] })).some(m => m.includes('perdida no se abre')));
  const outside = messages(reviewScreen(edit(createScreen(), 'start', { action: 'open:vip' }), { pages: ['menu'] })).join('\n');
  assert.match(outside, /abre la página "vip", que no está en este proyecto/);
});

test('elements outside the safe area are noted, full-screen art is not', () => {
  const edge = edit(createScreen(), 'start', { x: .9, width: .4 });
  assert.ok(messages(reviewScreen(edge)).some(m => m.includes('start sale de la zona segura')));
  const art = { ...createScreen(), elements: [...createScreen().elements, { ...createScreen().elements[0], id: 'fondo', kind: 'decoration', text: '', x: 0, y: 0, width: 2, height: 2 }] };
  assert.ok(!messages(reviewScreen(art)).some(m => m.includes('fondo sale')));
});

test('item icons: a missing item is warned; blocks and tinted items are noted, as the game will draw them', async () => {
  const { upgradeLogic, createScreen } = await import('./model.mjs');
  const screen = upgradeLogic(createScreen());
  const icon = (id, item) => ({ ...screen.elements[0], id, text: '', kind: 'decoration', action: '', x: 0, y: -.5, width: .125, height: .2222, item });
  screen.elements.push(icon('gem', 'diamond'), icon('ghost', 'no_such_item'), icon('stone_icon', 'stone'), icon('helmet', 'leather_helmet'));
  const icons = { diamond: { block: false, tinted: false }, stone: { block: true, tinted: false }, leather_helmet: { block: false, tinted: true } };
  const found = reviewScreen(screen, { icons }).filter(f => ['gem', 'ghost', 'stone_icon', 'helmet'].includes(f.element));
  assert.deepEqual(found.map(f => [f.element, f.level]), [['ghost', 'warning'], ['stone_icon', 'info'], ['helmet', 'info']]);
  assert.equal(reviewScreen(screen).filter(f => f.element === 'ghost').length, 0, 'without the table nothing is guessed');
});
