import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateScreen, parseScreen, serializeScreen, upgradeLogic, DEFAULT_GRADIENT_STYLE, BLUR_FIELDS, MAX_BLUR } from './model.mjs';

const element = (style = {}) => ({ id: 'glass', text: 'OK', x: 0, y: 0, width: .8, height: .4, kind: 'label', action: '', shape: 'rounded', border: true,
  messageCondition: null, background: true, image: null, style: { ...DEFAULT_GRADIENT_STYLE, ...style }, layer: 0, actionArea: null });
const screen = (version, elements) => ({ version, id: 'menu', title: 'MENU', quality: { preset: 'custom', width: 1024, height: 576 },
  presentation: { mode: 'menu', scale: 1, cursor: 'cross' }, elements });

test('schema 9 accepts the three blurs as integers 0 to 16, like the plugin', () => {
  assert.deepEqual(BLUR_FIELDS, ['blur', 'backdropBlur', 'shadowBlur']); assert.equal(MAX_BLUR, 16);
  validateScreen(screen(9, [element({ blur: 2, backdropBlur: 16, shadowBlur: 0 })]));
  validateScreen(screen(9, [element()]));
  for (const field of BLUR_FIELDS) {
    for (const bad of [17, -1, 1.5, '4', null]) assert.throws(() => validateScreen(screen(9, [element({ [field]: bad })])), new RegExp(field));
  }
});

test('older schemas refuse blurs and upgrading keeps the screen as it was', () => {
  assert.throws(() => validateScreen(screen(8, [element({ blur: 2 })])), /style/);
  const upgraded = upgradeLogic(screen(8, [element()]));
  assert.equal(upgraded.version, 9);
  validateScreen({ ...upgraded, elements: upgraded.elements.map(e => ({ ...e, style: { ...e.style, backdropBlur: 6 } })) });
});

test('blurs survive the strict reader and writer', () => {
  const parsed = parseScreen(serializeScreen(screen(9, [element({ blur: 1, backdropBlur: 8, shadowBlur: 4 })])));
  assert.equal(parsed.elements[0].style.blur, 1); assert.equal(parsed.elements[0].style.backdropBlur, 8); assert.equal(parsed.elements[0].style.shadowBlur, 4);
});

test('the inspector has a control for every blur and the preview applies them', () => {
  const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8');
  for (const field of BLUR_FIELDS) assert.match(html, new RegExp(`data-style="${field}" type="number" min="0" max="${MAX_BLUR}" step="1"`));
  const app = readFileSync(new URL('./app.mjs', import.meta.url), 'utf8');
  assert.match(app, /backdropFilter = backdrop > 0 \? `blur\(\$\{backdrop \* logicalScale\}px\)`/);
  assert.match(app, /blur > 0 \? ` blur\(\$\{blur \* logicalScale\}px\)`/);
  assert.match(app, /BLUR_FIELDS\.includes\(styleField\)\) && screen\.version < 9\) screen = upgradeLogic/);
});
