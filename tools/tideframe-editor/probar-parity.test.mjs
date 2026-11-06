// Probar follows the plugin where Codex's review found it did not (0.23): dialogs on open, function names and
// arguments, input rules, money in exact cents, list data and page permissions.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, upgradeLogic, setScreenLogic, setElementLogic } from './model.mjs';
import { startPlay, click, listRows, page } from './play.mjs';

const base = () => upgradeLogic(createScreen());
const play = (screens, options = {}) => startPlay({ pages: screens.map(screen => ({ screen })) }, screens[0].id, { now: 0, events: [], ...options });
const messages = session => session.events.filter(e => e.startsWith('message ')).map(e => e.slice(8));

test('a dialog in on-open is asked, not cancelled', () => {
  const page = setScreenLogic(base(), { 'on-open': [{ type: 'input', id: 'name', 'on-submit': [{ type: 'message', value: 'hi {name}' }], 'on-cancel': [{ type: 'message', value: 'no' }] },
    { type: 'confirm', value: 'Sure?', then: [{ type: 'message', value: 'yes' }], else: [{ type: 'message', value: 'nope' }] }] });
  const session = play([page], { ask: () => 'Ana', confirm: () => true });
  assert.deepEqual(messages(session), ['hi Ana', 'yes']);
});

test('functions are found by their exact name and take every argument given', () => {
  const page = setElementLogic(setScreenLogic(base(), { functions: { buy_item: { actions: [{ type: 'message', value: '{item} for {price}' }] } } }),
    'start', { actions: [{ type: 'function', function: 'buy_item', arguments: { item: 'Sword' }, }, { type: 'function', function: 'buy_item', args: { item: 'Bow', price: '9' } }] });
  const session = play([page]);
  click(session, 'start');
  assert.deepEqual(messages(session), ['Sword for {price}', 'Bow for 9']);
});

test('an answer that breaks the input rules counts as cancelled', () => {
  const page = setElementLogic(base(), 'start', { actions: [{ type: 'input', variable: 'code', filter: 'digits', 'max-length': 4, 'allow-empty': false,
    'on-submit': [{ type: 'message', value: 'ok {code}' }], 'on-cancel': [{ type: 'message', value: 'cancel' }] }] });
  for (const [typed, expected] of [['12', 'ok 12'], ['abc', 'cancel'], ['12345', 'cancel'], ['', 'cancel']]) {
    const session = play([page], { ask: () => typed });
    click(session, 'start');
    assert.deepEqual(messages(session), [expected], `"${typed}"`);
  }
});

test('money is counted in exact cents, cut to two decimals', () => {
  const page = setElementLogic(base(), 'start', { actions: [{ type: 'charge', amount: '0.29', then: [{ type: 'message', value: 'paid' }], else: [{ type: 'message', value: 'short' }] }] });
  const session = play([page]);
  session.balance = 0.28;
  click(session, 'start');
  assert.deepEqual(messages(session), ['short'], 'binary 0.29 * 100 used to round down to 28 cents and pass');
  session.balance = 0.29;
  click(session, 'start');
  assert.equal(session.balance, 0);
  const pay = setElementLogic(base(), 'start', { actions: [{ type: 'pay', amount: '1.999' }, { type: 'charge', amount: '0,5' }] });
  const paying = play([pay]);
  paying.balance = 0;
  click(paying, 'start');
  assert.equal(paying.balance, 1.99, 'cut, not rounded; "0,5" is not a number for the plugin');
});

test('list data splits on line feeds too, and a page the player may not open stays closed', () => {
  const list = setElementLogic(setScreenLogic(base(), { variables: { rows: 'A\nB,C' } }), 'start', { list: { source: 'rows', rows: 3 } });
  const session = play([list]);
  assert.deepEqual(listRows(session, page(session).elements.find(e => e.id === 'start')).items.map(row => row.item), ['A', 'B', 'C']);
  const locked = setScreenLogic({ ...base(), id: 'vip' }, { permission: 'shop.vip' });
  const door = setElementLogic(base(), 'start', { actions: [{ type: 'redirect', value: 'vip' }, { type: 'message', value: 'after' }] });
  const denied = play([door, locked], { permissions: false });
  click(denied, 'start');
  assert.equal(denied.pageId, door.id, 'the current page stays');
  assert.deepEqual(messages(denied), [], 'and the list ends there, as in the plugin');
});
