import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, setElementLogic, setScreenLogic } from './model.mjs';
import { startPlay, click, hover, key, shown, listRows, resolve, test as condition, transition, page } from './play.mjs';
import { motionKeyframes } from './play-ui.mjs';

// Two pages: "menu" (heading, start, close) and "shop".
function project(edit = screen => screen, shop = screen => screen) {
  const menu = edit(createScreen());
  const second = shop({ ...createScreen(), id: 'shop', title: 'SHOP' });
  return { pages: [{ screen: menu }, { screen: second }] };
}
const el = (session, id) => page(session).elements.find(e => e.id === id);
const texts = session => session.log.map(entry => entry.text);

test('the schema 1-8 single actions: open a page, back through history, close, messages', () => {
  const p = project(s => ({ ...s, elements: s.elements.map(e => e.id === 'start' ? { ...e, action: 'open:shop' } : e) }),
    s => ({ ...s, elements: s.elements.map(e => e.id === 'start' ? { ...e, action: 'message:Hola {player}' } : e) }));
  const run = startPlay(p, 'menu');
  assert.equal(click(run, 'start'), 'shop');
  assert.equal(run.transition.phase, 'open', 'the new page plays its entrance, not the old page its exit');
  click(run, 'start'); assert.deepEqual(texts(run).at(-1), 'Chat: Hola Steve');
  click(run, 'close'); assert.equal(run.closed, true); assert.equal(run.transition.phase, 'close');
  assert.equal(click(run, 'start'), 'shop', 'a closed menu ignores clicks');
});

test('actions: redirect and back keep a history; back with none closes', () => {
  let s = setElementLogic(createScreen(), 'start', { actions: [{ type: 'redirect', value: 'shop' }] });
  const p = project(() => s, shop => setElementLogic(shop, 'start', { actions: [{ type: 'back' }] }));
  const run = startPlay(p, 'menu');
  click(run, 'start'); assert.equal(run.pageId, 'shop');
  click(run, 'start'); assert.equal(run.pageId, 'menu');
  s = setElementLogic(createScreen(), 'start', { actions: [{ type: 'back' }] });
  const alone = startPlay(project(() => s), 'menu'); click(alone, 'start');
  assert.equal(alone.closed, true);
});

test('visibility, variables, conditions and functions follow the plugin rules', () => {
  const p = project(s => {
    s = setScreenLogic(s, { variables: { coins: '90', '*total': '5' }, functions: { pay: { params: { amount: '1' }, actions: [{ type: 'variable', variable: 'coins', operation: 'add', value: '{amount}' }] } } });
    s = setElementLogic(s, 'heading', { hidden: true });
    return setElementLogic(s, 'start', { actions: [
      { type: 'toggle', elements: ['heading'] },
      { type: 'function', function: 'pay', arguments: { amount: '15' } },
      { type: 'condition', value: '{coins} >= 100 && {total} = 5', then: [{ type: 'message', value: 'rico' }], else: [{ type: 'message', value: 'pobre' }] }
    ] });
  });
  const run = startPlay(p, 'menu');
  assert.equal(shown(run, el(run, 'heading')), false, 'hidden starts hidden');
  click(run, 'start');
  assert.equal(shown(run, el(run, 'heading')), true);
  assert.equal(resolve(run, '{coins}'), '105');
  assert.equal(texts(run).at(-1), 'Chat: rico');
  assert.equal(condition(run, '{coins} > 200 || {player} == Steve'), true);
  assert.equal(condition(run, '{coins} < abc'), false, 'ordering needs numbers on both sides');
});

test('permission branches follow the Probar choice, and the run says what it assumed', () => {
  const p = project(s => setElementLogic(s, 'start', { actions: [{ type: 'permission', permission: 'tf.vip', then: [{ type: 'message', value: 'si' }], else: [{ type: 'message', value: 'no' }] }] }));
  const yes = startPlay(p, 'menu'); click(yes, 'start'); assert.equal(texts(yes).at(-1), 'Chat: si');
  const no = startPlay(p, 'menu', { permissions: false }); click(no, 'start'); assert.equal(texts(no).at(-1), 'Chat: no');
  assert.match(texts(no).at(-2), /tf\.vip: no lo tiene/);
});

test('game-only actions are logged, never pretended', () => {
  const p = project(s => setElementLogic(s, 'start', { actions: [{ type: 'command', value: '/spawn' }, { type: 'sound', value: 'UI_BUTTON_CLICK 1 1' }, { type: 'give', value: 'DIAMOND 1' }, { type: 'delay', value: '500ms' }, { type: 'teleport', value: 'world 0 80 0' }] }));
  const run = startPlay(p, 'menu'); click(run, 'start');
  assert.deepEqual(texts(run).slice(-5).map(t => t.split(':')[0]), ['Comando del jugador', 'Sonido', 'Daría', 'Esperaría 500ms (Probar sigue sin esperar).', 'Teletransporte']);
});

test('hover shows the tooltip and runs on-hover and on-leave', () => {
  const p = project(s => setElementLogic(s, 'start', { tooltip: 'heading', 'on-hover': [{ type: 'message', value: 'in' }], 'on-leave': [{ type: 'message', value: 'out' }] }));
  const run = startPlay(p, 'menu');
  assert.equal(shown(run, el(run, 'heading')), false, 'a tooltip starts hidden');
  hover(run, 'start'); assert.equal(shown(run, el(run, 'heading')), true); assert.equal(texts(run).at(-1), 'Chat: in');
  hover(run, null); assert.equal(shown(run, el(run, 'heading')), false); assert.equal(texts(run).at(-1), 'Chat: out');
});

test('keys: bindings run their actions; Shift closes unless the page turned that off', () => {
  const p = project(s => setScreenLogic(s, { keybinds: [{ key: 'Q', actions: [{ type: 'message', value: 'q' }] }] }));
  const run = startPlay(p, 'menu'); key(run, 'Q'); assert.equal(texts(run).at(-1), 'Chat: q');
  key(run, 'SNEAK'); assert.equal(run.closed, true);
  const stays = startPlay(project(s => setScreenLogic(s, { behavior: { 'close-on-sneak': false } })), 'menu');
  key(stays, 'SNEAK'); assert.equal(stays.closed, false);
});

test('lists draw rows from a variable, filter them and select with {item}', () => {
  const p = project(s => {
    s = setScreenLogic(s, { variables: { servers: 'Survival,Skyblock,Creative,Skywars', q: '' } });
    return setElementLogic(s, 'heading', { list: { source: 'servers', rows: 2, spacing: 16, filter: 'q' } });
  });
  const run = startPlay(p, 'menu');
  assert.deepEqual(listRows(run, el(run, 'heading')).items.map(r => r.item), ['Survival', 'Skyblock']);
  run.locals.set('q', 'sky');
  assert.deepEqual(listRows(run, el(run, 'heading')).items.map(r => r.item), ['Skyblock', 'Skywars']);
  run.view.scroll.set('heading', 5);
  assert.deepEqual(listRows(run, el(run, 'heading')).items.map(r => r.index), [1, 2], 'scrolling stops at the last full page');
});

test('visible-if, offset, tint and the animation action change the view', () => {
  const p = project(s => {
    s = setScreenLogic(s, { variables: { coins: '0' } });
    s = setElementLogic(s, 'heading', { 'visible-if': '{coins} >= 1', keyframes: { trigger: 'action', steps: [{ at: 0 }, { at: 300, scale: 2 }] } });
    return setElementLogic(s, 'start', { actions: [{ type: 'variable', variable: 'coins', operation: 'set', value: '1' }, { type: 'offset', element: 'heading', dx: '24' }, { type: 'tint', element: 'heading', value: '#FF0000' }, { type: 'animation', element: 'heading' }] });
  });
  const run = startPlay(p, 'menu');
  assert.equal(shown(run, el(run, 'heading')), false);
  click(run, 'start', { now: 1000 });
  assert.equal(shown(run, el(run, 'heading')), true);
  assert.equal(run.view.offsets.get('heading'), 24); assert.equal(run.view.tints.get('heading'), '#ff0000');
  assert.deepEqual(run.view.plays.get('heading'), { at: 1000, mode: 'action' });
});

test('unknown pages and action types are reported, not fatal', () => {
  const p = project(s => setElementLogic(s, 'start', { actions: [{ type: 'redirect', value: 'nowhere' }] }));
  const run = startPlay(p, 'menu'); click(run, 'start');
  assert.match(texts(run).at(-1), /No hay una página "nowhere"/);
});

test('page transitions read the page animation as the plugin does', () => {
  const slide = setScreenLogic(createScreen(), { animation: { open: { effect: 'slide', from: 'right', distance: 64, 'duration-ms': 350 }, close: { effect: 'slide', from: 'right' } } });
  assert.deepEqual(transition(slide, 'open'), { effect: 'slide', duration: 350, easing: 'ease-out', from: 'right', distance: 64 });
  assert.equal(transition(createScreen(), 'open').effect, 'fade', 'default entrance is the native fade');
  assert.equal(transition(createScreen(), 'close').effect, 'none', 'no close object: no exit');
  assert.equal(transition(setScreenLogic(createScreen(), { animation: { close: {} } }), 'close').effect, 'fade', 'an empty close object fades');
});

test('keyframes carry values forward and keep y growing downwards', () => {
  const { move, fade } = motionKeyframes({ easing: 'linear', steps: [{ at: 0, x: -24, opacity: 0 }, { at: 150, scale: 2 }, { at: 300, x: 0, opacity: 1, y: 10 }] }, 2, .5);
  assert.deepEqual(move.map(k => k.translate), ['-48px 0px', '-48px 0px', '0px 20px']);
  assert.deepEqual(move.map(k => k.scale), ['1', '2', '2']);
  assert.deepEqual(fade.map(k => k.opacity), ['0', '0', '0.5'], 'opacity multiplies the element opacity');
  assert.deepEqual(move.map(k => k.offset), [0, .5, 1]);
});

test('keyboard navigation: W A S D focus the nearest button that way and Space clicks it, as in the game', () => {
  const shop = s => setScreenLogic({ ...s, elements: [
    { ...s.elements[1], id: 'a', x: -.5, y: .5, width: .3, height: .2, action: 'message:a' },
    { ...s.elements[1], id: 'b', x: .5, y: .5, width: .3, height: .2, action: 'message:b' },
    { ...s.elements[1], id: 'buy', x: 0, y: -.7, width: 1.2, height: .2, action: 'message:buy' }] },
    { behavior: { 'keyboard-navigation': true }, keybinds: [{ key: 'A', actions: [{ type: 'message', value: 'bound' }] }] });
  const run = startPlay(project(shop), 'menu');
  key(run, 'S'); assert.equal(run.view.hover, 'buy', 'the first press enters the nearest button that way');
  key(run, 'W'); assert.equal(run.view.hover, 'a', 'a tie goes to the first button');
  key(run, 'D'); assert.equal(run.view.hover, 'b');
  key(run, 'D'); assert.equal(run.view.hover, 'b', 'nothing further right: the focus stays');
  key(run, 'SPACE'); assert.equal(texts(run).at(-1), 'Chat: b');
  key(run, 'A'); assert.equal(texts(run).at(-1), 'Chat: bound', 'a bound key keeps its own action');
  assert.equal(run.view.hover, 'b');
  const off = startPlay(project(s => setScreenLogic(shop(s), { behavior: {} })), 'menu');
  key(off, 'S'); assert.equal(off.view.hover ?? null, null, 'off by default');
});
