import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, addElement, validateScreen, parseScreen, serializeScreen } from './model.mjs';
import { THEMES, applyTheme } from './themes.mjs';

test('every theme recolours by role and keeps bars meaningful', () => {
  let screen = addElement(createScreen(), 'decoration');
  screen = addElement(screen, 'label');
  const bar = screen.elements.at(-1); bar.text = '{bar:health}'; bar.background = true; bar.style = { ...bar.style, textColor: '#e04848' };
  for (const theme of THEMES) {
    const next = applyTheme(screen, theme.id);
    assert.equal(next.version, 8);
    validateScreen(parseScreen(serializeScreen(next)));
    const button = next.elements.find(e => e.kind === 'button');
    assert.equal(button.style.fillColor, theme.button[0]); assert.equal(button.style.gradientColor, theme.button[1]);
    assert.equal(button.style.hoverColor, theme.hover);
    const themedBar = next.elements.find(e => e.text === '{bar:health}');
    assert.equal(themedBar.style.textColor, '#e04848', 'bar fill colour is kept');
    assert.equal(themedBar.style.fillColor, theme.panel[1]);
  }
});

test('themes can target a selection only and reject unknown ids', () => {
  const screen = createScreen();
  const first = screen.elements[0].id;
  const next = applyTheme(screen, 'ember', [first]);
  const untouched = next.elements.filter(e => e.id !== first);
  for (const e of untouched) assert.equal(e.style.textColor, screen.elements.find(o => o.id === e.id).style.textColor);
  assert.throws(() => applyTheme(screen, 'neon'));
});
