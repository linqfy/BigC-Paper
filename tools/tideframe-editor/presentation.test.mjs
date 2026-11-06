import test from 'node:test';
import assert from 'node:assert/strict';
import * as m from './model.mjs';

test('presentation upgrade adds strict defaults without changing older screens', () => {
  const old=m.createScreen(); assert.equal(typeof m.upgradePresentation,'function');
  const next=m.upgradePresentation(old);
  assert.equal(old.version,4);assert.equal(next.version,6);
  assert.deepEqual(next.presentation,{mode:'menu',scale:1,cursor:'cross'});
  assert.deepEqual(next.elements[0].style,{...m.DEFAULT_APPEARANCE_STYLE,brightness:1});
  assert.deepEqual(m.promoteScreen(next),next);assert.deepEqual(m.upgradeAppearance(next),next);
  assert.equal(m.addElement(next,'decoration').elements.at(-1).style.brightness,1);
  const cloned=m.cloneScreen(next);cloned.presentation.scale=2;assert.equal(next.presentation.scale,1);
});
test('presentation supports persistent overlays scale and original cursor choices with v6 roundtrip', () => {
  const next=m.upgradePresentation(m.createScreen());
  next.presentation={mode:'overlay',scale:3,cursor:'text'};next.elements[0].style.brightness=1.5;next.elements[0].text='LINE\nTWO';
  assert.deepEqual(m.parseScreen(m.serializeScreen(next)),next);
  next.presentation.cursor='block';assert.equal(m.validateScreen(next),next);
});
test('presentation rejects unsupported enums inexact scale range and missing style fields', () => {
  const next=m.upgradePresentation(m.createScreen());
  for(const [key,value] of [['mode','script'],['scale',1.5],['scale',4],['cursor','foreign']]) {
    const bad=m.cloneScreen(next);bad.presentation[key]=value;assert.throws(()=>m.validateScreen(bad));
  }
  for(const value of [-.01,2.01,NaN,Infinity]) {const bad=m.cloneScreen(next);bad.elements[0].style.brightness=value;assert.throws(()=>m.validateScreen(bad));}
  assert.throws(()=>m.parseScreen(m.serializeScreen(next).replace('"scale": 1','"scale": 1.00000000000000000001')));
  const missing=m.cloneScreen(next);delete missing.elements[0].style.brightness;assert.throws(()=>m.validateScreen(missing));
  const legacy=m.createScreen();legacy.presentation={...m.DEFAULT_PRESENTATION};assert.throws(()=>m.validateScreen(legacy));
});
