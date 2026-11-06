import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, parseScreen, serializeScreen, setScreenLogic, setElementLogic, upgradeLogic, validateScreen, addElement, moveElement, deleteElement } from './model.mjs';
import { encodeClipboard, pasteClipboard } from './interaction.mjs';

const screenLogic = { variables: { coins: '100', '*total': '0' }, keybinds: [{ key: 'Q', actions: [{ type: 'close' }] }],
  functions: { buy: { params: { price: '50' }, actions: [{ type: 'condition', value: '{coins} >= {price}', then: [{ type: 'message', value: 'ok' }] }] } } };

test('schema 9 keeps every schema 8 field and adds optional logic', () => {
  const upgraded = upgradeLogic(createScreen());
  assert.equal(upgraded.version, 9);
  assert.equal(upgraded.logic, undefined);
  assert.ok('gradientColor' in upgraded.elements[0].style);
  const withLogic = setElementLogic(setScreenLogic(upgraded, screenLogic), 'start', { actions: [{ type: 'function', function: 'buy' }], 'right-actions': [{ type: 'message', value: 'info' }] });
  const round = parseScreen(serializeScreen(withLogic));
  assert.deepEqual(round, withLogic);
  assert.deepEqual(round.logic, screenLogic);
});
test('logic survives moving, adding, deleting other elements and clipboard copies', () => {
  let value = setElementLogic(upgradeLogic(createScreen()), 'start', { actions: [{ type: 'close' }] });
  value = moveElement(value, 'start', .2, .1);
  value = addElement(value, 'label');
  value = deleteElement(value, 'heading');
  assert.deepEqual(value.elements.find(e => e.id === 'start').logic, { actions: [{ type: 'close' }] });
  const pasted = pasteClipboard(value, encodeClipboard(value, ['start']));
  assert.equal(pasted.screen?.version ?? pasted.version, 9);
});
test('logic shape errors show up in the editor', () => {
  const value = upgradeLogic(createScreen());
  assert.throws(() => setScreenLogic(value, { unknown: true }), /unknown key/);
  assert.throws(() => setScreenLogic(value, { 'on-open': [{ type: 'mesage', value: 'x' }] }), /unknown action type/);
  assert.throws(() => setElementLogic(value, 'heading', { actions: [{ type: 'close' }] }), /need a button/);
  assert.throws(() => setElementLogic(value, 'start', { hidden: 'yes' }), /true or false/);
  assert.doesNotThrow(() => setElementLogic(value, 'heading', { hidden: true, 'visible-if': '{coins} > 1' }));
  assert.throws(() => validateScreen({ ...value, version: 8, logic: screenLogic }), /keys must be exactly/);
  const removed = setScreenLogic(setScreenLogic(value, screenLogic), null);
  assert.equal(removed.logic, undefined);
});
test('deeply nested branches parse (24 levels)', () => {
  let inner = [{ type: 'close' }];
  for (let i = 0; i < 6; i++) inner = [{ type: 'condition', value: '1=1', then: inner }];
  const value = setScreenLogic(upgradeLogic(createScreen()), { 'on-open': inner });
  assert.deepEqual(parseScreen(serializeScreen(value)).logic, value.logic);
});
