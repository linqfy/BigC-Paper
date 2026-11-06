// Calculated text and variable arithmetic in Probar: the same values as the plugin's ExpressionsTest and Variables.
import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluateExpression, renderExpressions, hasExpression, arithmetic, decimal, formatDecimal } from './expressions.mjs';

const VALUES = { coins: '120', price: '45', discount: '5', vip: 'true', name: 'Alex', zero: '0', word: 'abc', 'item.price': '7.5', '%vault_eco_balance%': '1250.5' };
const names = name => VALUES[name] ?? null;
const evaluate = source => evaluateExpression(source, names);
const render = text => renderExpressions(text, part => part, names);

test('numbers follow the plugin to the digit', () => {
  assert.equal(evaluate('coins * 2'), '240');
  assert.equal(evaluate('(price - discount) / 2'), '20');
  assert.equal(evaluate('1 + 2 * 3'), '7');
  assert.equal(evaluate('-coins + 5'), '-115');
  assert.equal(evaluate('coins % 12'), '0');
  assert.equal(evaluate('100 / 3'), '33.333333');
  assert.equal(evaluate('item.price * 2'), '15');
  assert.equal(evaluate('%vault_eco_balance% * 2'), '2501');
  assert.equal(evaluate('0.1 + 0.2'), '0.3', 'exact decimals, not binary floats');
  assert.equal(evaluate('2 / 3'), '0.666667', 'half up at six decimals');
  assert.equal(evaluate('1 / 8'), '0.125');
});
test('functions round and pick', () => {
  assert.equal(evaluate('round(100 / 3)'), '33');
  assert.equal(evaluate('round(100 / 3, 1)'), '33.3');
  assert.equal(evaluate('round(2.5)'), '3');
  assert.equal(evaluate('round(-2.5)'), '-3', 'half up rounds away from zero, like Java');
  assert.equal(evaluate('floor(3.9)'), '3');
  assert.equal(evaluate('floor(-3.1)'), '-4');
  assert.equal(evaluate('ceil(3.1)'), '4');
  assert.equal(evaluate('ceil(-3.9)'), '-3');
  assert.equal(evaluate('abs(0 - 5)'), '5');
  assert.equal(evaluate('min(coins, price, discount)'), '5');
  assert.equal(evaluate('max(coins, price)'), '120');
  assert.equal(evaluate('round(1, 7)'), null);
  assert.equal(evaluate('abs(1, 2)'), null);
});
test('text, comparisons and decisions', () => {
  assert.equal(evaluate('coins >= 100 ? "Enough" : "Missing"'), 'Enough');
  assert.equal(evaluate("price >= 100 ? 'Enough' : 'Missing'"), 'Missing');
  assert.equal(evaluate('vip ? "VIP" : "Normal"'), 'VIP');
  assert.equal(evaluate('"Hi " + name + "!"'), 'Hi Alex!');
  assert.equal(evaluate('"Level " + (12 + 1)'), 'Level 13');
  assert.equal(evaluate('name == "Alex" && coins > price'), 'true');
  assert.equal(evaluate('!vip'), 'false');
  assert.equal(evaluate('word > 3'), 'false');
  assert.equal(evaluate('zero ? "no" : "yes"'), 'yes');
  assert.equal(evaluate('zero > 0 ? coins / zero : "small"'), 'small');
});
test('anything that cannot be computed stays as written', () => {
  assert.equal(render('Total {coins / zero}'), 'Total {coins / zero}');
  assert.equal(render('{word * 2}'), '{word * 2}');
  assert.equal(render('{missing + 1}'), '{missing + 1}');
  assert.equal(render('{a b}'), '{a b}');
  assert.equal(render('{"key": 1}'), '{"key": 1}');
  assert.equal(render('{bar:health}'), '{bar:health}');
  assert.equal(render('{ SHOP }'), '{ SHOP }');
  assert.equal(render('{coins}'), '{coins}');
  assert.equal(hasExpression('Buy {coins} now'), false);
  assert.equal(hasExpression('Buy {coins * 2} now'), true);
  assert.equal(evaluate('1 +'), null);
  assert.equal(evaluate(`${'('.repeat(34)}1${')'.repeat(34)}`), null);
});
test('rendering keeps the text around and typed values stay values', () => {
  assert.equal(render('Price: {price * 2} ({vip ? "VIP" : "Normal"})'), 'Price: 90 (VIP)');
  assert.equal(render('${price * 2}'), '$90');
  const seen = [];
  assert.equal(renderExpressions('A {coins} B {coins + 1} C', part => { seen.push(part); return part.toUpperCase(); }, names), 'A {COINS} B 121 C');
  assert.deepEqual(seen, ['A {coins} B ', ' C']);
  const typed = { ...VALUES, answer: '1 + 1', evil: '} {coins * 1000', quote: '" + coins + "' };
  const value = source => evaluateExpression(source, name => typed[name] ?? null);
  assert.equal(value('answer + "!"'), '1 + 1!');
  assert.equal(value('evil + "x"'), '} {coins * 1000x');
  assert.equal(value('quote + ""'), '" + coins + "');
  assert.equal(value('answer == 2'), 'false');
});
test('variable arithmetic matches Variables.apply', () => {
  assert.equal(arithmetic('divide', '10', '3'), '3.333333');
  assert.equal(arithmetic('add', undefined, '5'), '5', 'a missing variable counts as 0');
  assert.equal(arithmetic('multiply', '1.5', '1.5'), '2.25');
  assert.equal(arithmetic('subtract', '0.3', '0.1'), '0.2');
  assert.equal(arithmetic('add', '1e2', '1'), '101');
  assert.equal(arithmetic('divide', '5', '0'), '5', 'dividing by zero leaves the value');
  assert.equal(arithmetic('add', 'abc', '1'), null, 'a word is not a number');
  assert.equal(arithmetic('add', '1', ' 2 '), '3');
  assert.equal(formatDecimal(decimal('-0.0000001')), '0');
  assert.equal(decimal('1,5'), null);
  assert.throws(() => arithmetic('power', '1', '2'));
});
