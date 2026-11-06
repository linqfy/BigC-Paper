import test from 'node:test';
import assert from 'node:assert/strict';
import {createScreen,upgradePresentation,validateScreen} from './model.mjs';
import {setPresentation,addCloseButton,addTextBlock,fillCanvas} from './presentation-tools.mjs';
import {encodeClipboard,pasteClipboard} from './interaction.mjs';
import {createWorkspace,parseProject,serializeProject} from './workspace.mjs';

test('presentation changes preserve old screen and export strict mode scale cursor fields',()=>{
  const original=createScreen(),before=JSON.stringify(original);
  const next=setPresentation(original,{mode:'overlay',scale:2,cursor:'text'});
  validateScreen(next);assert.equal(next.version,6);assert.equal(next.presentation.mode,'overlay');assert.equal(next.presentation.scale,2);
  assert.equal(JSON.stringify(original),before);assert.throws(()=>setPresentation(original,{scale:1.5}));assert.throws(()=>setPresentation(original,{unknown:true}));
});
test('close X uses a separate visible corner target and skips an existing X',()=>{
  const original=createScreen(),result=addCloseButton(original),close=result.screen.elements.find(e=>e.id===result.ids[0]);
  assert.equal(close.text,'X');assert.equal(close.action,'close');assert.equal(close.kind,'button');assert.ok(close.x>.75&&close.y>.75&&close.x+close.width/2<=.9&&close.y+close.height/2<=.88,'corner, inside the full-screen safe area');
  assert.equal(result.screen.elements.length,4);assert.equal(addCloseButton(result.screen).screen.elements.length,4);validateScreen(result.screen);
});
test('close insertion fails atomically if its reserved corner is already an action target',()=>{
  const original=createScreen();original.elements[1]={...original.elements[1],x:.91,y:.88};const before=JSON.stringify(original);
  assert.throws(()=>addCloseButton(original),/overlap/i);assert.equal(JSON.stringify(original),before);
});
test('text block is multiline left aligned and transparent without disturbing other elements',()=>{
  const original=createScreen(),result=addTextBlock(original),block=result.screen.elements.at(-1);
  assert.equal(result.screen.version,6);assert.ok(block.text.includes('\n'));assert.equal(block.style.textAlign,'left');assert.equal(block.background,false);
  assert.equal(block.style.brightness,1);assert.equal(original.elements.length,3);validateScreen(result.screen);
});
test('fill canvas sizes the selected visual while preserving explicit action area and source',()=>{
  const value=upgradePresentation(createScreen());const result=fillCanvas(value,'heading');
  assert.deepEqual([result.elements[0].x,result.elements[0].y,result.elements[0].width,result.elements[0].height],[0,0,2,2]);
  assert.equal(value.elements[0].width,1.4);assert.throws(()=>fillCanvas(value,'missing'));
});
test('v6 clipboard preserves brightness and destination presentation across mixed versions',()=>{
  const source=upgradePresentation(createScreen());source.presentation={mode:'overlay',scale:3,cursor:'block'};source.elements[0].style.brightness=.4;
  const target=upgradePresentation(createScreen());target.presentation={mode:'menu',scale:2,cursor:'text'};
  const pasted=pasteClipboard(target,encodeClipboard(source,['heading']));
  assert.equal(pasted.screen.version,6);assert.deepEqual(pasted.screen.presentation,target.presentation);assert.equal(pasted.screen.elements.at(-1).style.brightness,.4);
  const older=pasteClipboard(createScreen(),encodeClipboard(source,['heading']));assert.equal(older.screen.version,6);assert.equal(older.screen.presentation.scale,1);validateScreen(older.screen);
  const back=pasteClipboard(target,encodeClipboard(createScreen(),['heading']));assert.equal(back.screen.elements.at(-1).style.brightness,1);validateScreen(back.screen);
});
test('workspace accepts v6 but rejects fractional raw scale before Number rounding',()=>{
  const state=createWorkspace(upgradePresentation(createScreen()));state.commitScreen({...state.activePage().screen,title:'SIX'});
  const raw=serializeProject(state.project);assert.equal(parseProject(raw).pages[0].screen.version,6);
  assert.throws(()=>parseProject(raw.replace('"scale": 1','"scale": 0.9999999999999999999')),/exact integer/i);
});
