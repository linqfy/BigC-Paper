import test from 'node:test';
import assert from 'node:assert/strict';
import { serializeScreen, parseScreen, validateActionAreas } from './model.mjs';
import { PAGE_TEMPLATES, pageFromTemplate } from './page-templates.mjs';
import { reviewScreen } from './checker.mjs';
import { startPlay, click, resolve } from './play.mjs';

test('every page template is a valid schema 9 page with nothing for the checker to warn about', () => {
  for (const { id } of PAGE_TEMPLATES) {
    const screen = pageFromTemplate(id);
    assert.equal(screen.version, 9, id);
    assert.doesNotThrow(() => parseScreen(serializeScreen(screen)), id);
    const warnings = reviewScreen(screen, { pages: PAGE_TEMPLATES.map(t => t.id) }).filter(f => f.level === 'warning');
    assert.deepEqual(warnings.map(f => f.message), [], id);
  }
  assert.throws(() => pageFromTemplate('nope'), /Unknown original template/);
});

test('a template whose id is taken gets the next free id', () => {
  assert.equal(pageFromTemplate('tienda', ['tienda']).id, 'tienda_2');
  assert.equal(pageFromTemplate('tienda', ['tienda', 'tienda_2']).id, 'tienda_3');
});

test('the templates work in Probar: buying, switches, the language cycle and servers', () => {
  const shop = startPlay({ pages: [{ screen: pageFromTemplate('tienda') }] }, 'tienda');
  click(shop, 'comprar_espada');
  assert.equal(shop.balance, 1150);
  assert.ok(shop.log.some(e => e.text === 'Daría: DIAMOND_SWORD 1'));
  const settings = startPlay({ pages: [{ screen: pageFromTemplate('ajustes') }] }, 'ajustes');
  assert.equal(resolve(settings, '{musica}'), 'Sí');
  click(settings, 'cambiar_musica'); assert.equal(resolve(settings, '{musica}'), 'No');
  click(settings, 'cambiar_musica'); assert.equal(resolve(settings, '{musica}'), 'Sí');
  for (const expected of ['English', 'Português', 'Español']) { click(settings, 'cambiar_idioma'); assert.equal(resolve(settings, '{idioma}'), expected); }
  const servers = startPlay({ pages: [{ screen: pageFromTemplate('servidores') }] }, 'servidores');
  click(servers, 'entrar_skyblock');
  assert.equal(servers.closed, true); assert.match(servers.log[0].text, /servidor skyblock/);
});
