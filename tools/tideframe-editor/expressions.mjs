// Calculated text and variable arithmetic for "Probar", following the plugin's Expressions.java and Variables.java to
// the digit: decimals are exact (BigInt, like Java's BigDecimal), products and quotients keep 16 significant digits
// (MathContext.DECIMAL64, half-even) and results show at most 6 decimals (half-up). Pure: no DOM.

// ----- decimals: { u: BigInt unscaled, s: scale } meaning u × 10^-s -----
const NUMBER = /^[-+]?([0-9]+(\.[0-9]*)?|\.[0-9]+)([eE][-+]?[0-9]{1,3})?$/;
const TEN = 10n;
const pow10 = n => TEN ** BigInt(n);
const digits = u => (u < 0n ? -u : u).toString().length;
function strip(d) {
  let { u, s } = d;
  if (u === 0n) return { u: 0n, s: 0 };
  while (u % TEN === 0n) { u /= TEN; s--; }
  return { u, s };
}
/** The plugin's Variables.number: plain decimals with an optional exponent, else null. */
export function decimal(text) {
  if (text === null || text === undefined) return null;
  const t = String(text).trim();
  if (!NUMBER.test(t)) return null;
  let [mantissa, exponent = '0'] = t.toLowerCase().split('e');
  let sign = 1n;
  if (mantissa.startsWith('-')) { sign = -1n; mantissa = mantissa.slice(1); } else if (mantissa.startsWith('+')) mantissa = mantissa.slice(1);
  const [whole, fraction = ''] = mantissa.split('.');
  const u = BigInt((whole || '0') + fraction) * sign;
  return strip({ u, s: fraction.length - Number(exponent) });
}
function align(a, b) {
  const s = Math.max(a.s, b.s);
  return [a.u * pow10(s - a.s), b.u * pow10(s - b.s), s];
}
const add = (a, b) => { const [x, y, s] = align(a, b); return strip({ u: x + y, s }); };
const subtract = (a, b) => { const [x, y, s] = align(a, b); return strip({ u: x - y, s }); };
/** Rounds to 16 significant digits, half-even (MathContext.DECIMAL64); {@code sticky}: a nonzero remainder was dropped earlier. */
function round16(u, s, sticky = false) {
  const n = digits(u);
  if (n <= 16) return strip({ u, s });
  const drop = n - 16, unit = pow10(drop), negative = u < 0n, abs = negative ? -u : u;
  let keep = abs / unit;
  const dropped = abs % unit, half = 5n * pow10(drop - 1);
  if (dropped > half || dropped === half && (sticky || keep % 2n === 1n)) keep += 1n;
  return strip({ u: negative ? -keep : keep, s: s - drop });
}
const multiply = (a, b) => round16(a.u * b.u, a.s + b.s);
function divide(a, b) {
  if (b.u === 0n) return null;
  if (a.u === 0n) return { u: 0n, s: 0 };
  const k = Math.max(0, 20 + digits(b.u) - digits(a.u));
  const n = a.u * pow10(k), q = n / b.u, r = n % b.u;
  return round16(q, a.s - b.s + k, r !== 0n);
}
function remainder(a, b) {
  if (b.u === 0n) return null;
  const [x, y, s] = align(a, b);
  return strip({ u: x % y, s });
}
const compare = (a, b) => { const [x, y] = align(a, b); return x < y ? -1 : x > y ? 1 : 0; };
/** Rounds to {@code places} decimals with {@code mode}: half-up, floor or ceiling. */
function setScale(d, places, mode = 'half-up') {
  if (d.s <= places) return d;
  const unit = pow10(d.s - places), negative = d.u < 0n, abs = negative ? -d.u : d.u;
  let keep = abs / unit;
  const rest = abs % unit;
  if (mode === 'half-up' && rest * 2n >= unit) keep += 1n;
  if (mode === 'floor' && negative && rest !== 0n) keep += 1n;
  if (mode === 'ceiling' && !negative && rest !== 0n) keep += 1n;
  return strip({ u: negative ? -keep : keep, s: places });
}
/** The plugin's Variables.format: at most 6 decimals, no trailing zeros, null when longer than 256 characters. */
export function formatDecimal(d) {
  const { u, s } = setScale(d, 6);
  let text;
  if (s <= 0) text = (u * pow10(-s)).toString();
  else {
    const negative = u < 0n, raw = (negative ? -u : u).toString().padStart(s + 1, '0');
    text = `${negative ? '-' : ''}${raw.slice(0, raw.length - s)}.${raw.slice(raw.length - s)}`;
  }
  return text.length > 256 ? null : text === '-0' ? '0' : text;
}

/** {@code text} rounded half up to {@code places} decimals, as BigDecimal.valueOf(x).setScale(places, HALF_UP) writes it; the text itself when it is not a number. */
export function roundText(text, places) {
  const d = decimal(String(text));
  return d ? formatDecimal(setScale(d, places)) : String(text);
}
/** Variables.apply's arithmetic: add, subtract, multiply, divide; null when it cannot (a word, too long), {@code before} on division by zero. */
export function arithmetic(operation, before, value) {
  const left = decimal(before ?? '0'), right = decimal(value);
  if (!left || !right) return null;
  const result = { add, subtract, multiply, divide }[operation]?.(left, right);
  if (result === undefined) throw Error(`Unknown variable operation ${operation}`);
  return result === null ? before : formatDecimal(result);
}

// ----- expressions -----
const BRACES = /\{([^{}]{1,256})\}/g;
const PLAIN = /^[a-z_][a-z0-9_.]{0,47}$/;
const COLOUR = /[&§][0-9a-fk-orx]|<[^<>]{1,32}>/gi;
const FUNCTIONS = ['round', 'floor', 'ceil', 'abs', 'min', 'max'];
const MAX_TOKENS = 128, MAX_DEPTH = 32;
const plain = value => String(value).replace(COLOUR, '').trim();
const number = value => value === null || value === undefined ? null : decimal(plain(value));
export const truthy = value => !['', 'false', 'no', '0'].includes(plain(value).toLowerCase());
const bool = value => value ? 'true' : 'false';
const format = d => d === null ? null : formatDecimal(d);

const OPERATORS = ['==', '!=', '>=', '<=', '&&', '||', '>', '<', '+', '-', '*', '/', '%', '!', '?', ':', '(', ')', ','];
function tokens(source) {
  const out = [];
  let i = 0;
  while (i < source.length) {
    const c = source[i];
    if (c === ' ') { i++; continue; }
    if (out.length >= MAX_TOKENS) return null;
    if (c === '"' || c === "'") {
      const end = source.indexOf(c, i + 1);
      if (end < 0) return null;
      out.push({ kind: 's', text: source.slice(i + 1, end) }); i = end + 1; continue;
    }
    const rest = source.slice(i);
    let m;
    if (c === '%' && (m = /^%[A-Za-z0-9_:.,=-]{1,128}%/.exec(rest))) { out.push({ kind: 'p', text: m[0] }); i += m[0].length; continue; }
    if ((m = /^([0-9]+(\.[0-9]+)?|\.[0-9]+)/.exec(rest))) { out.push({ kind: 'n', text: m[0] }); i += m[0].length; continue; }
    if ((m = /^[$]?[a-z_][a-z0-9_.]{0,47}/.exec(rest))) {
      if (m[0].endsWith('.')) return null;
      out.push({ kind: 'i', text: m[0] }); i += m[0].length; continue;
    }
    const op = OPERATORS.find(candidate => source.startsWith(candidate, i));
    if (!op) return null;
    out.push({ kind: 'o', text: op }); i += op.length;
  }
  return out.length ? out : null;
}
class NotAnExpression extends Error { }
function parse(source) {
  const list = tokens(source);
  if (!list) return null;
  let at = 0;
  const accept = op => { if (at < list.length && list[at].kind === 'o' && list[at].text === op) { at++; return true; } return false; };
  const expect = op => { if (!accept(op)) throw new NotAnExpression(); };
  const deep = depth => { if (depth > MAX_DEPTH) throw new NotAnExpression(); };
  function ternary(depth) {
    deep(depth);
    const test = or(depth);
    if (!accept('?')) return test;
    const then = ternary(depth + 1);
    expect(':');
    return { type: 'ternary', test, then, otherwise: ternary(depth + 1) };
  }
  function or(depth) { let node = and(depth); while (accept('||')) node = { type: 'binary', op: '||', left: node, right: and(depth) }; return node; }
  function and(depth) { let node = comparison(depth); while (accept('&&')) node = { type: 'binary', op: '&&', left: node, right: comparison(depth) }; return node; }
  function comparison(depth) {
    const node = sum(depth);
    for (const op of ['==', '!=', '>=', '<=', '>', '<']) if (accept(op)) return { type: 'binary', op, left: node, right: sum(depth) };
    return node;
  }
  function sum(depth) {
    let node = product(depth);
    for (;;) {
      if (accept('+')) node = { type: 'binary', op: '+', left: node, right: product(depth) };
      else if (accept('-')) node = { type: 'binary', op: '-', left: node, right: product(depth) };
      else return node;
    }
  }
  function product(depth) {
    let node = unary(depth);
    for (;;) {
      const op = ['*', '/', '%'].find(candidate => accept(candidate));
      if (!op) return node;
      node = { type: 'binary', op, left: node, right: unary(depth) };
    }
  }
  function unary(depth) {
    deep(depth);
    if (accept('-')) return { type: 'negate', inner: unary(depth + 1) };
    if (accept('!')) return { type: 'not', inner: unary(depth + 1) };
    return primary(depth);
  }
  function primary(depth) {
    if (at >= list.length) throw new NotAnExpression();
    const token = list[at++];
    if (token.kind === 'n' || token.kind === 's') return { type: 'literal', value: token.text };
    if (token.kind === 'p') return { type: 'name', name: token.text };
    if (token.kind === 'i') {
      if (FUNCTIONS.includes(token.text) && accept('(')) {
        const args = [];
        do { args.push(ternary(depth + 1)); } while (accept(','));
        expect(')');
        const fits = token.text === 'round' ? args.length <= 2 : ['min', 'max'].includes(token.text) ? args.length <= 16 : args.length === 1;
        if (!fits) throw new NotAnExpression();
        return { type: 'call', name: token.text, args };
      }
      return { type: 'name', name: token.text };
    }
    if (token.text !== '(') throw new NotAnExpression();
    const inner = ternary(depth + 1);
    expect(')');
    return inner;
  }
  try {
    const node = ternary(0);
    return at === list.length ? node : null;
  } catch (error) { if (error instanceof NotAnExpression) return null; throw error; }
}
function evaluate(node, names) {
  switch (node.type) {
    case 'literal': return node.value;
    case 'name': { const value = names(node.name); return value === undefined ? null : value; }
    case 'not': { const v = evaluate(node.inner, names); return v === null ? null : bool(!truthy(v)); }
    case 'negate': { const n = number(evaluate(node.inner, names)); return n === null ? null : format({ u: -n.u, s: n.s }); }
    case 'ternary': { const v = evaluate(node.test, names); return v === null ? null : evaluate(truthy(v) ? node.then : node.otherwise, names); }
    case 'call': {
      const values = [];
      for (const arg of node.args) { const n = number(evaluate(arg, names)); if (n === null) return null; values.push(n); }
      const [x] = values;
      switch (node.name) {
        case 'round': {
          if (values.length === 1) return format(setScale(x, 0));
          const places = values[1];
          if (places.s > 0 || compare(places, decimal('0')) < 0 || compare(places, decimal('6')) > 0) return null;
          return format(setScale(x, Number(places.u * pow10(-places.s))));
        }
        case 'floor': return format(setScale(x, 0, 'floor'));
        case 'ceil': return format(setScale(x, 0, 'ceiling'));
        case 'abs': return format({ u: x.u < 0n ? -x.u : x.u, s: x.s });
        case 'min': return format(values.reduce((a, b) => compare(b, a) < 0 ? b : a));
        default: return format(values.reduce((a, b) => compare(b, a) > 0 ? b : a));
      }
    }
    default: {
      const { op } = node;
      if (op === '&&' || op === '||') {
        const a = evaluate(node.left, names);
        if (a === null) return null;
        if (op === '&&' ? !truthy(a) : truthy(a)) return bool(op === '||');
        const b = evaluate(node.right, names);
        return b === null ? null : bool(truthy(b));
      }
      const a = evaluate(node.left, names), b = evaluate(node.right, names);
      if (a === null || b === null) return null;
      const x = number(a), y = number(b);
      if (op === '==' || op === '!=') { const same = x && y ? compare(x, y) === 0 : plain(a) === plain(b); return bool((op === '==') === same); }
      if (['>=', '<=', '>', '<'].includes(op)) {
        if (!x || !y) return bool(false);
        const c = compare(x, y);
        return bool({ '>=': c >= 0, '<=': c <= 0, '>': c > 0, '<': c < 0 }[op]);
      }
      if (op === '+') return x && y ? format(add(x, y)) : a + b;
      if (!x || !y) return null;
      return format({ '-': subtract, '*': multiply, '/': divide, '%': remainder }[op](x, y));
    }
  }
}

/** True when {@code text} has a brace pair that is an expression (not a plain name), like LiveText.isLive's check. */
export function hasExpression(text) {
  for (const m of String(text ?? '').matchAll(BRACES)) if (!PLAIN.test(m[1]) && parse(m[1])) return true;
  return false;
}
/** One expression's value (no braces), or null when it does not parse or cannot be computed. */
export function evaluateExpression(source, names) {
  const node = parse(source);
  return node ? evaluate(node, names) : null;
}
/**
 * Expressions.render: computable tokens become their values; the text around them, and every token that stays as
 * written, goes through {@code chunk}. {@code names(name)} gives a value or null.
 */
export function renderExpressions(text, chunk, names) {
  text = String(text ?? '');
  if (!text.includes('{')) return chunk(text);
  let out = '', last = 0, replaced = false;
  for (const m of text.matchAll(BRACES)) {
    if (PLAIN.test(m[1])) continue;
    const node = parse(m[1]);
    if (!node) continue;
    const value = evaluate(node, names);
    if (value === null) continue;
    out += chunk(text.slice(last, m.index)) + value;
    last = m.index + m[0].length; replaced = true;
  }
  return replaced ? out + chunk(text.slice(last)) : chunk(text);
}
