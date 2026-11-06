// Probar = plugin (idea 27): the shared cases of src/test/resources/cross run through Probar here and through the
// plugin's ScreenRuntime in CrossProbarTest.java; both must give exactly the expected events, variables and pages.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { startPlay, click, key, listRows, page, shown, damage, changeWorld, tick } from './play.mjs';

const folder = join(dirname(fileURLToPath(import.meta.url)), 'fixtures', 'cross');
const names = readFileSync(join(folder, 'index.txt'), 'utf8').split('\n').map(line => line.trim()).filter(Boolean);

test('there are shared cases to play', () => assert.ok(names.length >= 10));
for (const name of names) {
  const fixture = JSON.parse(readFileSync(join(folder, `${name}.json`), 'utf8'));
  test(`Probar plays the shared case ${name} like the plugin`, () => {
    const answers = [];
    const project = { pages: fixture.pages.map(screen => ({ screen })) };
    // Events are recorded from the very first on-open, as the plugin's host sees them.
    const session = startPlay(project, fixture.start ?? fixture.pages[0].id, { now: 0, events: [], confirm: () => answers.shift() === 'yes' });
    for (const step of fixture.steps) {
      if (step.click) click(session, step.click, { now: session.now });
      else if (step.right) click(session, step.right, { right: true, now: session.now });
      else if (step.row) {
        const [id, index] = step.row, e = page(session).elements.find(entry => entry.id === id);
        click(session, id, { row: listRows(session, e).items[index], now: session.now });
      } else if (step.key) key(session, step.key);
      else if (step.wait) session.now += step.wait;
      else if (step.answers) answers.push(...step.answers);
      else if (step.damage !== undefined) damage(session, step.damage, step.cause);
      else if (step.world) changeWorld(session, step.world);
      else if (step.timer !== undefined) tick(session, step.timer);
      else throw Error(`Unknown step ${JSON.stringify(step)}`);
    }
    const expect = fixture.expect;
    assert.deepEqual(session.events, expect.events, 'events');
    for (const [variable, value] of Object.entries(expect.variables ?? {}))
      assert.equal(session.locals.get(variable) ?? session.global.get(variable), value, `variable ${variable}`);
    for (const [id, visible] of Object.entries(expect.shown ?? {}))
      assert.equal(shown(session, page(session).elements.find(e => e.id === id)), visible, `shown ${id}`);
    assert.equal(session.pageId, expect.page, 'page');
    assert.equal(session.closed, expect.closed, 'closed');
  });
}
