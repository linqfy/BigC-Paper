import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, addElement, upgradeTextStyle, upgradePresentation, validateScreen, parseScreen, serializeScreen, barSource, DEFAULT_TEXT_STYLE } from './model.mjs';
import { encodeClipboard, pasteClipboard } from './interaction.mjs';

test('schema 7 adds text size, outline and shadow without touching older fields', () => {
  const screen = upgradeTextStyle(createScreen());
  assert.equal(screen.version, 7);
  for (const e of screen.elements) assert.deepEqual(Object.keys(e.style).sort(), Object.keys(DEFAULT_TEXT_STYLE).sort());
  screen.elements[0].style = { ...screen.elements[0].style, textScale: 3, textOutline: '#102030', textShadow: true, hoverEffect: 'glow' };
  const round = parseScreen(serializeScreen(validateScreen(screen)));
  assert.equal(round.elements[0].style.textScale, 3);
  assert.equal(round.elements[0].style.textOutline, '#102030');
  assert.equal(round.elements[0].style.textShadow, true);
  assert.equal(round.elements[0].style.hoverEffect, 'glow');
  // Upgrading again never downgrades a version 7 screen.
  assert.equal(upgradePresentation(round).version, 7);
});

test('schema 7 rejects out-of-range text styles', () => {
  const screen = upgradeTextStyle(createScreen());
  for (const bad of [{ textScale: 5 }, { textScale: 1.5 }, { textOutline: 'red' }, { textShadow: 'yes' }, { hoverEffect: 'spin' }]) {
    const next = structuredClone(screen); Object.assign(next.elements[0].style, bad);
    assert.throws(() => validateScreen(next));
  }
  assert.throws(() => parseScreen(serializeScreen(screen).replace(/"textScale": 1/, '"textScale": 1.5')));
});

test('new elements in a version 7 screen get the full text style', () => {
  const next = addElement(upgradeTextStyle(createScreen()), 'label');
  assert.equal(next.elements.at(-1).style.textScale, 1);
  assert.equal(next.elements.at(-1).style.textOutline, null);
});

test('bar tokens are recognised only as a whole label text', () => {
  const base = { kind: 'label' };
  assert.equal(barSource({ ...base, text: '{bar:health}' }), 'health');
  assert.equal(barSource({ ...base, text: '{bar:xp}' }), 'xp');
  assert.equal(barSource({ ...base, text: 'HP {bar:health}' }), null);
  // As LiveText.BAR: a variable fills a bar too (out of 100, or of the maximum after /).
  assert.equal(barSource({ ...base, text: '{bar:mana}' }), 'mana');
  assert.equal(barSource({ ...base, text: '{bar:progreso/10}' }), 'progreso');
  assert.equal(barSource({ ...base, text: '{bar:Mana}' }), null);
  assert.equal(barSource({ ...base, text: '{bar:health:20}' }), 'health');
  assert.equal(barSource({ ...base, text: '{bar:boss}' }), 'boss');
  assert.equal(barSource({ kind: 'decoration', text: '{bar:food}' }), null);
});

test('pasting version 7 elements upgrades the target screen', () => {
  const source = upgradeTextStyle(createScreen());
  source.elements[0].style.textScale = 2;
  const clip = encodeClipboard(source, [source.elements[0].id]);
  const pasted = pasteClipboard(createScreen(), clip);
  assert.equal(pasted.screen.version, 7);
  assert.equal(pasted.screen.elements.at(-1).style.textScale, 2);
});
