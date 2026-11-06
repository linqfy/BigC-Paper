// Original compact templates. No external component artwork, layout, font or CSS is used.
import { addElement, promoteScreen, upgradeGradient, upgradeLogic, placeElements, validateScreenBudget, setScreenLogic, DEFAULT_GRADIENT_STYLE } from './model.mjs';

export const templateCatalog=Object.freeze([
  {id:'glass-panel',name:'Panel de vidrio esmerilado (nuevo)'},
  {id:'glow-button',name:'Botón con brillo y sonido al pasar (nuevo)'},
  {id:'popup',name:'Ventana emergente que abre un botón (nuevo)'},
  {id:'tooltip-button',name:'Botón con tooltip (nuevo)'},
  {id:'data-list',name:'Lista de datos con selección (nuevo)'},
  {id:'gif',name:'Animación GIF o PNG del servidor (nuevo)'},
  {id:'item-icon',name:'Ícono de ítem del juego (nuevo)'},
  {id:'switch',name:'Control: interruptor Sí / No (nuevo)'},
  {id:'checkbox',name:'Control: casilla para marcar (nuevo)'},
  {id:'stepper',name:'Control: número con - y + (nuevo)'},
  {id:'cycle',name:'Control: selector de opciones (nuevo)'},
  {id:'confirm-box',name:'Confirmación con el estilo del menú (nuevo)'},
  {id:'tabs',name:'Pestañas con paneles (nuevo)'},
  {id:'plain-button',name:'Botón de acción'},
  {id:'heading',name:'Título'},
  {id:'info-panel',name:'Panel de información'},
  {id:'card',name:'Tarjeta'},
  {id:'list-row',name:'Fila de lista (fija)'},
  {id:'title',name:'Título grande (degradé y contorno)'},
  {id:'health-bar',name:'Barra de vida en vivo (estilo jefe)'},
  {id:'boss-bar',name:'Nombre y barra del jefe'},
  {id:'key-cap',name:'Tecla del jefe (W/A/S/D)'},
  {id:'key-cap-wide',name:'Tecla ancha del jefe (SPACE/CLICK)'},
  {id:'timer-bar',name:'Barra de tiempo del jefe (blanca)'}
].map(Object.freeze));
// Components that use text size, outline, shadow or gradients (schema 8).
const STYLED=new Set(['title','health-bar','boss-bar','key-cap','key-cap-wide','timer-bar']);
// Components with blurs, hover effects, logic or accented text (schema 9).
const LOGIC=new Set(['glass-panel','glow-button','popup','tooltip-button','data-list','gif','item-icon','switch','checkbox','stepper','cycle','confirm-box','tabs']);
// Controls keep their value in a page variable of their own (named after the control, so two copies never mix).
const CONTROL_VALUES={switch:'Sí',checkbox:'[ ]',stepper:'5',cycle:'Normal'};

export function addTemplate(value,key) {
  if(!templateCatalog.some(t=>t.id===key))throw Error('Unknown original template.');
  const next=LOGIC.has(key)?upgradeLogic(value):STYLED.has(key)?upgradeGradient(value):promoteScreen(value), prototype=kind=>addElement({...next,elements:[]},kind).elements[0];
  // Styled pieces: full schema-8 style with overrides.
  const styled=(kind,id,text,x,y,width,height,style,{background=false,border=false,shape='rectangle',layer=0}={})=>({...prototype(kind),id,text,x,y,width,height,
    background,border,shape,layer,action:'',style:{...DEFAULT_GRADIENT_STYLE,...style}});
  const element=(kind,id,text,x,y,width,height,fill=false)=>{
    const e=prototype(kind);
    return {...e,id,text,x,y,width,height,background:fill,border:kind!=='decoration',
      style:{...e.style,fillColor:'#173b48',borderColor:'#8bdfc9',textColor:'#edf8f4'},
      action:kind==='button'?'message:Button selected.':''};
  };
  const panel=(id,x,y,w,h)=>({...element('decoration',id,'',x,y,w,h,true),
    style:{...prototype('decoration').style,fillColor:'#173b48',borderColor:'#8bdfc9',textColor:'#edf8f4',opacity:.88,shadow:true}});
  const make={
    'plain-button':()=>[element('button','action','ACTION',0,0,.72,.20,true)],
    heading:()=>[element('label','heading','YOUR HEADING',0,.15,1.3,.20)],
    'info-panel':()=>[panel('panel',0,0,1.30,.52),element('label','caption','YOUR MESSAGE',0,.05,1.16,.18)],
    card:()=>[panel('card',0,0,1.16,.68),element('label','title','YOUR CARD',0,.18,1.02,.16),element('label','detail','Details go here',0,-.09,1.02,.18)],
    'list-row':()=>[panel('row',0,0,1.60,.24),element('label','name','LIST ENTRY',-.30,0,.80,.16),element('button','choose','VIEW',.55,0,.40,.18,true)],
    title:()=>[styled('label','title','YOUR TITLE',0,.6,1.6,.24,{textColor:'#ffe07a',textGradient:'#ff8a2a',textScale:2,textOutline:'#2a1406',textShadow:true})],
    'health-bar':()=>[styled('label','hp_label','HEALTH {health}/{max_health}',0,-.62,1,.1,{textColor:'#ffffff',textOutline:'#000000',textAlign:'left'}),
      styled('label','hp_bar','{bar:health:10}',0,-.74,1,.09,{fillColor:'#2a0c10',borderColor:'#000000',textColor:'#e83a48',radius:.5},{background:true,border:true,shape:'rounded'})],
    'boss-bar':()=>[styled('label','boss_name','BOSS NAME',0,.76,1.6,.2,{textColor:'#ffffff',textScale:2,textOutline:'#000000',textShadow:true}),
      styled('label','boss_bar','{bar:boss:20}',0,.615,1.3,.085,{fillColor:'#1a0508',borderColor:'#000000',textColor:'#e83a48',radius:.5,shadow:true},{background:true,border:true,shape:'rounded'})],
    'key-cap':()=>keyCap(styled,false),
    'key-cap-wide':()=>keyCap(styled,true),
    'timer-bar':()=>[styled('label','timer','{bar:timer:0}',0,-.68,.82,.07,{fillColor:'#000000',borderColor:'#000000',textColor:'#ffffff',radius:.5,opacity:.85},{background:true,border:true,shape:'rounded'})],
    ...newComponents(styled)
  }[key]();
  if(next.elements.length+make.length>128)throw Error('Template would exceed the 128 element limit.');
  const moved=placeElements(next,make), used=new Set(next.elements.map(e=>e.id)),ids=[],renamed=new Map();
  for(const e of moved) {
    const prefix=`own_${e.id}`;let count=1,id=prefix;
    while(used.has(id)) {const suffix=`_${count++}`;id=prefix.slice(0,32-suffix.length)+suffix;}
    // Placement adds offsets: keep positions readable (-0.34, not -0.33999999).
    const tidy=v=>Math.round(v*1e6)/1e6;
    used.add(id);ids.push(id);renamed.set(e.id,id);next.elements.push({...e,id,x:tidy(e.x),y:tidy(e.y),...(e.actionArea?{actionArea:{...e.actionArea,x:tidy(e.actionArea.x),y:tidy(e.actionArea.y)}}:{})});
  }
  // Pieces that name each other (a popup's toggle, a tooltip) follow their new ids.
  for(const e of next.elements) if(ids.includes(e.id)&&e.logic) e.logic=retarget(e.logic,renamed);
  // An empty logic object ({}) is no logic at all.
  next.elements=next.elements.map(e=>e.logic&&!Object.keys(e.logic).length?(({logic,...rest})=>rest)(e):e);
  if(CONTROL_VALUES[key]!==undefined) return finishControl(next,key,ids);
  validateScreenBudget(next);
  return {screen:next,ids,group:{id:`g_${ids[0]}`.slice(0,32),members:ids}};
}

/** A white keycap with a darker base and a black letter: the boss prompt look. */
function keyCap(styled,wide) {
  const w=wide?.82:.32,h=.56,y=-.3;
  return [styled('decoration','cap_base','',0,y-.045,w,h,{fillColor:'#5c5c5c',borderColor:'#000000',outlineWidth:2,radius:.2},{background:true,border:true,shape:'rounded',layer:1}),
    styled('decoration','cap_face','',0,y,w,h,{fillColor:'#ffffff',gradientColor:'#d9d9d9',borderColor:'#000000',outlineWidth:2,radius:.2},{background:true,border:true,shape:'rounded',layer:2}),
    styled('label',wide?'key_word':'key',wide?'{key_word}':'{key}',0,y,w,h,{textColor:'#111111',textScale:wide?3:4},{layer:3})];
}

/** {@code logic} with every element reference (elements, element, show, hide, tooltip) renamed by {@code map}. */
export function retarget(logic,map) {
  if(Array.isArray(logic))return logic.map(v=>retarget(v,map));
  if(!logic||typeof logic!=='object')return logic;
  const out={};
  for(const [k,v] of Object.entries(logic)) {
    if(['elements','show','hide'].includes(k)&&Array.isArray(v))out[k]=v.map(id=>map.get(id)??id);
    else if(['element','tooltip'].includes(k)&&typeof v==='string')out[k]=map.get(v)??v;
    else out[k]=retarget(v,map);
  }
  return out;
}

/** The schema 9 components: each shows one of the newer features ready to use (and to try with Probar). */
function newComponents(styled) {
  const glass={fillColor:'#0c1824',borderColor:'#bfe9ff',textColor:'#ffffff',opacity:.82,backdropBlur:6,radius:.14,textCase:'original'};
  const button={fillColor:'#173b48',borderColor:'#8bdfc9',textColor:'#edf8f4',radius:.14,hoverColor:'#53dbc1',hoverEffect:'glow',textCase:'original'};
  const text={textColor:'#ffffff',textCase:'original'}, note={textColor:'#bfe9ff',textCase:'original'};
  const withLogic=(e,logic)=>({...e,logic});
  return {
    'glass-panel':()=>[styled('decoration','glass','',0,0,1.2,.6,glass,{background:true,border:true,shape:'rounded'}),
      styled('label','glass_text','Vidrio esmerilado',0,.12,1.1,.14,text,{layer:1}),
      styled('label','glass_note','Suaviza lo que tiene debajo',0,-.08,1.1,.12,note,{layer:1})],
    'glow-button':()=>[withLogic(styled('button','shine','Brilla al pasar',0,0,.8,.18,button,{background:true,border:true,shape:'rounded'}),
      {'on-hover':[{type:'sound',value:'UI_BUTTON_CLICK 0.4 1.6'}],actions:[{type:'message',value:'<aqua>¡Hola {player}!'}]})],
    popup:()=>[withLogic(styled('button','popup_open','Abrir ventana',0,-.5,.8,.18,button,{background:true,border:true,shape:'rounded'}),
        {actions:[{type:'toggle',elements:['popup_panel','popup_text']}]}),
      withLogic(styled('decoration','popup_panel','',0,.1,1.1,.6,{...glass,backdropBlur:10,shadow:true,shadowBlur:4},{background:true,border:true,shape:'rounded',layer:5}),{hidden:true}),
      withLogic(styled('label','popup_text','Ventana emergente\nTocá el botón otra vez',0,.1,1,.3,{...text,wrapWidth:120},{layer:6}),{hidden:true})],
    'tooltip-button':()=>[withLogic(styled('button','tip_button','Pasá el cursor',0,-.2,.8,.18,button,{background:true,border:true,shape:'rounded'}),{tooltip:'tip_text'}),
      styled('label','tip_text','Esto es un tooltip',0,.02,.9,.14,{...glass,backdropBlur:0,opacity:.92},{background:true,border:true,shape:'rounded',layer:4})],
    'data-list':()=>[withLogic(styled('button','list','{index}. {item}',0,.45,1,.16,button,{background:true,border:true,shape:'rounded'}),
      {list:{source:'servers',rows:3,spacing:16,'on-select':[{type:'message',value:'<green>Elegiste {item}'}]}}),
      // One short line: the full explanation is in the inspector's Datos tab.
      styled('label','list_note','Datos: la variable servers',0,-.08,1.2,.1,note,{layer:1})],
    switch:()=>[styled('label','switch_text','Opción',-.25,0,.5,.12,{...text,textAlign:'left'},{layer:1}),
      withLogic(styled('button','switch','{__var__}',.25,0,.36,.16,button,{background:true,border:true,shape:'rounded',layer:1}),
        {actions:[{type:'condition',value:'{__var__} == Sí',then:[{type:'variable',variable:'__var__',value:'No'}],else:[{type:'variable',variable:'__var__',value:'Sí'}]}]})],
    checkbox:()=>[withLogic(styled('button','checkbox','{__var__} Recordarme',0,0,.8,.16,{...button,textAlign:'left'},{background:true,border:true,shape:'rounded',layer:1}),
        {actions:[{type:'condition',value:'{__var__} == [ ]',then:[{type:'variable',variable:'__var__',value:'[✔]'}],else:[{type:'variable',variable:'__var__',value:'[ ]'}]}]})],
    stepper:()=>[withLogic(styled('button','stepper_less','-',-.4,0,.2,.16,button,{background:true,border:true,shape:'rounded',layer:1}),
        {actions:[{type:'condition',value:'{__var__} > 0',then:[{type:'variable',variable:'__var__',operation:'subtract',value:'1'}]}]}),
      styled('label','stepper_value','Volumen: {__var__}',0,0,.56,.12,text,{layer:1}),
      withLogic(styled('button','stepper_more','+',.4,0,.2,.16,button,{background:true,border:true,shape:'rounded',layer:1}),
        {actions:[{type:'condition',value:'{__var__} < 10',then:[{type:'variable',variable:'__var__',operation:'add',value:'1'}]}]})],
    cycle:()=>[withLogic(styled('button','cycle','< {__var__} >',0,0,.62,.16,button,{background:true,border:true,shape:'rounded',layer:1}),
        {actions:[{type:'condition',value:'{__var__} == Fácil',then:[{type:'variable',variable:'__var__',value:'Normal'}],
          else:[{type:'condition',value:'{__var__} == Normal',then:[{type:'variable',variable:'__var__',value:'Difícil'}],else:[{type:'variable',variable:'__var__',value:'Fácil'}]}]}]})],
    // A question in the page's own style (idea 26): the button shows it; Sí hides it and runs its own actions (edit them), No just hides it.
    'confirm-box':()=>{
      const box=['confirm_panel','confirm_text','confirm_yes','confirm_no'], hide={type:'hide',elements:box};
      return [withLogic(styled('button','confirm_open','Comprar',0,-.62,.7,.16,button,{background:true,border:true,shape:'rounded'}),{actions:[{type:'show',elements:box}]}),
        withLogic(styled('decoration','confirm_panel','',0,.08,1.1,.62,{...glass,backdropBlur:10,shadow:true,shadowBlur:4},{background:true,border:true,shape:'rounded',layer:5}),{hidden:true}),
        withLogic(styled('label','confirm_text','¿Seguro? Cuesta 100.',0,.24,1,.14,{...text},{layer:6}),{hidden:true}),
        withLogic(styled('button','confirm_yes','Sí',-.24,-.06,.36,.16,button,{background:true,border:true,shape:'rounded',layer:6}),
          {hidden:true,actions:[hide,{type:'message',value:'<green>¡Hecho!'}]}),
        withLogic(styled('button','confirm_no','No',.24,-.06,.36,.16,{...button,fillColor:'#3a2430',borderColor:'#f0a0b8',hoverColor:'#ff7aa0'},{background:true,border:true,shape:'rounded',layer:6}),
          {hidden:true,actions:[hide]})];
    },
    // Three tabs over a panel (idea 36): each shows its own content, and the bar under the tabs slides to the one chosen.
    tabs:()=>{
      const names=['General','Tienda','Ajustes'], step=.44, contents=names.map((_,i)=>`tab_content_${i+1}`);
      return [styled('decoration','tab_panel','',0,-.08,1.44,.7,glass,{background:true,border:true,shape:'rounded'}),
        ...names.map((name,i)=>withLogic(styled('button',`tab_${i+1}`,name,(i-1)*step,.36,.4,.14,{...button,radius:.3},{background:true,border:true,shape:'rounded',layer:1}),
          {actions:[{type:'show',elements:[contents[i]]},{type:'hide',elements:contents.filter((_,j)=>j!==i)},{type:'offset',element:'tab_marker',dx:String(Math.round(i*step*128))}]})),
        styled('decoration','tab_marker','',-step,.27,.32,.02,{fillColor:'#53dbc1',borderColor:'#53dbc1',textColor:'#ffffff',radius:.5},{background:true,border:false,shape:'rounded',layer:2}),
        ...names.map((name,i)=>withLogic(styled('label',contents[i],`Contenido de ${name}`,0,-.1,1.3,.14,{...text},{layer:1}),i?{hidden:true}:{}))];
    },
    // A 16-pixel item icon in the lower part of the menu, where the game can draw it.
    'item-icon':()=>[{...styled('decoration','item_icon','',0,-.4,.125,.2222,{...text},{}),item:'diamond'}],
    gif:()=>[withLogic(styled('decoration','gif','',0,.05,.5,.5,{...text},{}),{frames:{folder:'mi_animacion'}}),
      styled('label','gif_note','GIF: images/mi_animacion.gif',0,-.3,1.2,.1,note,{layer:1})]
  };
}

/** A control's own page variable: named after its first element, declared global (so variables.saved can keep it). */
function finishControl(screen,key,ids) {
  const used=new Set(Object.keys(screen.logic?.variables??{}).map(name=>name.replace(/^[*$]/,'')));
  const base=`v_${key}`;
  let name=base,n=2;
  while(used.has(name)){const suffix=`_${n++}`;name=base.slice(0,24-suffix.length)+suffix;}
  const rename=value=>typeof value==='string'?value.replaceAll('__var__',name):Array.isArray(value)?value.map(rename)
    :value&&typeof value==='object'?Object.fromEntries(Object.entries(value).map(([k,v])=>[k,rename(v)])):value;
  screen.elements=screen.elements.map(e=>ids.includes(e.id)?{...e,text:rename(e.text),...(e.logic?{logic:rename(e.logic)}:{})}:e);
  const next=setScreenLogic(screen,{...(screen.logic??{}),variables:{...(screen.logic?.variables??{}),[`*${name}`]:CONTROL_VALUES[key]}});
  validateScreenBudget(next);
  return {screen:next,ids,group:{id:`g_${ids[0]}`.slice(0,32),members:ids},variable:name};
}
