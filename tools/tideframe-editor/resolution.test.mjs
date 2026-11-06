import test from 'node:test';
import assert from 'node:assert/strict';
import { autoGuiScale, effectiveGuiScale, canvasTopGui, simulate, WINDOW_PRESETS, MENU_SIZES } from './resolution.mjs';

test('GUI scale follows the game: the largest automatic one keeping 320 x 240, a chosen one capped by it', () => {
  assert.equal(autoGuiScale(1920, 1080), 4);
  assert.equal(autoGuiScale(2560, 1440), 6);
  assert.equal(autoGuiScale(1280, 1024), 4);
  assert.equal(autoGuiScale(854, 480), 2);
  assert.equal(effectiveGuiScale(1920, 1080, 2), 2);
  assert.equal(effectiveGuiScale(1920, 1080, 9), 4, 'a chosen scale never exceeds the automatic maximum');
  assert.deepEqual(simulate({ width: 1366, height: 768 }).screen, { width: 456, height: 256 }, '1366/3 rounds up like the game');
});

test('the canvas frame matches the pack: centred at 1x, the 2x frame 4 GUI pixels lower', () => {
  assert.equal(canvasTopGui(4), -72, 'half of 144: exactly centred');
  assert.equal(canvasTopGui(8), -140, 'half of 288 would be -144');
  assert.equal(canvasTopGui(7), -4 * (3 + 28), 'round(18 x 1.75 - 3) = 29, capped at 4 x 7 = 28');
});

test('1080p at GUI Auto with the 2x menu: the art covers the screen, only its edge falls off, the safe area is in', () => {
  const view = simulate({ width: 1920, height: 1080, mode: 'fixed', quarters: 8 });
  assert.deepEqual(view.canvas, { x: -16, y: -5, width: 512, height: 288 });
  assert.deepEqual(view.cut, { left: 8, right: 8, top: 2.5, bottom: 6.5 });
  assert.equal(view.safeVisible, true);
});

test('a 5:4 window loses the sides with a fixed size; auto-fit keeps the safe area exactly inside', () => {
  const fixed = simulate({ width: 1280, height: 1024, mode: 'fixed', quarters: 8 });
  assert.equal(fixed.safeVisible, false);
  assert.ok(fixed.cut.left > 40);
  const fitted = simulate({ width: 1280, height: 1024, mode: 'autofit' });
  assert.equal(fitted.safeVisible, true);
  assert.ok(Math.abs(fitted.factor - 320 / 464) < 1e-9);
  assert.ok(Math.abs(fitted.canvas.x + 12 * fitted.unit) < 1e-6, 'the safe area touches the side edges');
  const wide = simulate({ width: 3440, height: 1440, mode: 'autofit' });
  assert.equal(wide.safeVisible, true, '21:9 too');
});

test('every preset and size gives a screen and a canvas', () => {
  for (const preset of WINDOW_PRESETS) for (const quarters of MENU_SIZES) {
    const view = simulate({ width: preset.width, height: preset.height, quarters });
    assert.ok(view.screen.width >= 320 && view.screen.height >= 240, preset.id);
    assert.equal(view.canvas.width, 64 * quarters);
  }
  assert.throws(() => simulate({ width: 100, height: 100 }), /320 x 240/);
});
