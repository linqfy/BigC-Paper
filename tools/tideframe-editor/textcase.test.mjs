import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateScreen, parseScreen, serializeScreen, caseText, normalizeText, drawableText, extendedText, TEXT_EXTRA, LATIN_UPPER, LATIN_LOWER, TEXT_SYMBOLS,
  DEFAULT_GRADIENT_STYLE } from './model.mjs';

const element = (extra = {}) => ({ id: 'tag', text: 'Año ¿Sí? ★', x: 0, y: 0, width: .8, height: .2, kind: 'label', action: '', shape: 'rectangle', border: true,
  messageCondition: null, background: false, image: null, style: { ...DEFAULT_GRADIENT_STYLE }, layer: 0, actionArea: null, ...extra });
const screen = (version, elements, title = 'Menú') => ({ version, id: 'menu', title, quality: { preset: 'custom', width: 1024, height: 576 },
  presentation: { mode: 'menu', scale: 1, cursor: 'cross' }, elements });

test('schema 9 accepts accented text, symbols and textCase; schema 8 stays ASCII', () => {
  validateScreen(screen(9, [element({ style: { ...DEFAULT_GRADIENT_STYLE, textCase: 'original' } })]));
  validateScreen(screen(9, [element()]));
  assert.throws(() => validateScreen(screen(8, [element()], 'MENU')), /text/);
  assert.throws(() => validateScreen(screen(8, [element({ text: 'OK' })])), /title/);
  assert.throws(() => validateScreen(screen(8, [element({ text: 'OK', style: { ...DEFAULT_GRADIENT_STYLE, textCase: 'original' } })], 'MENU')), /style/);
  assert.throws(() => validateScreen(screen(9, [element({ style: { ...DEFAULT_GRADIENT_STYLE, textCase: 'lower' } })])), /textCase/);
  for (const text of ['snow ☃', 'emoji 😀', 'tab\t']) assert.throws(() => validateScreen(screen(9, [element({ text })])), /text/);
});

test('a schema 9 screen with accents round-trips through the strict reader', () => {
  const value = screen(9, [element({ style: { ...DEFAULT_GRADIENT_STYLE, textCase: 'original' } })], 'Menú Ñandú');
  const parsed = parseScreen(serializeScreen(value));
  assert.equal(parsed.title, 'Menú Ñandú'); assert.equal(parsed.elements[0].text, 'Año ¿Sí? ★'); assert.equal(parsed.elements[0].style.textCase, 'original');
});

test('text case and look-alikes match the game font', () => {
  assert.equal(caseText('año ¿qué? ñandú ★'), 'AÑO ¿QUÉ? ÑANDÚ ★');
  assert.equal(caseText('año', 'original'), 'año');
  assert.equal(caseText('µß'), 'µß', 'no capitals the font lacks');
  assert.equal(normalizeText('♥️ ‘x’ – “y” ✓ ⭐'), '❤ \'x\' - "y" ✔ ★');
  assert.ok(drawableText('¡Hola!\n3×2 → ★')); assert.ok(!drawableText('☃'));
  assert.ok(extendedText('ñ')); assert.ok(!extendedText('PLAIN\nASCII'));
  for (let i = 0; i < LATIN_LOWER.length; i++) assert.equal(caseText(LATIN_LOWER[i]), LATIN_UPPER[i]);
});

test('the editor and the plugin share one character set', () => {
  const java = readFileSync(new URL('../../src/main/java/dev/linqfy/bigCasares/modules/tideframe/engine/core/LiveText.java', import.meta.url), 'utf8');
  const constant = name => java.match(new RegExp(`${name} = "([^"]+)"`))[1];
  assert.equal(LATIN_UPPER, constant('LATIN_UPPER')); assert.equal(LATIN_LOWER, constant('LATIN_LOWER')); assert.equal(TEXT_SYMBOLS, constant('SYMBOLS'));
  assert.equal(TEXT_EXTRA.length, 72);
});
