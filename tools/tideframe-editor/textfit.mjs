// Text that shrinks to fit its box (the plugin's TextFit.java): on schema 9 pages a line wider than its box is drawn at
// 3/4 and then 1/2 of its size; past half it is cut with an ellipsis. Sizes are quarters of the text size (4 = as
// authored). Only the width counts: wrapped text already breaks its lines. The plugin decides with these same numbers;
// the shared cases are in src/test/resources/textfit.json.

import { plain } from './richtext.mjs';
import { hasExpression } from './expressions.mjs';

export const FULL = 4, THREE_QUARTERS = 3, HALF = 2;
export const SIZES = Object.freeze([FULL, THREE_QUARTERS, HALF]);
export const ELLIPSIS = '…';
/** What {@link longest} counts for a token it cannot bound. */
export const UNBOUNDED = 256;

/** True when a page of this schema version shrinks its text (schema 9; older pages keep cutting). */
export const allowed = version => version >= 9;
/** Box width in logical pixels, as the plugin measures it. */
export const boxWidth = element => Math.max(1, Math.round(element.width * 128));
/** Characters of one line that fit in {@code width} logical pixels at text scale {@code textScale}, drawn at {@code size}. */
export const maxChars = (width, textScale, size) => Math.max(0, Math.floor((4 * width + textScale * size) / (6 * textScale * size)));
export const elementMaxChars = (element, size) => maxChars(boxWidth(element), element.style?.textScale ?? 1, size);
/** The largest of {@code sizes} at which a line of {@code length} characters fits; the smallest when none does. */
export function sizeFor(element, length, sizes = SIZES) {
  let smallest = FULL;
  for (const size of SIZES) {
    if (!sizes.includes(size)) continue;
    if (length <= elementMaxChars(element, size)) return size;
    smallest = size;
  }
  return smallest;
}
/** {@code line} cut to {@code max} characters, the last one an ellipsis, when it is longer. */
export const cut = (line, max) => line.length <= max ? line : max <= 0 ? '' : line.slice(0, max - 1) + ELLIPSIS;
/** The lines of a text that may shrink (none when it wraps: wrapping already fits its width). */
export const lines = (element, drawn) => (element.style?.wrapWidth ?? 0) > 0 ? [] : String(drawn).split('\n');
export const widest = list => Math.max(0, ...list.map(line => line.length));

/** Longest each built-in {token} can be (LivePlaceholders.builtIn, TextFit.TOKEN_LENGTHS); any other token is unbounded. */
const TOKEN_LENGTHS = Object.freeze({ player: 16, health: 4, max_health: 4, food: 2, level: 10, xp: 4, ping: 10, online: 10, max_players: 10, x: 9, y: 5, z: 9, time: 5, balance_short: 8 });
const TOKEN = /\$?\{[a-z_][a-z0-9_.]{0,47}\}|%[A-Za-z0-9_:.-]{1,64}(?:,(?:MIN|MAX|DEFAULT)=[^,%]{1,32}){0,3}%/g;
/** Most characters a live text can show: its fixed text plus the longest each token can be. Tags are not counted. */
export function longest(template) {
  const text = plain(template);
  if (hasExpression(text)) return UNBOUNDED;
  let total = 0, end = 0;
  for (const match of text.matchAll(TOKEN)) {
    total += match.index - end;
    const token = match[0], bound = token.startsWith('{') ? TOKEN_LENGTHS[token.slice(1, -1)] : undefined;
    total += bound ?? UNBOUNDED;
    end = match.index + token.length;
  }
  return Math.min(UNBOUNDED, total + text.length - end);
}
/** The smaller sizes a live text may need in its box, judged by {@link longest}. */
export function liveSizes(element) {
  const most = longest(element.text);
  if (most <= elementMaxChars(element, FULL)) return [];
  return most <= elementMaxChars(element, THREE_QUARTERS) ? [THREE_QUARTERS] : [THREE_QUARTERS, HALF];
}
/** LiveText.lineTop: the single line's top row at full size, in logical pixels. */
const lineTop = element => Math.round((1 - element.y) * 72) - Math.floor(7 * (element.style?.textScale ?? 1) / 2) - 1;
/** Top of a single line's glyphs at {@code size}, in quarter logical pixels: the line keeps its centre. */
export function lineTopQuarters(element, size) {
  const scale = element.style?.textScale ?? 1;
  return 4 * lineTop(element) + 14 * scale - Math.floor(7 * scale * size / 2);
}
/** Left of a single line of {@code length} characters at {@code size}, in quarter logical pixels, by its alignment. */
export function lineLeftQuarters(element, length, size) {
  const width = boxWidth(element), centerX = Math.round((element.x + 1) * 128), scale = element.style?.textScale ?? 1;
  const left = centerX - Math.floor(width / 2), align = element.style?.textAlign ?? 'center';
  if (size === FULL) {
    const textWidth = Math.max(0, (length * 6 - 1) * scale);
    return 4 * (align === 'left' ? left : align === 'right' ? left + width - textWidth : centerX - Math.floor(textWidth / 2));
  }
  const textWidth = Math.max(0, (length * 6 - 1) * scale * size);
  return align === 'left' ? 4 * left : align === 'right' ? 4 * (left + width) - textWidth : 4 * centerX - Math.floor(textWidth / 2);
}

/**
 * The runs of a text ({ text, color }, lines split by '\n') as the game draws them in the element's box: the size and
 * the runs, each line cut with an ellipsis past half size (the ellipsis keeps the colour of the character it replaces).
 * Schema 1-8 pages and wrapped text come back as they are, at full size.
 */
export function fitRuns(element, runs, version) {
  const text = runs.map(run => run.text).join(''), fitting = allowed(version) && element.kind !== 'decoration' ? lines(element, text) : [];
  const longestLine = widest(fitting);
  if (!fitting.length || longestLine <= elementMaxChars(element, FULL)) return { size: FULL, runs };
  const size = sizeFor(element, longestLine), max = elementMaxChars(element, size);
  if (longestLine <= max) return { size, runs };
  // One colour per character, then each line cut and the characters regrouped into runs.
  const colors = runs.flatMap(run => Array.from(run.text, () => run.color ?? null));
  const out = [];
  const push = (character, color) => { const last = out.at(-1); if (last && last.color === color) last.text += character; else out.push({ text: character, color }); };
  let start = 0;
  for (const [index, line] of text.split('\n').entries()) {
    if (index > 0) push('\n', colors[start - 1] ?? null);
    const shown = cut(line, max);
    for (let k = 0; k < shown.length; k++) push(shown[k], colors[start + Math.min(k, line.length - 1)] ?? null);
    start += line.length + 1;
  }
  return { size, runs: out };
}
