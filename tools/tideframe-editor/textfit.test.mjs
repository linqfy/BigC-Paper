// Text that shrinks to fit (textfit.mjs) gives the plugin's answers: the shared cases of src/test/resources/textfit.json,
// also run by TextFitTest.java, plus the editor's own runs (colours, lines, old pages).
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { maxChars, sizeFor, cut, longest, liveSizes, lineTopQuarters, lineLeftQuarters, fitRuns, SIZES, ELLIPSIS } from './textfit.mjs';

const cases = JSON.parse(readFileSync(join(dirname(fileURLToPath(import.meta.url)), 'fixtures', 'textfit.json'), 'utf8'));
const element = ({ element: e }) => ({ id: 't', kind: 'label', text: e.text ?? '{player}', x: e.x, y: e.y, width: e.width, height: e.height, style: { textScale: e.textScale, textAlign: e.textAlign, wrapWidth: 0 } });

test('the same characters fit at each size as in the plugin', () => {
  for (const entry of cases.maxChars) assert.equal(maxChars(entry.width, entry.textScale, entry.size), entry.chars, JSON.stringify(entry));
});
test('the largest size that fits among those the pack has', () => {
  for (const entry of cases.sizes) assert.equal(sizeFor(element(entry), entry.length, entry.available ?? SIZES), entry.size, JSON.stringify(entry));
});
test('a line cut even at half size ends in an ellipsis', () => {
  for (const entry of cases.cut) assert.equal(cut(entry.line, entry.max), entry.shown, JSON.stringify(entry));
});
test('the longest a live text can be, and the smaller fonts it needs', () => {
  for (const entry of cases.longest) assert.equal(longest(entry.text), entry.chars, entry.text);
  for (const entry of cases.liveSizes) assert.deepEqual(liveSizes(element(entry)), entry.sizes, entry.element.text);
});
test('shrunk lines keep their centre and alignment', () => {
  for (const entry of cases.lineTop) assert.equal(lineTopQuarters(element(entry), entry.size), entry.quarters, JSON.stringify(entry));
  for (const entry of cases.lineLeft) assert.equal(lineLeftQuarters(element(entry), entry.length, entry.size), entry.quarters, JSON.stringify(entry));
});

const box = { id: 'hola', kind: 'label', x: -0.48, y: 0.265, width: 0.78, height: 0.12, style: { textScale: 1, wrapWidth: 0 } };
test('a long name shrinks on a schema 9 page and keeps its colours', () => {
  const runs = [{ text: 'HOLA ', color: null }, { text: 'LOCOBESTIAPARDA', color: '#55ffff' }];
  assert.deepEqual(fitRuns(box, runs, 9), { size: 3, runs }, '20 letters in a 16-letter box: three quarters, nothing cut');
  assert.deepEqual(fitRuns(box, runs, 8), { size: 4, runs }, 'an old page keeps cutting as before');
  assert.deepEqual(fitRuns(box, [{ text: 'HOLA', color: null }], 9), { size: 4, runs: [{ text: 'HOLA', color: null }] });
});
test('past half size each line is cut, and the ellipsis takes the colour of what it replaces', () => {
  const long = 'A'.repeat(30) + 'BBBBBB';
  const fitted = fitRuns(box, [{ text: long.slice(0, 30), color: '#ff0000' }, { text: long.slice(30), color: '#00ff00' }], 9);
  assert.equal(fitted.size, 2);
  assert.equal(fitted.runs.map(run => run.text).join(''), 'A'.repeat(30) + 'BB' + ELLIPSIS, '33 characters fit at half');
  assert.deepEqual(fitted.runs.map(run => run.color), ['#ff0000', '#00ff00']);
  const two = fitRuns(box, [{ text: `${'C'.repeat(40)}\nOK`, color: null }], 9);
  assert.equal(two.runs.map(run => run.text).join(''), `${'C'.repeat(32)}${ELLIPSIS}\nOK`, 'each line on its own');
  assert.equal(fitRuns({ ...box, style: { textScale: 1, wrapWidth: 60 } }, [{ text: 'C'.repeat(40), color: null }], 9).size, 4, 'wrapped text already fits its width');
});
