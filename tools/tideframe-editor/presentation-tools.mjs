// Original screen authoring helpers; no client size or keyboard signal is invented.
import { upgradeLogic,upgradePresentation,addElement,cloneScreen,validateScreenBudget} from './model.mjs';
export function setPresentation(value,patch) {
  if(!patch||Object.keys(patch).some(k=>!['mode','scale','cursor','cursorSize','cursorSpeed','stack'].includes(k)))throw Error('Unknown presentation field.');
  // Per-page cursor size and speed, and HUD pages that stack, are schema 9; null / 1 / false remove them again.
  const custom='cursorSize' in patch||'cursorSpeed' in patch||patch.stack===true;
  const next=custom?upgradeLogic(value):upgradePresentation(value);next.presentation={...next.presentation,...patch};
  if(next.presentation.cursorSize===null||next.presentation.cursorSize===undefined)delete next.presentation.cursorSize;
  if(next.presentation.cursorSpeed===1||next.presentation.cursorSpeed===undefined)delete next.presentation.cursorSpeed;
  if(patch.stack===true&&next.presentation.mode!=='overlay')throw Error('Presentation stack is for HUD pages (mode overlay).');
  // A page turned into a menu stops stacking.
  if(next.presentation.stack!==true||next.presentation.mode!=='overlay')delete next.presentation.stack;
  validateScreenBudget(next);return next;
}
export function addCloseButton(value) {
  const next=upgradePresentation(value),existing=next.elements.find(e=>e.kind==='button'&&e.action==='close'&&e.text==='X');
  if(existing)return {screen:next,ids:[existing.id]};
  const proto=addElement({...next,elements:[]},'button').elements[0],used=new Set(next.elements.map(e=>e.id));let id='close_x',n=1;
  while(used.has(id))id=`close_x_${n++}`;
  next.elements.push({...proto,id,text:'X',x:.84,y:.79,width:.12,height:.16,action:'close',background:true,
    style:{...proto.style,fillColor:'#173b48',borderColor:'#8bdfc9',textColor:'#edf8f4'},layer:128});
  validateScreenBudget(next);return {screen:next,ids:[id]};
}
export function addTextBlock(value) {
  const next=upgradePresentation(value),added=addElement(next,'label'),e=added.elements.at(-1);
  added.elements[added.elements.length-1]={...e,text:'YOUR TEXT\nGOES HERE',width:1.2,height:.42,
    style:{...e.style,textAlign:'left',wrapWidth:96}};
  validateScreenBudget(added);return {screen:added,ids:[e.id]};
}
export function fillCanvas(value,id) {
  const next=cloneScreen(value),e=next.elements.find(e=>e.id===id);if(!e)throw Error('Select an existing element.');
  Object.assign(e,{x:0,y:0,width:2,height:2});validateScreenBudget(next);return next;
}
