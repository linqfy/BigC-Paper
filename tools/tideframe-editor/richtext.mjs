// Colours for words inside a text (idea 28, schema 9), as in MiniMessage: "Saldo: <gold>{balance}</gold>" or
// "<#ffcc00>word</#ffcc00>". The twin of dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText, with exactly the same rules: only the 16 game
// colour names (plus grey and dark_grey) and #rrggbb are tags; anything else, such as "<3" or "a<b", stays text. A
// closing tag ends the latest tag spelled the same way (and any opened after it); a closing tag with nothing to close
// is text, and an unclosed tag runs to the end. "\<" draws a "<" a tag would otherwise take.

export const MAX_COLORS = 8;
export const COLOR_NAMES = Object.freeze({ black: '#000000', dark_blue: '#0000aa', dark_green: '#00aa00', dark_aqua: '#00aaaa', dark_red: '#aa0000',
  dark_purple: '#aa00aa', gold: '#ffaa00', gray: '#aaaaaa', grey: '#aaaaaa', dark_gray: '#555555', dark_grey: '#555555', blue: '#5555ff', green: '#55ff55',
  aqua: '#55ffff', red: '#ff5555', light_purple: '#ff55ff', yellow: '#ffff55', white: '#ffffff' });
const TAG = /^<(\/?)(#[0-9a-fA-F]{6}|[a-z_]{3,12})>/;
const colorOf = name => name.startsWith('#') ? name.toLowerCase() : Object.hasOwn(COLOR_NAMES, name) ? COLOR_NAMES[name] : null;
/** The colour tag starting at {@code index}: { closing, spelling, color, length }, or null. */
function tagAt(text, index) {
  const match = TAG.exec(text.slice(index, index + 16));
  if (!match) return null;
  const spelling = match[2].toLowerCase(), color = colorOf(spelling);
  return color ? { closing: match[1] === '/', spelling, color, length: match[0].length } : null;
}

/** The runs of {@code text} ([{ text, color }], color null for the element's own): tags out, "\<" resolved, neighbours of one colour merged. */
export function runs(text) {
  const out = [], open = [];
  let current = '';
  const add = () => {
    if (!current) return;
    const color = open.length ? open.at(-1).color : null, last = out.at(-1);
    if (last && last.color === color) last.text += current; else out.push({ text: current, color });
    current = '';
  };
  for (let i = 0; i < text.length;) {
    if (text[i] === '\\' && text[i + 1] === '<') { current += '<'; i += 2; continue; }
    const tag = text[i] === '<' ? tagAt(text, i) : null;
    if (tag) {
      const at = tag.closing ? open.map(entry => entry.spelling).lastIndexOf(tag.spelling) : -1;
      if (!tag.closing || at >= 0) {
        add();
        if (tag.closing) open.length = at; else open.push(tag);
        i += tag.length; continue;
      }
    }
    current += text[i]; i++;
  }
  add();
  return out;
}
/** The text as drawn, without tags. */
export function plain(text) { return String(text).includes('<') ? runs(String(text)).map(run => run.text).join('') : String(text); }
/** True when a tag or a "\<" changes how {@code text} is drawn (schema 9 rules). */
export function hasTags(text) { return typeof text === 'string' && text.includes('<') && plain(text) !== text; }
/** The distinct colours {@code text} uses, in order of appearance. */
export function colors(text) { return typeof text === 'string' && text.includes('<') ? [...new Set(runs(text).map(run => run.color).filter(Boolean))] : []; }
/** {@code text} with every "<" a tag or an escape would take written "\<": under the new rules it looks as before (schema 1-8 texts). */
export function escape(text) {
  if (!text.includes('<')) return text;
  let out = '';
  for (let i = 0; i < text.length; i++) out += (text[i] === '<' && (text[i - 1] === '\\' || tagAt(text, i)) ? '\\' : '') + text[i];
  return out;
}
