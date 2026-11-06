import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { validateScreen, validateLogic, parseScreen, serializeScreen, DEFAULT_GRADIENT_STYLE } from './model.mjs';

// A 1 x 1 opaque PNG, built like the plugin's fixtures (structurally valid for validatePngBase64).
const PNG = readFileSync(new URL('./images.test.mjs', import.meta.url), 'utf8').match(/'(iVBOR[A-Za-z0-9+/=]{40,})'/)?.[1];
const element = (extra = {}) => ({ id: 'icon', text: '', x: 0, y: 0, width: .5, height: .5, kind: 'button', action: '', shape: 'rectangle', border: false,
  messageCondition: null, background: false, image: null, style: { ...DEFAULT_GRADIENT_STYLE }, layer: 0, actionArea: null, ...extra });
const screen = (version, elements) => ({ version, id: 'menu', title: 'MENU', quality: { preset: 'custom', width: 1024, height: 576 },
  presentation: { mode: 'menu', scale: 1, cursor: 'cross' }, elements });

test('menus may slide out when they close, as well as fade', () => {
  validateLogic({ animation: { open: { effect: 'slide', from: 'left' }, close: { effect: 'slide', from: 'right', distance: 40, 'duration-ms': 300 } } }, false, 'menu');
  validateLogic({ animation: { close: { effect: 'fade' } } }, false, 'menu');
  assert.throws(() => validateLogic({ animation: { close: { effect: 'zoom' } } }, false, 'menu'), /fade, slide or none/);
});

test('a button may carry a hover image in schema 9 only, and only a button', { skip: !PNG && 'no PNG fixture found' }, () => {
  validateScreen(screen(9, [element({ image: PNG, hoverImage: PNG })]));
  assert.throws(() => validateScreen(screen(8, [element({ hoverImage: PNG })])), /Element/);
  assert.throws(() => validateScreen(screen(9, [element({ kind: 'label', hoverImage: PNG })])), /hover image needs a button/);
  assert.throws(() => validateScreen(screen(9, [element({ hoverImage: 'not-a-png' })])), /hover image/);
  const parsed = parseScreen(serializeScreen(screen(9, [element({ hoverImage: PNG })])));
  assert.equal(parsed.elements[0].hoverImage, PNG);
});

test('the inspector loads and removes hover images and the preview swaps them on hover', () => {
  const html = readFileSync(new URL('./index.html', import.meta.url), 'utf8');
  for (const id of ['load-hover-image', 'remove-hover-image', 'hover-image-file', 'hover-image-info']) assert.match(html, new RegExp(`id="${id}"`));
  const css = readFileSync(new URL('./styles.css', import.meta.url), 'utf8');
  assert.match(css, /\[data-hover-image="true"\]:hover \.element-hover-image\{display:block\}/);
  const app = readFileSync(new URL('./app.mjs', import.meta.url), 'utf8');
  assert.match(app, /changeActive\(\{ hoverImage \}, 'Load hover image'\)/);
  assert.match(app, /cached\.hoverImage !== element\.hoverImage/);
});
