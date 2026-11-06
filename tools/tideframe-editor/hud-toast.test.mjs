// 0.23 ideas 24 and 22 in the editor: HUD pages that stack, the hud and toast actions (format, Probar, Revisar) and
// the toast page templates.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, upgradeLogic, validateScreen, setElementLogic, serializeScreen, parseScreen } from './model.mjs';
import { setPresentation } from './presentation-tools.mjs';
import { startPlay, click, toastSeconds } from './play.mjs';
import { reviewProject } from './checker.mjs';
import { PAGE_TEMPLATES, pageFromTemplate } from './page-templates.mjs';
import { ACTION_RECIPES } from './advanced-settings.mjs';
import { RECIPE_TEXT, es } from './i18n.mjs';

const overlay = () => setPresentation(createScreen(), { mode: 'overlay' });

test('only a HUD page stacks, as a schema 9 true or false that the menu mode drops', () => {
  const stacked = setPresentation(overlay(), { stack: true });
  assert.equal(stacked.version, 9);
  assert.equal(stacked.presentation.stack, true);
  assert.equal(parseScreen(serializeScreen(stacked)).presentation.stack, true);
  assert.equal(setPresentation(stacked, { stack: false }).presentation.stack, undefined, 'false is not written');
  assert.equal(setPresentation(stacked, { mode: 'menu' }).presentation.stack, undefined, 'a page turned into a menu stops stacking');
  assert.throws(() => setPresentation(createScreen(), { stack: true }), /HUD pages/);
  assert.throws(() => validateScreen({ ...stacked, presentation: { ...stacked.presentation, mode: 'menu' } }), /HUD pages/);
  assert.throws(() => validateScreen({ ...stacked, presentation: { ...stacked.presentation, stack: 'yes' } }), /true or false/);
  assert.throws(() => validateScreen({ ...setPresentation(createScreen(), { mode: 'overlay' }), presentation: { mode: 'overlay', scale: 1, cursor: 'cross', stack: true } }), /Presentation/, 'schema 6 has no stack');
  assert.equal(es('Passive overlay / 1x game HUD / cursor disabled / stacks with other HUD pages'), 'HUD pasivo / 1x en el juego / sin cursor / se suma a los otros HUD');
});

test('hud and toast actions follow the plugin format rules', () => {
  const base = upgradeLogic(createScreen()), with_ = actions => setElementLogic(base, 'start', { actions });
  assert.doesNotThrow(() => with_([{ type: 'hud', value: 'hud_vida' }, { type: 'hud', value: 'hud_vida', mode: 'REMOVE' },
    { type: 'toast', value: 'aviso', seconds: '{t}', values: { titulo: 'Hola {player}', '*total': 3, ok: true } }]));
  assert.throws(() => with_([{ type: 'hud', value: 'x', mode: 'toggle' }]), /add or remove/);
  assert.throws(() => with_([{ type: 'toast', value: 'x', values: { 'Bad Name': '1' } }]), /variable name/);
  assert.throws(() => with_([{ type: 'toast', value: 'x', values: { titulo: 'x'.repeat(257) } }]), /256/);
  assert.throws(() => with_([{ type: 'toast', value: 'x', values: Object.fromEntries(Array.from({ length: 17 }, (_, i) => [`v${i}`, '1'])) }]), /16/);
  assert.throws(() => with_([{ type: 'toast', value: 'x', values: ['a'] }]), /16 variable names/);
  for (const id of ['hud', 'hud-remove', 'toast']) {
    const recipe = ACTION_RECIPES.find(r => r.id === id);
    assert.ok(recipe && RECIPE_TEXT[id], id);
    assert.doesNotThrow(() => with_([recipe.action]), id);
  }
});

test('Probar notes hud and toast actions like the plugin runs them, with toast seconds read the same way', () => {
  for (const [given, seconds] of [['4', 4], ['90', 60], ['0.5', 1], ['2.5', 2.5], [' 1e1 ', 10], ['3d', 3], ['abc', 4], ['', 4], ['NaN', 4], ['Infinity', 4], ['0x10', 4]])
    assert.equal(toastSeconds(given), seconds, `"${given}"`);
  const menu = setElementLogic(upgradeLogic(createScreen()), 'start', { actions: [{ type: 'hud', value: 'hud_{which}' },
    { type: 'toast', value: 'aviso', seconds: '{t}', values: { titulo: 'Hola {player}' } }, { type: 'hud', page: 'hud_vida', mode: 'remove' }] });
  const session = startPlay({ pages: [{ screen: { ...menu, logic: { ...menu.logic, variables: { which: 'vida', t: '2' } } } }] }, 'menu', { now: 0, events: [] });
  click(session, 'start');
  assert.deepEqual(session.events.filter(e => /^(hud|toast)/.test(e)), ['hud add hud_vida', 'toast aviso', 'hud remove hud_vida']);
  const notes = session.log.map(entry => entry.text).join('\n');
  assert.match(notes, /Sumaría la página HUD hud_vida/);
  assert.match(notes, /Mostraría el aviso aviso durante 2 s \(titulo = Hola Steve\); en el juego entra cuando se cierra el menú/);
  assert.match(notes, /Quitaría la página HUD hud_vida/);
});

test('Revisar checks the pages hud and toast show, and counts them as reached', () => {
  const button = (id, actions) => setElementLogic(upgradeLogic(createScreen()), 'start', { actions });
  const menu = button('menu', [{ type: 'toast', value: 'aviso' }, { type: 'hud', value: 'tienda' }, { type: 'toast', value: 'otro_server' }, { type: 'hud', value: 'aviso', mode: 'remove' }, { type: 'toast', value: '{page}' }]);
  const tienda = { ...upgradeLogic(createScreen()), id: 'tienda' }, aviso = pageFromTemplate('aviso');
  const found = reviewProject({ pages: [menu, tienda, aviso].map(screen => ({ screen })) });
  const messages = found.map(f => `${f.level} ${f.page}: ${f.message}`);
  assert.ok(messages.some(m => m.startsWith('warning menu') && m.includes('la página HUD "tienda"') && m.includes('es un menú')), messages.join('\n'));
  assert.ok(messages.some(m => m.startsWith('info menu') && m.includes('el aviso "otro_server"')), messages.join('\n'));
  assert.ok(!messages.some(m => m.includes('"aviso"') || m.includes('{page}')), 'a HUD page of the project and a token are fine');
  assert.ok(!messages.some(m => m.startsWith('info aviso') && m.includes('no se abre')), 'a toast page shown by an action is reached');
});

test('toast templates stack, slide in from the left and carry the texts the toast action fills', () => {
  for (const id of ['aviso', 'logro']) {
    assert.ok(PAGE_TEMPLATES.some(t => t.id === id));
    const screen = pageFromTemplate(id);
    assert.deepEqual([screen.presentation.mode, screen.presentation.stack], ['overlay', true], id);
    assert.deepEqual(screen.logic.animation.open, { effect: 'slide', from: 'left', distance: 48, 'duration-ms': 300, easing: 'ease-out' });
    assert.equal(screen.logic.animation.close.easing, 'ease-in');
    assert.deepEqual(Object.keys(screen.logic.variables), ['titulo', 'texto']);
    assert.ok(screen.elements.some(e => e.text === '{titulo}') && screen.elements.some(e => e.text === '{texto}'), id);
    assert.ok(!screen.elements.some(e => e.kind === 'button'), 'a passive HUD page has no buttons');
    assert.deepEqual(parseScreen(serializeScreen(screen)), screen);
  }
});
