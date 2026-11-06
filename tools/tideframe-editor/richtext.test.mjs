// Idea 28 in the editor: the same tag rules as dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText (RichTextTest.java has the same cases).
import test from 'node:test';
import assert from 'node:assert/strict';
import { runs, plain, hasTags, colors, escape } from './richtext.mjs';
import { createScreen, upgradeLogic, validateScreen, extendedText } from './model.mjs';
import { reviewScreen } from './checker.mjs';
import { es } from './i18n.mjs';

const run = (text, color = null) => ({ text, color });

test('named and hex tags colour their words and nest', () => {
  assert.deepEqual(runs('Saldo: <gold>100</gold> ok'), [run('Saldo: '), run('100', '#ffaa00'), run(' ok')]);
  assert.deepEqual(runs('<#FFCC00>a<red>b</red>c</#ffcc00>d'), [run('a', '#ffcc00'), run('b', '#ff5555'), run('c', '#ffcc00'), run('d')]);
  assert.deepEqual(runs('<red>a<blue>b</red>c'), [run('a', '#ff5555'), run('b', '#5555ff'), run('c')], 'closing a tag closes the ones opened after it');
  assert.deepEqual(runs('<green>rest'), [run('rest', '#55ff55')]);
  assert.deepEqual(runs('<grey>a</grey><gray>b</gray>'), [run('ab', '#aaaaaa')]);
  assert.deepEqual(colors('<gold>a</gold> <dark_red>b <gold>c'), ['#ffaa00', '#aa0000']);
});

test('anything that is not a colour tag stays text', () => {
  for (const text of ['<3 a<b <bold>x</bold>', '</gold> closes nothing', '<#ffcc0>x', '<GOLD>x', '<gold >x', 'x < y > z', '{coins < 5 ? "a" : "b"}'])
    assert.deepEqual(runs(text), [run(text)], text);
  assert.equal(hasTags('<3 a<b'), false);
  assert.deepEqual(runs('\\<gold>x'), [run('<gold>x')]);
  assert.equal(plain('a\\b'), 'a\\b');
  assert.ok(hasTags('\\<gold>x') && hasTags('<red></red>'));
});

test('escaping keeps old texts as written', () => {
  for (const old of ['<gold>hi</gold>', 'a\\<b', '\\\\<red>x', '<3 <b>', 'x</red>', 'plain']) assert.deepEqual(runs(escape(old)), [run(old)], old);
  assert.equal(escape('<3 <b>'), '<3 <b>');
});

test('the editor upgrades old pages with their tags kept as text and counts coloured texts as schema 9', () => {
  const old = createScreen();
  old.elements[0].text = '<RED>OK</RED> <red>x';
  const upgraded = upgradeLogic(old);
  assert.equal(upgraded.version, 9);
  assert.equal(plain(upgraded.elements[0].text), '<RED>OK</RED> <red>x', 'drawn exactly as before');
  assert.ok(extendedText('Saldo: <gold>100</gold>') && !extendedText('a < b'), 'typing a colour tag moves the page to schema 9');
  const many = ['black', 'dark_blue', 'dark_green', 'dark_aqua', 'dark_red', 'dark_purple', 'gold', 'gray', 'blue'].map(c => `<${c}>x`).join('');
  assert.throws(() => validateScreen({ ...upgraded, elements: [{ ...upgraded.elements[0], text: many }, ...upgraded.elements.slice(1)] }), /8 word colours/);
  assert.equal(es('heading text uses more than 8 word colours.'), 'heading: el texto usa más de 8 colores por palabra.');
});

test('Revisar measures the text as drawn, without its tags', () => {
  const page = upgradeLogic(createScreen()), label = page.elements.find(e => e.kind === 'label') ?? page.elements[0];
  const fits = { ...label, text: '<gold>OK</gold>', width: 30 / 128, height: .2 };
  const findings = reviewScreen({ ...page, elements: [fits] }).filter(f => f.message.includes('no entra'));
  assert.deepEqual(findings, [], 'two letters fit a 30 px box even though the raw text is 15 characters long');
});
