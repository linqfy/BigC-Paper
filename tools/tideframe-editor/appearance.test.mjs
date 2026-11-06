import test from 'node:test';
import assert from 'node:assert/strict';
import * as model from './model.mjs';

test('appearance upgrade preservesv4 input and adds strictv5 style defaults', () => {
  const old = model.createScreen();
  assert.equal(typeof model.upgradeAppearance, 'function');
  const next = model.upgradeAppearance(old);
  assert.equal(old.version, 4); assert.equal(next.version, 5);
  assert.deepEqual(next.elements[0].style, { ...model.DEFAULT_STYLE, rotation: 0, radius: .08, outlineWidth: 1,
    hoverColor: null, hoverEffect: 'none', textAlign: 'center', wrapWidth: 0 });
  assert.deepEqual(model.promoteScreen(next), next);
  assert.equal(model.addElement(next, 'decoration').elements.at(-1).style.rotation, 0);
});

test('v5 multiline and512printableASCII characters roundtrip whilev4 staysstrict', () => {
  const next = model.upgradeAppearance(model.createScreen());
  next.elements[0].text = 'LINE ONE\nLINE TWO';
  assert.deepEqual(model.parseScreen(model.serializeScreen(next)), next);
  next.elements[0].text = 'X'.repeat(512); assert.equal(model.validateScreen(next), next);
  next.elements[0].text += 'X'; assert.throws(() => model.validateScreen(next));
  for (const text of ['LINE\nTWO', 'X'.repeat(65)]) {
    const old = model.createScreen(); old.elements[0].text = text; assert.throws(() => model.validateScreen(old));
  }
});

test('v5 appearance ranges keys and rawinteger tokens reject malformed input', () => {
  const next = model.upgradeAppearance(model.createScreen());
  for (const [field, value] of [['rotation', 181], ['radius', .51], ['outlineWidth', 1.5], ['wrapWidth', 256.1],
    ['hoverColor', '#bad'], ['hoverEffect', 'script'], ['textAlign', 'justify']]) {
    const bad = model.cloneScreen(next); bad.elements[0].style[field] = value;
    assert.throws(() => model.validateScreen(bad), field);
  }
  for (const field of ['outlineWidth', 'wrapWidth']) {
    const raw = model.serializeScreen(next).replace(`"${field}": ${field === 'outlineWidth' ? 1 : 0}`, `"${field}": 1.00000000000000000001`);
    assert.throws(() => model.parseScreen(raw), field);
  }
  const bad = model.cloneScreen(next); delete bad.elements[0].style.radius; assert.throws(() => model.validateScreen(bad));
});
