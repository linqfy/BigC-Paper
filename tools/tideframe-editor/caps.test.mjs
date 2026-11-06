import test from 'node:test';
import assert from 'node:assert/strict';
import * as interaction from './interaction.mjs';

test('Caps Lock state uses reported modifier state and defaults conservatively to OFF', () => {
  assert.equal(typeof interaction.capsLockState, 'function');
  assert.equal(interaction.capsLockState({ getModifierState: key => key === 'CapsLock' }), true);
  assert.equal(interaction.capsLockState({ getModifierState: () => false }), false);
  assert.equal(interaction.capsLockState({ key: 'CapsLock' }), false);
  assert.equal(interaction.capsLockState({ getModifierState() { throw Error('unsupported'); } }), false);
  assert.equal(interaction.capsLockState(null), false);
});

test('turning Caps Lock OFF ends an active resize and cannot resume it without a new gesture', () => {
  assert.equal(typeof interaction.guardPointerGesture, 'function');
  const drag = { mode: 'resize', pointer: 1 };
  assert.equal(interaction.guardPointerGesture(drag, true), drag);
  const stopped = interaction.guardPointerGesture(drag, false);
  assert.equal(stopped, null); assert.equal(interaction.guardPointerGesture(stopped, true), null);
  const move = { mode: 'move', pointer: 2 };
  assert.equal(interaction.guardPointerGesture(move, false), move);
  assert.equal(interaction.guardPointerGesture(move, true), move);
});
