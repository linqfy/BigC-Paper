import test from 'node:test';
import assert from 'node:assert/strict';
import * as workspace from './workspace.mjs';
import { createScreen, upgradeAppearance } from './model.mjs';
const storage = () => { const values = new Map(); return { values, getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }; };
const fresh = id => ({ ...createScreen(), id });
const edited = (screen, text) => ({ ...screen, title: text });

test('workspace creates one v4 page with complete original preferences and editor metadata', () => {
  const value = workspace.createProject(fresh('menu'));
  assert.equal(value.format, 'tideframe-project'); assert.equal(value.version, 1); assert.equal(value.activePageId, 'menu');
  assert.deepEqual(value.pages[0].hiddenIds, []); assert.deepEqual(value.pages[0].lockedIds, []); assert.deepEqual(value.pages[0].groups, []);
  assert.equal(value.preferences.primaryColor, '#96e4da'); assert.equal(value.preferences.secondaryColor, '#102939'); assert.equal(value.preferences.zoom, 1);
  assert.deepEqual(workspace.parseProject(workspace.serializeProject(value)), value);
});
test('project reader rejects unknown/duplicate keys, duplicate page IDs and fractional raw integers', () => {
  const value = workspace.createProject(), raw = workspace.serializeProject(value);
  assert.throws(() => workspace.parseProject(raw.replace('"version": 1', '"version": 1, "version": 1')), /duplicate/i);
  assert.throws(() => workspace.parseProject(raw.replace('"version": 1', '"version": 1.000000000000000001')), /version|integer/i);
  assert.throws(() => workspace.parseProject(raw.replace('"width": 512', '"width": 511.9999999999999999999')), /integer/i);
  assert.throws(() => workspace.validateProject({ ...value, extra: true }), /keys/i);
  assert.throws(() => workspace.validateProject({ ...value, pages: [...value.pages, value.pages[0]] }), /duplicate/i);
});
test('metadata IDs and disjoint groups must refer to actual elements', () => {
  const value = workspace.createProject();
  for (const change of [{ hiddenIds: ['missing'] }, { lockedIds: ['start', 'start'] }, { groups: [{ id: 'one', members: ['start'] }, { id: 'two', members: ['start'] }] }, { groups: [{ id: 'one', members: ['missing'] }] }]) assert.throws(() => workspace.validateProject({ ...value, pages: [{ ...value.pages[0], ...change }] }), /unknown|duplicate|group|member/i);
});
test('visible export removes hidden elements while locked and grouping stay editor only', () => {
  const value = workspace.createProject(), page = { ...value.pages[0], hiddenIds: ['start'], lockedIds: ['close'], groups: [{ id: 'controls', members: ['start', 'close'] }] };
  const exported = workspace.exportVisibleScreen(page);
  assert.deepEqual(exported.elements.map(e => e.id), ['heading', 'close']); assert.deepEqual(Object.keys(exported).sort(), ['elements', 'id', 'quality', 'title', 'version']);
  assert.equal(page.screen.elements.length, 3);
});
test('history stores one transaction per gesture and dirty tracking follows saved state', () => {
  const memory = storage(), state = workspace.createWorkspace(fresh('menu'), { storage: memory });
  assert.equal(state.dirty, false); state.begin('Drag'); state.commitScreen(edited(state.activePage().screen, 'ONE')); state.commitScreen(edited(state.activePage().screen, 'TWO')); state.commit();
  assert.equal(state.undoCount, 1); assert.equal(state.dirty, true); state.undo(); assert.equal(state.activePage().screen.title, 'TIDEFRAME'); assert.equal(state.dirty, false);
  state.redo(); assert.equal(state.activePage().screen.title, 'TWO'); assert.equal(state.save().ok, true); assert.equal(state.dirty, false);
  state.undo(); assert.equal(state.dirty, true); state.redo(); assert.equal(state.dirty, false);
});
test('history is bounded to40 and a new edit invalidates redo', () => {
  const state = workspace.createWorkspace();
  for (let i=0; i<50; i++) state.commitScreen(edited(state.activePage().screen, `EDIT ${i}`));
  assert.equal(state.undoCount, 40); state.undo(); state.commitScreen(edited(state.activePage().screen, 'OTHER')); assert.equal(state.redoCount, 0);
});
test('invalid drafts and failed transactions retain valid history without changing supplied draft', () => {
  const state = workspace.createWorkspace(), before = state.project;
  const draft = { ...state.activePage().screen, title: '' };
  assert.throws(() => state.commitScreen(draft), /title/i); assert.equal(state.project, before); assert.equal(draft.title, ''); assert.equal(state.undoCount, 0);
  state.begin('Resize'); state.commitScreen(edited(state.activePage().screen, 'VALID')); state.cancel(); assert.equal(state.project, before); assert.equal(state.undoCount, 0);
});
test('pages add duplicate rename and close participate in undo and maintain a nonempty workspace', () => {
  const state = workspace.createWorkspace(); state.addPage(); const added = state.activePage().screen.id; assert.notEqual(added, 'menu');
  state.duplicatePage(added); const duplicated = state.activePage().screen.id; assert.notEqual(duplicated, added); state.renamePage(duplicated, 'profile', 'PROFILE'); assert.equal(state.activePage().screen.title, 'PROFILE');
  state.closePage('profile', { discard: true }); assert.equal(state.project.pages.length, 2); state.undo(); assert.equal(state.activePage().screen.id, 'profile');
  state.closeAll({ discard: true }); assert.equal(state.project.pages.length, 1); state.undo(); assert.equal(state.project.pages.length, 3);
  const single = workspace.createWorkspace(); single.closePage('menu', { discard: true }); assert.equal(single.project.pages.length, 1);
});
test('metadata updates are undoable and commit prunes deleted IDs with optional renaming', () => {
  const state = workspace.createWorkspace(); state.setHidden(['start'], true); state.setLocked(['close'], true); state.setGroups([{ id:'controls', members:['start','close'] }]);
  const screen = { ...state.activePage().screen, elements: state.activePage().screen.elements.map(e => e.id === 'start' ? { ...e, id: 'renamed' } : e).filter(e => e.id !== 'close') };
  state.commitScreen(screen, 'Rename', { idRenames: { start: 'renamed' } });
  assert.deepEqual(state.activePage().hiddenIds, ['renamed']); assert.deepEqual(state.activePage().lockedIds, []); assert.deepEqual(state.activePage().groups, [{ id:'controls', members:['renamed'] }]);
});
test('preferences strictly validate booleans ranges colors and persist as editor only', () => {
  const state = workspace.createWorkspace(); state.setPreferences({ zoom: 2, showHotbar: true, snapStep: .05 }); assert.equal(state.project.preferences.zoom, 2);
  for (const patch of [{ zoom: .1 }, { snapStep: 0 }, { gridVisible: 'true' }, { primaryColor: '#fff' }, { unknown: true }]) assert.throws(() => state.setPreferences(patch), /preference|keys|zoom|snap|color/i);
});
test('storage quota and corrupt restore keep current project old durable data and dirty state', () => {
  const memory = storage(), state = workspace.createWorkspace(undefined, { storage: memory }); assert.equal(state.save().ok, true); const old = memory.getItem(state.storageKey);
  state.commitScreen(edited(state.activePage().screen, 'UNSAVED')); memory.setItem = () => { throw Error('QuotaExceededError'); };
  assert.equal(state.save().ok, false); assert.equal(state.dirty, true); assert.equal(memory.getItem(state.storageKey), old);
  memory.values.set(state.storageKey, '{broken'); const before = state.project; assert.equal(state.restore().ok, false); assert.equal(state.project, before); assert.equal(state.dirty, true);
});
test('closing with durable backup fails atomically on quota and preserves consecutive closed snapshots', () => {
  const memory = storage(), state = workspace.createWorkspace(undefined, { storage: memory }); state.addPage(fresh('profile'));
  const originalSet = memory.setItem; memory.setItem = () => { throw Error('QuotaExceededError'); }; const before = state.project;
  assert.throws(() => state.closePage('profile', { persistBackup: true }), /backup|quota/i); assert.equal(state.project, before);
  memory.setItem = originalSet; state.closePage('profile', { persistBackup: true }); state.closePage('menu', { persistBackup: true });
  assert.equal(state.listBackups().length, 2); assert.equal(state.listBackups()[0].project.pages.length, 2);
});
test('project loading validates all pages before backup or mutation and safely restores saved projects', () => {
  const memory = storage(), state = workspace.createWorkspace(undefined, { storage: memory }); const before = state.project;
  assert.throws(() => state.loadProject({ ...workspace.createProject(), activePageId: 'missing' }, { persistBackup: true }), /active/i); assert.equal(state.project, before); assert.equal(memory.values.size, 0);
  const incoming = workspace.createProject(fresh('incoming')); state.loadProject(incoming, { persistBackup: true }); assert.equal(state.project.activePageId, 'incoming'); assert.equal(state.listBackups().length, 1);
  state.save(); state.commitScreen(edited(state.activePage().screen, 'CHANGED')); assert.equal(state.restore().ok, true); assert.equal(state.activePage().screen.title, 'TIDEFRAME'); assert.equal(state.dirty, false);
});
test('project enforces16 pages and128MiB before parse and groups cannot carry unknown data', () => {
  const project = workspace.createProject(); const pages = Array.from({ length:16 }, (_,i) => ({ ...project.pages[0], screen:fresh(`page${i}`) }));
  workspace.validateProject({ ...project, pages, activePageId:'page0' }); assert.throws(() => workspace.validateProject({ ...project, pages:[...pages,{ ...pages[0], screen:fresh('overflow') }], activePageId:'page0' }), /16/i);
  assert.throws(() => workspace.parseProject(' '.repeat(134217729)), /128 MiB/i);
  assert.throws(() => workspace.validateProject({ ...project, pages:[{ ...project.pages[0], groups:[{ id:'g', members:['start'], url:'https://example.com' }] }] }), /keys/i);
});

test('project publication validates each distinct PNG content before replacing any workspace', async () => {
  assert.equal(typeof workspace.parseProjectForPublication, 'function');
  const value = workspace.createProject(); value.pages[0].screen.elements[0].image = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACUlEQVR4nGMAAAABAAFe/335AAAAAElFTkSuQmCC';
  let decoded = false; await assert.rejects(workspace.parseProjectForPublication(JSON.stringify(value), async () => { decoded = true; }), /scanline|inflated/i); assert.equal(decoded, false);
});
test('export validates only visible action areas and rejects invalid hidden metadata', () => {
  const value = workspace.createProject(), page = value.pages[0]; page.screen.elements[2] = { ...page.screen.elements[2], x: page.screen.elements[1].x, y: page.screen.elements[1].y };
  assert.throws(() => workspace.exportVisibleScreen(page), /overlap/i);
  page.hiddenIds = ['close']; assert.equal(workspace.exportVisibleScreen(page).elements.length, 2);
  assert.throws(() => workspace.exportVisibleScreen({ ...page, hiddenIds: ['missing'] }), /unknown/i);
});
test('backup archive never evicts prior closed projects and fails safely when corrupt or full', () => {
  const memory = storage(), state = workspace.createWorkspace(undefined, { storage: memory });
  for (let i=0; i<40; i++) assert.equal(state.backup(`BACKUP ${i}`).ok, true);
  const original = memory.getItem(state.backupKey); assert.equal(state.backup('TOO MANY').ok, false); assert.equal(memory.getItem(state.backupKey), original);
  const before = state.project; memory.values.set(state.backupKey, '{broken'); assert.throws(() => state.closeAll({ persistBackup:true }), /backup/i); assert.equal(state.project,before);
});
test('unavailable storage and pending gestures cannot claim saved or close without preserving data', () => {
  const state = workspace.createWorkspace(); assert.equal(state.save().ok,false); assert.throws(() => state.closeAll({ persistBackup:true }), /backup|storage/i);
  const memory = storage(), saved = workspace.createWorkspace(undefined,{storage:memory}); saved.begin('Drag'); saved.commitScreen(edited(saved.activePage().screen,'DRAFT')); assert.equal(saved.save().ok,false); assert.equal(memory.values.size,0); saved.cancel();
});

test('editing one page shares untouched immutable page snapshots and PNG strings across history', () => {
  const initial = fresh('menu'), image = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPgUbLwAwABWQC1RssqBwAAAABJRU5ErkJggg=='; initial.elements[0].image = image;
  const state = workspace.createWorkspace(initial); const originalPage = state.activePage(); state.addPage(fresh('profile'));
  assert.equal(state.project.pages[0], originalPage);
  state.commitScreen(edited(state.activePage().screen,'PROFILE EDIT')); assert.equal(state.project.pages[0], originalPage);
  assert.equal(originalPage.screen.elements[0].image,image);
  assert.equal(Object.isFrozen(originalPage.screen.elements[0].style),true); assert.throws(() => { originalPage.screen.title='MUTATED'; }, TypeError);
});

test('external shallow frozen screens cannot bypass validation after their children change', () => {
  const project = workspace.createProject(); Object.freeze(project.pages[0].screen); workspace.validateProject(project);
  project.pages[0].screen.elements[0].width = 0; assert.throws(() => workspace.validateProject(project), /width/i);
});
test('published project validates shared images once across all pages', async () => {
  const project = workspace.createProject(), image = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGPgUbLwAwABWQC1RssqBwAAAABJRU5ErkJggg==';
  project.pages[0].screen.elements[0].image=image; project.pages.push({ ...project.pages[0], screen:{ ...project.pages[0].screen,id:'profile' } });
  let checks=0; const imported=await workspace.parseProjectForPublication(workspace.serializeProject(project),async () => { checks++; });
  assert.equal(imported.pages.length,2); assert.equal(checks,1); assert.equal(workspace.projectByteLength(project),new TextEncoder().encode(workspace.serializeProject(project)).length);
});

test('project v5 rejects fractional raw appearance integers before Number rounding', () => {
  const project = workspace.createProject(upgradeAppearance(createScreen())), raw = JSON.stringify(project);
  for (const field of ['outlineWidth', 'wrapWidth']) {
    const invalid = raw.replace(`"${field}":${field === 'outlineWidth' ? 1 : 0}`, `"${field}":0.99999999999999999999`);
    assert.throws(() => workspace.parseProject(invalid), /exact integer/i);
  }
  const valid = raw.replace('"outlineWidth":1', '"outlineWidth":1e0'); assert.equal(workspace.parseProject(valid).pages[0].screen.elements[0].style.outlineWidth,1);
});
test('renamePage atomically retargets exact internal open links and preserves all metadata', () => {
  const state = workspace.createWorkspace(); state.commitScreen({ ...state.activePage().screen,elements:state.activePage().screen.elements.map(e=>e.id==='start'?{...e,action:'open:profile'}:e) });
  state.setHidden(['heading'],true); state.setLocked(['start'],true); state.setGroups([{id:'controls',members:['start','close']}]);
  const profile = fresh('profile'); profile.elements[1].action='open:profile'; profile.elements[2].action='open:profile_extra'; state.addPage(profile);
  const before=state.project, count=state.undoCount; state.renamePage('profile','character','CHARACTER');
  const menu=state.project.pages.find(p=>p.screen.id==='menu'), character=state.activePage();
  assert.equal(menu.screen.elements.find(e=>e.id==='start').action,'open:character'); assert.equal(character.screen.elements[1].action,'open:character'); assert.equal(character.screen.elements[2].action,'open:profile_extra');
  assert.deepEqual(menu.hiddenIds,['heading']); assert.deepEqual(menu.lockedIds,['start']); assert.deepEqual(menu.groups,[{id:'controls',members:['start','close']}]);
  assert.deepEqual(menu.screen.elements.map(e=>e.id),before.pages[0].screen.elements.map(e=>e.id)); assert.equal(state.undoCount,count+1);
  state.undo(); assert.equal(state.project,before); state.redo(); assert.equal(state.project.pages[0].screen.elements[1].action,'open:character');
  const valid=state.project; assert.throws(()=>state.renamePage('character','menu'),/duplicate/i); assert.equal(state.project,valid);
});
test('commitScreen page ID edits use the same atomic link retargeting as renamePage', () => {
  const state=workspace.createWorkspace(); const first={...state.activePage().screen,elements:state.activePage().screen.elements.map(e=>e.id==='start'?{...e,action:'open:menu'}:e)}; state.commitScreen(first);
  state.addPage(fresh('profile')); state.commitScreen({...state.activePage().screen,elements:state.activePage().screen.elements.map(e=>e.id==='start'?{...e,action:'open:menu'}:e)}); state.setActivePage('menu');
  const before=state.project; state.commitScreen({...state.activePage().screen,id:'home'},'Rename page');
  assert.equal(state.project.activePageId,'home'); assert.ok(state.project.pages.every(p=>p.screen.elements.find(e=>e.id==='start').action==='open:home'));
  state.undo(); assert.equal(state.project,before);
});
