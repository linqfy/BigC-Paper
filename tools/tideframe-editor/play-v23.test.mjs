// 0.23 phase 1 in Probar and the editor: GIF playback, timers, damage, the GIF timeline, and the new page format.
import test from 'node:test';
import assert from 'node:assert/strict';
import { startPlay, click, gifTimeline, timers, tick, damage, changeWorld, cooldownMs, secondsLeft, resolve } from './play.mjs';
import { createScreen, upgradeLogic, setElementLogic, setScreenLogic, validateScreen } from './model.mjs';
import { writeSetting, appendAction, ACTION_RECIPES } from './advanced-settings.mjs';
import { SETTING_TEXT, RECIPE_TEXT, es } from './i18n.mjs';

function project(logic, elementLogic = {}) {
  let screen = upgradeLogic(createScreen());
  if (logic) screen = setScreenLogic(screen, logic);
  for (const [id, value] of Object.entries(elementLogic)) screen = setElementLogic(screen, id, value);
  return { pages: [{ screen }] };
}

test('pause, resume and frame record the playback the page shows', () => {
  const p = project(null, { heading: { frames: { folder: 'fire' } }, start: { actions: [{ type: 'pause', element: 'heading' }, { type: 'frame', element: 'heading', value: '{step}' }] }, close: { actions: [{ type: 'resume', value: 'heading' }, { type: 'pause', element: 'start' }] } });
  const session = startPlay(p, 'menu');
  session.locals.set('step', '3');
  click(session, 'start');
  assert.deepEqual(session.view.playback.get('heading'), { paused: true, seek: 3, version: 2 });
  click(session, 'close');
  assert.deepEqual(session.view.playback.get('heading'), { paused: false, seek: null, version: 3 });
  assert.match(session.log.at(-1).text, /start no es una animación PNG o GIF/);
});
test('timers, damage and world change follow the page', () => {
  const p = project({ timers: [{ every: '2s', actions: [{ type: 'message', value: 'tick' }] }],
    'on-damage': [{ type: 'message', value: 'hit {damage} {cause}' }], 'on-world-change': [{ type: 'message', value: '{from}>{world}' }], behavior: { 'close-on-damage': true } });
  // A page from elsewhere may carry a timer the plugin would refuse: Probar does not run it either.
  p.pages[0].screen.logic.timers.push({ every: '100ms', actions: [{ type: 'close' }] });
  const session = startPlay(p, 'menu', { events: [] });
  assert.deepEqual(timers(session).map(t => t.everyMs), [2000], 'a timer faster than 0.25 s is not run');
  tick(session, 0);
  changeWorld(session, 'nether');
  assert.equal(resolve(session, '{world}'), 'nether');
  damage(session, 1.005, 'lava');
  assert.deepEqual(session.events, ['message tick', 'message world>nether', 'message hit 1.01 lava', 'close'], '{damage} rounds half up to two decimals, like BigDecimal');
  assert.equal(session.closed, true, 'close-on-damage closes after on-damage');
});
test('the GIF timeline samples like GifFrames', () => {
  assert.deepEqual(gifTimeline([100, 100, 100]), { interval: 100, sources: [0, 1, 2] });
  assert.deepEqual(gifTimeline([50, 150]), { interval: 50, sources: [0, 1, 1, 1] }, 'uneven delays repeat frames on the shortest step');
  assert.deepEqual(gifTimeline([0, 10]), { interval: 100, sources: [0, 1] }, '0 and 10 ms play as 100 ms');
  assert.deepEqual(gifTimeline([70]), { interval: 50, sources: [0] });
  assert.deepEqual([gifTimeline(Array(200).fill(50)).interval, gifTimeline(Array(200).fill(50)).sources.length], [200, 50], 'too long for 64 frames: a longer step');
  assert.deepEqual(gifTimeline([100, 100], 200), { interval: 200, sources: [0] });
});
test('cooldown helpers read durations like the plugin', () => {
  assert.equal(cooldownMs('2s'), 2000);
  assert.equal(cooldownMs('500ms'), 500);
  assert.equal(cooldownMs('20t'), 1000);
  assert.equal(cooldownMs(40), 2000);
  assert.equal(cooldownMs('soon'), 0);
  assert.equal(secondsLeft(1500), '1.5');
  assert.equal(secondsLeft(2000), '2');
  assert.equal(secondsLeft(1), '0.1');
});
test('the editor validates the new keys before publishing', () => {
  const base = upgradeLogic(createScreen());
  assert.doesNotThrow(() => validateScreen(setElementLogic(base, 'start', { actions: [{ type: 'close' }], cooldown: '2s', 'on-cooldown': [{ type: 'message', value: 'wait' }] })));
  assert.throws(() => setElementLogic(base, 'start', { 'on-cooldown': [{ type: 'close' }] }), /on-cooldown needs a cooldown/);
  assert.throws(() => setElementLogic(base, 'start', { cooldown: '2 hours' }), /cooldown must be a duration/);
  assert.doesNotThrow(() => setScreenLogic(base, { timers: [{ every: '5s', actions: [{ type: 'close' }] }], 'on-damage': [{ type: 'close' }], 'on-world-change': [], 'on-change': { '*coins': [] } }));
  assert.throws(() => setScreenLogic(base, { timers: [{ every: '100ms', actions: [{ type: 'close' }] }] }), /every must be 0.25 to 3600/);
  assert.throws(() => setScreenLogic(base, { 'on-change': { coins: [], '*coins': [] } }), /invalid or repeated variable/);
  assert.doesNotThrow(() => setElementLogic(base, 'start', { actions: [{ type: 'confirm', value: 'Sure?', then: [{ type: 'frame', element: 'heading', value: '2' }] }] }));
  assert.match(es('start cooldown must be a duration like "2s" or "500ms", up to one hour.'), /espera entre clics/);
});
test('settings and recipes offer the new features in Spanish', () => {
  const base = upgradeLogic(createScreen());
  const withCooldown = writeSetting(base, 'element', 'cooldown', '1.5s', 'start');
  assert.equal(withCooldown.elements.find(e => e.id === 'start').logic.cooldown, '1.5s');
  assert.throws(() => writeSetting(base, 'element', 'cooldown', 'later', 'start'), /Wait between clicks/);
  const withTimer = writeSetting(base, 'screen', 'timers', [{ every: '5s', actions: [{ type: 'message', value: 'hi' }] }]);
  assert.equal(withTimer.logic.timers.length, 1);
  for (const id of ['confirm', 'pause', 'resume', 'frame']) {
    assert.ok(ACTION_RECIPES.some(recipe => recipe.id === id));
    assert.ok(RECIPE_TEXT[id]);
    assert.equal(appendAction([], id).at(-1).type, id);
  }
  for (const key of ['element:cooldown', 'element:on-cooldown', 'screen:timers', 'screen:on-damage', 'screen:on-world-change', 'screen:on-change']) assert.ok(SETTING_TEXT[key], key);
});
