import test from 'node:test';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import os from 'node:os';
import path from 'node:path';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { openJar, decodePng, inkWidth, parseIconTable } from './client-jar.mjs';
import { createServer } from './serve.mjs';
import { createScreen, upgradeLogic, validateScreen, iconSize, iconFits, iconFollowsStyle, serializeScreen } from './model.mjs';

/** A PNG: {@code rows} are raw scanlines (no filter byte); each gets {@code filter} (0-4) as an encoder would. */
function png(width, height, color, depth, rows, { filter = 0, palette = null, transparency = null } = {}) {
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[color], stride = Math.ceil(width * channels * depth / 8), step = Math.max(1, channels * depth >> 3);
  const raw = Buffer.alloc((stride + 1) * height);
  rows.forEach((row, y) => {
    raw[y * (stride + 1)] = filter;
    for (let i = 0; i < stride; i++) {
      const a = i >= step ? row[i - step] : 0, b = y ? rows[y - 1][i] : 0, c = y && i >= step ? rows[y - 1][i - step] : 0;
      const predictor = filter === 1 ? a : filter === 2 ? b : filter === 3 ? (a + b) >> 1 : filter === 4 ? (() => { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); return pa <= pb && pa <= pc ? a : pb <= pc ? b : c; })() : 0;
      raw[y * (stride + 1) + 1 + i] = (row[i] - predictor) & 255;
    }
  });
  const chunk = (type, body) => { const head = Buffer.alloc(8); head.writeUInt32BE(body.length); head.write(type, 4, 'latin1'); const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(Buffer.concat([Buffer.from(type, 'latin1'), body]))); return Buffer.concat([head, body, crc]); };
  const header = Buffer.alloc(13); header.writeUInt32BE(width); header.writeUInt32BE(height, 4); header[8] = depth; header[9] = color;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), ...(palette ? [chunk('PLTE', palette)] : []),
    ...(transparency ? [chunk('tRNS', transparency)] : []), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}
/** An RGBA image painted where {@code paint(x, y)} is true. */
const rgba = (width, height, paint) => Array.from({ length: height }, (_, y) => Buffer.from(Array.from({ length: width }, (_, x) => paint(x, y) ? [200, 40, 40, 255] : [0, 0, 0, 0]).flat()));
/** A zip with one stored and one deflated entry, as a jar would have. */
function zip(files) {
  const locals = [], centrals = []; let offset = 0;
  for (const [name, data, deflate] of files) {
    const packed = deflate ? zlib.deflateRawSync(data) : data, nameBytes = Buffer.from(name, 'utf8');
    const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50); local.writeUInt16LE(deflate ? 8 : 0, 8); local.writeUInt32LE(zlib.crc32(data), 14); local.writeUInt32LE(packed.length, 18); local.writeUInt32LE(data.length, 22); local.writeUInt16LE(nameBytes.length, 26);
    const central = Buffer.alloc(46); central.writeUInt32LE(0x02014b50); central.writeUInt16LE(deflate ? 8 : 0, 10); central.writeUInt32LE(zlib.crc32(data), 16); central.writeUInt32LE(packed.length, 20); central.writeUInt32LE(data.length, 24); central.writeUInt16LE(nameBytes.length, 28); central.writeUInt32LE(offset, 42);
    locals.push(local, nameBytes, packed); centrals.push(central, nameBytes); offset += 30 + nameBytes.length + packed.length;
  }
  const directory = Buffer.concat(centrals), end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50); end.writeUInt16LE(files.length, 8); end.writeUInt16LE(files.length, 10); end.writeUInt32LE(directory.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, directory, end]);
}

test('the ink width is what the client measures: the last column with any visible pixel, in the first frame only', () => {
  // A 16x16 sword shape ending at column 12, written with each PNG filter.
  for (const filter of [0, 1, 2, 3, 4]) {
    const image = decodePng(png(16, 16, 6, 8, rgba(16, 16, (x, y) => x <= 12 && Math.abs(x - y) < 3), { filter }));
    assert.equal(inkWidth(image, 16, 16), 13, `filter ${filter}`);
  }
  // A 16x32 animation strip: the second frame reaches column 15, but only the first cell counts.
  const strip = decodePng(png(16, 32, 6, 8, rgba(16, 32, (x, y) => y < 16 ? x === 9 : x === 15)));
  assert.equal(inkWidth(strip, 16, 16), 10);
  // Palette with transparency, 4 bits per pixel: index 0 is invisible, index 1 is drawn up to column 6.
  const indexed = Array.from({ length: 16 }, () => Buffer.from([0x11, 0x11, 0x11, 0x10, 0, 0, 0, 0]));
  assert.equal(inkWidth(decodePng(png(16, 16, 3, 4, indexed, { palette: Buffer.from([0, 0, 0, 255, 255, 255]), transparency: Buffer.from([0]) })), 16, 16), 7);
  // Grey with alpha: a faint pixel still counts (the client tests alpha != 0).
  const grey = Array.from({ length: 16 }, (_, y) => Buffer.from(Array.from({ length: 16 }, (_, x) => x === 11 && y === 3 ? [90, 1] : [90, 0]).flat()));
  assert.equal(inkWidth(decodePng(png(16, 16, 4, 8, grey)), 16, 16), 12);
  // No alpha at all: every pixel is visible, so the whole cell counts.
  assert.equal(inkWidth(decodePng(png(16, 16, 2, 8, Array.from({ length: 16 }, () => Buffer.alloc(48)))), 16, 16), 16);
});

test('the jar reader finds stored and deflated entries and refuses damaged ones', () => {
  const texture = png(16, 16, 6, 8, rgba(16, 16, (x, y) => x < 8));
  const jar = openJar(zip([['pack.mcmeta', Buffer.from('{}'), false], ['assets/minecraft/textures/item/test_gem.png', texture, true]]));
  assert.equal(jar.has('pack.mcmeta'), true);
  assert.deepEqual(jar.read('assets/minecraft/textures/item/test_gem.png'), texture);
  assert.equal(jar.read('assets/minecraft/textures/item/nothing.png'), null);
  const broken = zip([['a.txt', Buffer.from('hello hello hello'), true]]); broken[30 + 5 + 2] ^= 0xff; // inside the deflated bytes
  assert.throws(() => openJar(broken).read('a.txt'));
  assert.throws(() => openJar(Buffer.from('not a zip at all, just bytes')));
  assert.deepEqual([...parseIconTable('# comment\nstone\tblock/stone\t16\t16\t1\t16\tb\nmagma_block\tblock/magma\t16\t48\t3\t16\t\n')],
    [['stone', { texture: 'block/stone', width: 16, height: 16, rows: 1, ink: 16, block: true, tinted: false }],
     ['magma_block', { texture: 'block/magma', width: 16, height: 48, rows: 3, ink: 16, block: false, tinted: false }]]);
});

test('the editor serves the icon table as a module and each texture from the client jar, for the preview only', async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), 'tideframe-icons-'));
  const texture = png(16, 16, 6, 8, rgba(16, 16, x => x < 10)), jarPath = path.join(dir, 'client.jar'), tablePath = path.join(dir, 'item-icons.tsv');
  await writeFile(jarPath, zip([['assets/minecraft/textures/item/test_gem.png', texture, true]]));
  await writeFile(tablePath, 'test_gem\titem/test_gem\t16\t16\t1\t10\t\nghost\titem/ghost\t16\t16\t1\t9\tt\n');
  const server = createServer({ clientJar: jarPath, iconTable: tablePath });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    const module = await fetch(`${base}/item-icons.mjs`);
    assert.equal(module.status, 200); assert.match(module.headers.get('content-type'), /javascript/);
    assert.match(module.headers.get('content-security-policy'), /img-src data: 'self'/);
    const text = await module.text();
    assert.match(text, /^export const ITEM_ICONS = Object\.freeze\(/);
    assert.deepEqual(JSON.parse(text.slice(text.indexOf('(') + 1, text.lastIndexOf(')'))), {
      test_gem: { texture: 'item/test_gem', rows: 1, block: false, tinted: false }, ghost: { texture: 'item/ghost', rows: 1, block: false, tinted: true } });
    const image = await fetch(`${base}/item-texture/test_gem`);
    assert.equal(image.status, 200); assert.equal(image.headers.get('content-type'), 'image/png');
    assert.deepEqual(Buffer.from(await image.arrayBuffer()), texture);
    for (const missing of ['/item-texture/ghost', '/item-texture/unknown', '/item-texture/../client.jar', '/item-texture/Test_Gem'])
      assert.equal((await fetch(`${base}${missing}`)).status, 404, missing);
  } finally { await new Promise(resolve => server.close(resolve)); }
  const bare = createServer({ clientJar: path.join(dir, 'none.jar'), iconTable: path.join(dir, 'none.tsv') });
  await new Promise(resolve => bare.listen(0, '127.0.0.1', resolve));
  try {
    const at = `http://127.0.0.1:${bare.address().port}`;
    assert.equal((await fetch(`${at}/item-icons.mjs`)).status, 404, 'no table: no list, and the page carries on');
    assert.equal((await fetch(`${at}/item-texture/test_gem`)).status, 404);
  } finally { await new Promise(resolve => bare.close(resolve)); }
});

test('item icons follow the plugin rules: schema 9, whole sixteens, only the lower part of the menu, no lists or animations', () => {
  const icon = (y, extra = {}) => { const s = upgradeLogic(createScreen()); s.elements.push({ ...s.elements[0], id: 'gem', text: '', kind: 'decoration', action: '', x: 0, y, width: 16 / 128, height: 16 / 72, item: 'diamond', ...extra }); return s; };
  assert.equal(iconSize({ width: 16 / 128, height: 16 / 72 }), 16);
  assert.equal(iconSize({ width: 40 / 128, height: 40 / 72 }), 32);
  assert.equal(iconSize({ width: 2, height: 2 }), 64);
  assert.equal(iconFits({ y: 1 - 56 / 72, width: 16 / 128, height: 16 / 72 }), true, 'bottom edge exactly at y 64');
  assert.equal(iconFits({ y: 1 - 55 / 72, width: 16 / 128, height: 16 / 72 }), false);
  const ok = validateScreen(icon(-.4));
  assert.match(serializeScreen(ok), /"item": "diamond"/);
  assert.throws(() => validateScreen(icon(.8)), /lower part of the menu/);
  assert.throws(() => validateScreen(icon(-.4, { item: 'Diamond!' })), /game item id/);
  assert.throws(() => validateScreen(icon(-.4, { logic: { frames: { folder: 'spin' } } })), /cannot go on a list/);
  // The game draws the texture as it is: looks the icon could not follow are refused; opacity 0 hides both.
  const look = style => icon(-.4, { style: { ...ok.elements.at(-1).style, ...style } });
  for (const style of [{ opacity: .5 }, { rotation: 10 }, { brightness: 1.2 }, { shadow: true }, { blur: 2 }, { hoverEffect: 'translate' }, { hoverEffect: 'scale' }])
    assert.throws(() => validateScreen(look(style)), /cannot follow the element's look/, JSON.stringify(style));
  assert.doesNotThrow(() => validateScreen(look({ opacity: 0 })));
  assert.doesNotThrow(() => validateScreen(look({ hoverEffect: 'glow', hoverColor: '#ff0000' })));
  assert.equal(iconFollowsStyle({ opacity: 1, rotation: 0, brightness: 1, shadow: false, hoverEffect: 'none' }), true);
  const old = createScreen(); old.elements.push({ ...old.elements[0], id: 'gem', item: 'diamond' });
  assert.throws(() => validateScreen(old), 'only schema 9 has item');
});
