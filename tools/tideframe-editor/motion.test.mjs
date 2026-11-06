import test from 'node:test';
import assert from 'node:assert/strict';
import { validateScreen, validateLogic, DEFAULT_GRADIENT_STYLE, KEYFRAME_TRIGGERS, EASINGS, ELEMENT_PRESETS, SCREEN_PRESETS, mergeLogic, setElementLogic, setScreenLogic } from './model.mjs';
import { setPresentation } from './presentation-tools.mjs';

const element = (id, kind, extra = {}) => ({ id, text: id.toUpperCase(), x: 0, y: 0, width: .4, height: .2, kind, action: '', shape: 'rectangle', border: true,
  messageCondition: null, background: false, image: null, style: { ...DEFAULT_GRADIENT_STYLE }, layer: 0, actionArea: null, ...extra });
const screen = (elements, extra = {}) => ({ version: 9, id: 'menu', title: 'MENU', quality: { preset: 'custom', width: 1024, height: 576 },
  presentation: { mode: 'menu', scale: 1, cursor: 'cross' }, elements, ...extra });

test('hover actions, tooltips and keyframes are checked while typing', () => {
  validateLogic({ 'on-hover': [{ type: 'sound', value: 'ui.button.click' }], 'on-leave': [], tooltip: 'tip',
    keyframes: { trigger: 'hover', easing: 'ease-out', steps: [{ at: 0 }, { at: 150, scale: 1.08, y: -2 }] } }, true, 'buy');
  for (const keyframes of [{ steps: [{ at: 0 }] }, { steps: [{ at: 5 }, { at: 10 }] }, { trigger: 'click', steps: [{ at: 0 }, { at: 9 }] },
    { steps: [{ at: 0, scale: 9 }, { at: 9 }] }, { steps: [{ at: 0, spin: 1 }, { at: 9 }] }, { easing: 'bounce', steps: [{ at: 0 }, { at: 9 }] }])
    assert.throws(() => validateLogic({ keyframes }, true, 'x'), /keyframe/);
  assert.throws(() => validateLogic({ tooltip: 'Not An Id' }, true, 'x'), /tooltip/);
  assert.deepEqual([...KEYFRAME_TRIGGERS], ['open', 'loop', 'hover', 'action']); assert.equal(EASINGS.length, 4);
});

test('hover logic needs a button, and an element cannot be its own tooltip', () => {
  validateScreen(screen([element('tip', 'label'), element('buy', 'button', { logic: { tooltip: 'tip' } })]));
  assert.throws(() => validateScreen(screen([element('a', 'label', { logic: { 'on-hover': [{ type: 'message', value: 'x' }] } })])), /button/);
  assert.throws(() => validateScreen(screen([element('a', 'label', { logic: { keyframes: { trigger: 'hover', steps: [{ at: 0 }, { at: 50 }] } } })])), /button/);
  assert.throws(() => validateScreen(screen([element('a', 'button', { logic: { tooltip: 'a' } })])), /own tooltip/);
  validateScreen(screen([element('logo', 'label', { logic: { keyframes: { trigger: 'loop', steps: [{ at: 0 }, { at: 1000, rotation: 360 }] } } })]));
});

test('screen open and close animations', () => {
  validateLogic({ animation: { open: { effect: 'slide', from: 'right', distance: 48, 'duration-ms': 350, easing: 'ease-out' }, close: { effect: 'fade', 'duration-ms': 200 } } }, false, 'menu');
  validateLogic({ animation: { open: { effect: 'fade' }, 'close-reversed': true } }, false, 'menu');
  validateLogic({ animation: { close: { effect: 'slide' } } }, false, 'menu'); // 0.22: menus may slide out
  assert.throws(() => validateLogic({ animation: { close: { effect: 'zoom' } } }, false, 'menu'), /fade, slide or none/);
  assert.throws(() => validateLogic({ animation: { open: { effect: 'zoom' } } }, false, 'menu'), /effect/);
  assert.throws(() => validateLogic({ animation: { open: { 'duration-ms': 9000 } } }, false, 'menu'), /duration/);
});

test('per-page cursor size and speed upgrade to schema 9 and clear again', () => {
  const v8 = { ...screen([]), version: 8 };
  const sized = setPresentation(v8, { cursorSize: 1.2, cursorSpeed: 1.5 });
  assert.equal(sized.version, 9); assert.equal(sized.presentation.cursorSize, 1.2); assert.equal(sized.presentation.cursorSpeed, 1.5);
  const cleared = setPresentation(sized, { cursorSize: null, cursorSpeed: 1 });
  assert.equal(cleared.presentation.cursorSize, undefined); assert.equal(cleared.presentation.cursorSpeed, undefined);
  assert.throws(() => setPresentation(v8, { cursorSize: 3 }), /cursorSize/);
  assert.throws(() => validateScreen({ ...v8, presentation: { ...v8.presentation, cursorSpeed: 2 } }), /Presentation/);
});

test('logic presets are valid, merge into existing logic and leave the original untouched', () => {
  for (const [name, preset] of Object.entries(ELEMENT_PRESETS)) validateLogic(preset, true, name);
  for (const [name, preset] of Object.entries(SCREEN_PRESETS)) validateLogic(preset, false, name);
  const before = { actions: [{ type: 'close' }], keyframes: { steps: [{ at: 0 }, { at: 50 }] } };
  const merged = mergeLogic(before, ELEMENT_PRESETS['hover-grow']);
  assert.deepEqual(merged.actions, [{ type: 'close' }]); assert.equal(merged.keyframes.trigger, 'hover');
  assert.equal(before.keyframes.trigger, undefined, 'the original stays as it was');
  merged.keyframes.steps[1].scale = 2; assert.equal(ELEMENT_PRESETS['hover-grow'].keyframes.steps[1].scale, 1.08, 'presets are copied, not shared');
  const v8 = { ...screen([element('buy', 'button')]), version: 8 };
  const withHover = setElementLogic(v8, 'buy', mergeLogic(null, ELEMENT_PRESETS['hover-grow']));
  assert.equal(withHover.version, 9); assert.equal(withHover.elements[0].logic.keyframes.trigger, 'hover');
  const animated = setScreenLogic(withHover, mergeLogic(withHover.logic, SCREEN_PRESETS['open-slide-left']));
  assert.equal(animated.logic.animation.open.effect, 'slide');
});
