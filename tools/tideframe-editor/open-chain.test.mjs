// Probar stops pages that keep opening each other from their on-open actions, as LogicController.MAX_OPEN_CHAIN does.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createScreen, upgradeLogic, setScreenLogic } from './model.mjs';
import { startPlay } from './play.mjs';

const pageOpening = (id, target) => setScreenLogic({ ...upgradeLogic(createScreen()), id }, { 'on-open': [{ type: 'redirect', value: target }] });

test('a page that opens itself, or two that open each other, stop after 8 openings instead of overflowing', () => {
  for (const pages of [[pageOpening('a', 'a')], [pageOpening('a', 'b'), pageOpening('b', 'a')]]) {
    let session;
    assert.doesNotThrow(() => { session = startPlay({ pages: pages.map(screen => ({ screen })) }, 'a', { now: 0, events: [] }); });
    assert.ok(session.log.some(entry => entry.kind === 'error' && entry.text.includes('después de 8')), session.log.map(entry => entry.text).join('\n'));
    assert.equal(session.events.filter(event => event.startsWith('open ')).length, 8);
  }
});
