import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, validateScreen } from './model.mjs';
import { addTemplate, templateCatalog } from './templates.mjs';

test('own templates produce valid exported elements with stable group membership', () => {
  for(const template of templateCatalog) {
    const base=createScreen(), before=JSON.stringify(base);
    const result=addTemplate(base,template.id);
    validateScreen(result.screen);
    assert.ok(result.ids.length>0);
    assert.deepEqual(result.group.members,result.ids);
    assert.equal(new Set(result.screen.elements.map(e=>e.id)).size,result.screen.elements.length);
    assert.equal(JSON.stringify(base),before);
  }
});
test('repeated templates get new IDs and actionable buttons use unoccupied space', () => {
  const first=addTemplate(createScreen(),'plain-button');
  const second=addTemplate(first.screen,'plain-button');
  validateScreen(second.screen);
  assert.ok(second.ids.every(id=>!first.ids.includes(id)));
  assert.equal(second.screen.elements.find(e=>e.id===second.ids[0]).action,'message:Button selected.');
});
test('template limits and unknown templates fail without modifying the design', () => {
  const base=createScreen();
  base.elements=Array.from({length:128},(_,i)=>({...base.elements[0],id:`label${i}`}));
  const before=JSON.stringify(base);
  assert.throws(()=>addTemplate(base,'card'),/128/);
  assert.throws(()=>addTemplate(base,'commercial-assets'),/Unknown/);
  assert.equal(JSON.stringify(base),before);
});

test('boss and bar components insert valid schema-8 pieces with the live tokens', async () => {
  const { createScreen, validateScreen, serializeScreen, parseScreen } = await import('./model.mjs');
  const { addTemplate, templateCatalog } = await import('./templates.mjs');
  for (const key of ['title', 'health-bar', 'boss-bar', 'key-cap', 'key-cap-wide', 'timer-bar']) {
    assert.ok(templateCatalog.some(t => t.id === key), key);
    const result = addTemplate(createScreen(), key);
    assert.equal(result.screen.version, 8, key);
    validateScreen(parseScreen(serializeScreen(result.screen)));
  }
  const cap = addTemplate(createScreen(), 'key-cap').screen;
  assert.ok(cap.elements.some(e => e.text === '{key}' && e.style.textScale === 4 && e.style.textColor === '#111111'));
  assert.ok(addTemplate(createScreen(), 'timer-bar').screen.elements.some(e => e.text === '{bar:timer:0}' && e.style.textColor === '#ffffff'));
});
