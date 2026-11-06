import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, validateScreen, serializeScreen, parseScreen, SCREEN_PRESETS, setScreenLogic, mergeLogic } from './model.mjs';
import { addTemplate } from './templates.mjs';

test('the schema 9 components keep their links after renaming and validate', () => {
  const popup = addTemplate(addTemplate(createScreen(), 'popup').screen, 'popup');
  const opener = popup.screen.elements.find(e => e.id === popup.ids[0]);
  assert.deepEqual(opener.logic.actions[0].elements, popup.ids.slice(1), 'the second popup toggles its own panel and text');
  const tip = addTemplate(createScreen(), 'tooltip-button');
  assert.equal(tip.screen.elements.find(e => e.id === tip.ids[0]).logic.tooltip, tip.ids[1]);
  for (const key of ['glass-panel', 'glow-button', 'popup', 'tooltip-button', 'data-list', 'gif']) {
    const result = addTemplate(createScreen(), key);
    assert.equal(result.screen.version, 9, key);
    validateScreen(parseScreen(serializeScreen(result.screen)));
  }
  assert.equal(addTemplate(createScreen(), 'glass-panel').screen.elements.find(e => e.id === 'own_glass').style.backdropBlur, 6);
});

test('controls keep their value in a page variable of their own and work in Probar', async () => {
  const { startPlay, click, resolve } = await import('./play.mjs');
  const { reviewScreen } = await import('./checker.mjs');
  const first = addTemplate(createScreen(), 'switch'), second = addTemplate(first.screen, 'switch');
  assert.notEqual(first.variable, second.variable, 'two switches never share a value');
  assert.deepEqual(Object.keys(second.screen.logic.variables).sort(), [`*${first.variable}`, `*${second.variable}`].sort());
  let screen = second.screen;
  for (const key of ['checkbox', 'stepper', 'cycle']) screen = addTemplate(screen, key).screen;
  validateScreen(parseScreen(serializeScreen(screen)));
  assert.deepEqual(reviewScreen(screen).filter(f => f.level === 'warning').map(f => f.message), []);
  const run = startPlay({ pages: [{ screen }] }, 'menu');
  const switchVar = first.variable;
  assert.equal(resolve(run, `{${switchVar}}`), 'Sí');
  click(run, first.ids[1]); assert.equal(resolve(run, `{${switchVar}}`), 'No');
  click(run, 'own_checkbox'); assert.equal(resolve(run, '{v_checkbox}'), '[✔]');
  for (let i = 0; i < 8; i++) click(run, 'own_stepper_more');
  assert.equal(resolve(run, '{v_stepper}'), '10', 'stops at 10');
  for (let i = 0; i < 12; i++) click(run, 'own_stepper_less');
  assert.equal(resolve(run, '{v_stepper}'), '0', 'stops at 0');
  for (const expected of ['Difícil', 'Fácil', 'Normal']) { click(run, 'own_cycle'); assert.equal(resolve(run, '{v_cycle}'), expected); }
});

test('the slide in and out preset uses the 0.23 closing slide', () => {
  const screen = setScreenLogic(createScreen(), mergeLogic(null, SCREEN_PRESETS['slide-in-out']));
  assert.deepEqual(screen.logic.animation.close, { effect: 'slide', from: 'right', distance: 96, 'duration-ms': 350, easing: 'ease-in' });
  validateScreen(parseScreen(serializeScreen(screen)));
});
