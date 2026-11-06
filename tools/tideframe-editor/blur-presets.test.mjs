import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, serializeScreen, parseScreen, setElementLogic } from './model.mjs';
import { BLUR_PRESETS, applyBlurPreset } from './advanced-settings.mjs';

test('blur presets apply only to selected visuals and preserve actions, logic, colors and geometry', () => {
  const original = setElementLogic(createScreen(), 'start', { 'on-hover': [{ type: 'message', value: 'Hello' }] });
  for (const preset of BLUR_PRESETS) {
    const next = applyBlurPreset(original, ['start'], preset.id);
    assert.equal(next.version, 9);
    assert.deepEqual(next.elements[0], original.elements[0]);
    const before = original.elements.find(e => e.id === 'start'), after = next.elements.find(e => e.id === 'start');
    for (const key of ['action', 'logic', 'image', 'x', 'y', 'width', 'height']) assert.deepEqual(after[key], before[key]);
    assert.equal(after.style.fillColor, before.style.fillColor);
    assert.deepEqual(parseScreen(serializeScreen(next)), next);
  }
});

test('glass and soft shadow are visible without needing a second setting', () => {
  const glass = applyBlurPreset(createScreen(), ['start'], 'glass').elements.find(e => e.id === 'start');
  assert.equal(glass.background, true); assert.equal(glass.style.backdropBlur, 6); assert.ok(glass.style.opacity < 1);
  const shadow = applyBlurPreset(createScreen(), ['start'], 'shadow').elements.find(e => e.id === 'start');
  assert.equal(shadow.style.shadow, true); assert.equal(shadow.style.shadowBlur, 4);
  const clear = applyBlurPreset(applyBlurPreset(createScreen(), ['start'], 'shadow'), ['start'], 'clear').elements.find(e => e.id === 'start');
  assert.equal(clear.style.shadow, false); assert.equal(clear.style.shadowBlur, 0);
  assert.throws(() => applyBlurPreset(createScreen(), ['missing'], 'glass'));
});
