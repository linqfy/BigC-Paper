import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, setElementLogic, validateScreen, serializeScreen, parseScreen } from './model.mjs';
import { ACTION_RECIPES, appendAction } from './advanced-settings.mjs';
import { startPlay, click, resolve } from './play.mjs';
import { RECIPE_TEXT } from './i18n.mjs';

const buy = (price, extra = []) => setElementLogic(createScreen(), 'start', { actions: [
  { type: 'charge', amount: String(price), then: [{ type: 'message', value: 'ok' }], else: [{ type: 'message', value: 'no' }] }, ...extra] });

test('the editor accepts charge, pay and server, with their branches', () => {
  const screen = setElementLogic(buy(10), 'close', { actions: [{ type: 'pay', amount: '5' }, { type: 'server', value: 'lobby' }, { type: 'deposit', value: '1' }, { type: 'connect', server: 'survival' }] });
  assert.doesNotThrow(() => validateScreen(parseScreen(serializeScreen(screen))));
  assert.throws(() => setElementLogic(createScreen(), 'start', { actions: [{ type: 'charge', amount: '5', then: [{ type: 'nope' }] }] }), /unknown action type nope/);
  for (const id of ['charge', 'pay', 'server']) {
    assert.ok(ACTION_RECIPES.some(r => r.id === id), id);
    assert.ok(RECIPE_TEXT[id], `${id} has a Spanish label`);
    assert.equal(appendAction('[]', id)[0].type, id);
  }
});

test('Probar charges a simulated balance only when it is enough, and pays into it', () => {
  const run = startPlay({ pages: [{ screen: buy(1000, [{ type: 'pay', amount: '0.5' }]) }] }, 'menu');
  assert.equal(resolve(run, '{balance}'), '1250', 'starts at the editor sample');
  click(run, 'start'); assert.equal(run.balance, 250.5);
  assert.deepEqual(run.log.map(e => e.text).slice(0, 3), ['Cobraría 1000 con Vault (saldo simulado: 250).', 'Chat: ok', 'Pagaría 0.5 con Vault (saldo simulado: 250.5).']);
  click(run, 'start');
  assert.equal(run.balance, 251, 'the second purchase took nothing; only the payment arrived');
  assert.match(run.log.at(-3).text, /No alcanza: cuesta 1000/);
  assert.equal(run.log.at(-2).text, 'Chat: no');
  assert.equal(resolve(run, '{balance}'), '251', 'texts follow the simulated balance');
});

test('Probar sends to another server by closing the menu and refuses bad names', () => {
  const screen = setElementLogic(createScreen(), 'start', { actions: [{ type: 'server', value: 'lobby-2' }, { type: 'message', value: 'never' }] });
  const run = startPlay({ pages: [{ screen }] }, 'menu');
  click(run, 'start');
  assert.equal(run.closed, true);
  assert.match(run.log[0].text, /servidor lobby-2/);
  assert.ok(!run.log.some(e => e.text === 'Chat: never'), 'the rest of the list does not run');
  const bad = startPlay({ pages: [{ screen: setElementLogic(createScreen(), 'start', { actions: [{ type: 'server', value: 'dos palabras' }] }) }] }, 'menu');
  click(bad, 'start');
  assert.equal(bad.closed, false); assert.match(bad.log[0].text, /no es un nombre de servidor/);
  const free = startPlay({ pages: [{ screen: buy(0) }] }, 'menu');
  click(free, 'start');
  assert.match(free.log[0].text, /Cobro inválido/); assert.equal(free.log[1].text, 'Chat: no'); assert.equal(free.balance, 1250);
});
