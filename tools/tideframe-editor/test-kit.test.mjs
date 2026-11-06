// The in-game test kit (test-kit.mjs) stays valid and clean as the editor changes.
import test from 'node:test';
import assert from 'node:assert/strict';
import { kitPages } from './test-kit.mjs';
import { reviewProject } from './checker.mjs';
import { serializeScreen, parseScreen } from './model.mjs';

test('the test kit pages validate, round-trip and give Revisar nothing to warn about', () => {
  const pages = kitPages();
  assert.deepEqual(pages.map(s => s.id), ['prueba', 'prueba_hud_vida', 'prueba_hud_dinero', 'prueba_logro'], 'every id starts with prueba, so it never meets the operator pages');
  for (const screen of pages) assert.deepEqual(parseScreen(serializeScreen(screen)), screen, screen.id);
  assert.deepEqual(reviewProject({ pages: pages.map(screen => ({ screen })) }), []);
});

test('the kit menu covers the functions the checklist names', () => {
  const [menu, vida, dinero, logro] = kitPages();
  assert.equal(menu.logic.behavior['keyboard-navigation'], true);
  const text = JSON.stringify(menu);
  for (const needed of ['"type":"toast"', '"type":"hud"', 'tideframe boss hit', 'tideframe:prueba', '"type":"confirm"', '"cooldown":"3s"', '"type":"charge"', '<gold>', '{21 * 2}', '"imageClip":true', '"slice":16', '"item":"diamond_sword"'])
    assert.ok(text.includes(needed), needed);
  assert.deepEqual([vida.presentation.mode, dinero.presentation.stack, logro.presentation.stack], ['overlay', true, true]);
});
