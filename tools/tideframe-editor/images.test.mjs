import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateSync } from 'node:zlib';
import { promoteScreen, parseScreen, serializeScreen, validateScreen } from './model.mjs';
import { encodeClipboard, pasteClipboard, moveSelection, resizeSelection } from './interaction.mjs';
import * as model from './model.mjs';
import * as interaction from './interaction.mjs';

// Mathematical RGBA fixtures authored for this suite; no third-party images.
function crc(bytes) {
  let result = 0xffffffff;
  for (const byte of bytes) { result ^= byte; for (let i = 0; i < 8; i++) result = (result >>> 1) ^ ((result & 1) ? 0xedb88320 : 0); }
  return (result ^ 0xffffffff) >>> 0;
}
function chunk(type, bytes = Buffer.alloc(0)) {
  const name = Buffer.from(type), size = Buffer.alloc(4), checksum = Buffer.alloc(4);
  size.writeUInt32BE(bytes.length); checksum.writeUInt32BE(crc(Buffer.concat([name, bytes])));
  return Buffer.concat([size, name, bytes, checksum]);
}
const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
function png(width = 1, height = 1, extra = [], compressed = deflateSync(Buffer.from([0, 12, 34, 56, 78]))) {
  const header = Buffer.alloc(13); header.writeUInt32BE(width); header.writeUInt32BE(height, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([signature, chunk('IHDR', header), ...extra, chunk('IDAT', compressed), chunk('IEND')]);
}
const fixture = png().toString('base64');
const images = await import('./images.mjs');

test('canonical static RGBA PNG bytes survive strict base64 validation unchanged', () => {
  assert.equal(typeof images.validatePngBase64, 'function', 'portable PNG validator must be available');
  const result = images.validatePngBase64(fixture);
  assert.equal(result.width, 1); assert.equal(result.height, 1);
  assert.deepEqual(Buffer.from(result.bytes), png());
  assert.equal(images.bytesToBase64(result.bytes), fixture);
});

test('PNG validation rejects malformed signature, CRC, chunk lengths and trailing bytes', () => {
  assert.equal(typeof images.validatePngBase64, 'function');
  const badSignature = png(); badSignature[0] = 0;
  const badCrc = png(); badCrc[29] ^= 1;
  const badLength = png(); badLength.writeUInt32BE(0xffffffff, 8);
  for (const bytes of [badSignature, badCrc, badLength, png().subarray(0, -12), Buffer.concat([png(), Buffer.from([0])])])
    assert.throws(() => images.validatePngBase64(bytes.toString('base64')), /PNG|CRC|chunk|IEND/i);
});

test('PNG validation refuses APNG, excess dimensions and excess decoded bytes', () => {
  assert.equal(typeof images.validatePngBase64, 'function');
  for (const bytes of [png(0), png(3841), png(1, 3841), png(1, 1, [chunk('acTL', Buffer.alloc(8))]), png(1, 1, [chunk('fcTL', Buffer.alloc(26))])])
    assert.throws(() => images.validatePngBase64(bytes.toString('base64')), /PNG|3840|static/i);
  assert.throws(() => images.validatePngBase64(Buffer.alloc(41943041).toString('base64')), /40 MiB/i);
});

test('1024-pixel landscape PNG and files over the former 256 KiB limit are accepted unchanged', async () => {
  assert.equal(images.MAX_PNG_SIDE, 3840); assert.equal(images.MAX_PNG_BYTES, 41943040);
  const width = 1024, height = 576, rows = Buffer.alloc(height * (1 + width * 4));
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const at = y * (1 + width * 4) + 1 + x * 4;
    rows[at] = x; rows[at + 1] = y; rows[at + 2] = 160; rows[at + 3] = (x + y) % 256;
  }
  const bytes = png(width, height, [chunk('tEXt', Buffer.alloc(300000, 65))], deflateSync(rows));
  assert.ok(bytes.length > 262144 && bytes.length <= 6291456);
  let decoded = false;
  const loaded = await images.loadPngFile({ size: bytes.length, arrayBuffer: async () => bytes }, async (value, metadata) => {
    decoded = true; assert.equal(value, bytes.toString('base64')); assert.equal(metadata.width, width); assert.equal(metadata.height, height);
  });
  assert.equal(loaded, bytes.toString('base64')); assert.equal(decoded, true);
});

test('maximum 1024x1024 RGBA16 Adam7 image remains bounded and validates exact pass sizes', async () => {
  const header = Buffer.alloc(13); header.writeUInt32BE(1024); header.writeUInt32BE(1024, 4); header[8] = 16; header[9] = 6; header[12] = 1;
  const passes = [[0, 0, 8, 8], [4, 0, 8, 8], [0, 4, 4, 8], [2, 0, 4, 4], [0, 2, 2, 4], [1, 0, 2, 2], [0, 1, 1, 2]];
  const size = passes.reduce((sum, [x, y, dx, dy]) => sum + Math.ceil((1024 - y) / dy) * (1 + Math.ceil((1024 - x) / dx) * 8), 0);
  const bytes = Buffer.concat([signature, chunk('IHDR', header), chunk('IDAT', deflateSync(Buffer.alloc(size))), chunk('IEND')]);
  const result = await images.validatePngContent(bytes.toString('base64'), async () => {});
  assert.equal(result.width, 1024); assert.equal(result.height, 1024); assert.equal(result.interlace, 1);
});

test('only canonical base64 PNG is accepted, with no URLs, data URLs, whitespace or alternate formats', () => {
  assert.equal(typeof images.validatePngBase64, 'function');
  for (const raw of ['', fixture + '\n', fixture.replace(/=$/, ''), fixture.replace(/.$/, 'A'), 'https://example.com/a.png', 'data:image/png;base64,' + fixture,
    Buffer.from('<svg/>').toString('base64'), Buffer.from('GIF89a').toString('base64')])
    assert.throws(() => images.validatePngBase64(raw), /PNG|base64/i);
  // The final padded sextet may not contain discarded nonzero bits.
  assert.throws(() => images.validatePngBase64('iVBORw=='), /PNG|base64/i);
  assert.throws(() => images.validatePngBase64('iVBORx=='), /base64/i);
});

test('PNG and background survive image-only labels/buttons, group edits, clipboard and JSON', () => {
  const value = promoteScreen({ version: 1, id: 'menu', title: 'TIDEFRAME', elements: [
    { id: 'art', text: '', x: 0, y: 0, width: .4, height: .2, kind: 'button', action: '' }
  ] });
  value.elements[0] = { ...value.elements[0], image: fixture, background: true, action: 'conditional-message',
    messageCondition: { permission: 'example.vip', ifTrue: 'YES', ifFalse: 'NO' } };
  assert.equal(validateScreen(value).version, 4);
  const moved = moveSelection(value, ['art'], .1, -.2), resized = resizeSelection(moved, ['art'], .2, .3);
  const pasted = pasteClipboard(resized, encodeClipboard(resized, ['art'])).screen;
  const saved = parseScreen(serializeScreen(pasted));
  assert.equal(model.screenByteLength(saved), new TextEncoder().encode(serializeScreen(saved)).length);
  assert.equal(saved.elements[1].image, fixture); assert.equal(saved.elements[1].background, true);
  assert.equal(saved.elements[1].width, .6); assert.equal(saved.elements[1].height, .5);
  assert.deepEqual(saved.elements[1].messageCondition, { permission: 'example.vip', ifTrue: 'YES', ifFalse: 'NO' });
  const label = { ...saved.elements[1], kind: 'label', action: '', messageCondition: null };
  validateScreen({ ...saved, elements: [label] });
});

test('browser PNG decode must succeed before accepting file bytes and preserves original RGBA bytes', async () => {
  assert.equal(typeof images.loadPngFile, 'function');
  const file = { size: png().length, arrayBuffer: async () => png() };
  let observed;
  const result = await images.loadPngFile(file, async (base64, metadata) => { observed = [base64, metadata.width, metadata.height]; });
  assert.equal(result, fixture); assert.deepEqual(observed, [fixture, 1, 1]);
  await assert.rejects(images.loadPngFile(file, async () => { throw Error('PNG decode failed'); }), /decode/i);
  let read = false;
  await assert.rejects(images.loadPngFile({ size: 41943041, arrayBuffer: async () => { read = true; } }, async () => {}), /40 MiB/i);
  assert.equal(read, false);
});

test('combined images cannot bypass the JSON byte limit through clipboard paste', () => {
  const image = png(1, 1, [chunk('tEXt', Buffer.concat([Buffer.from('note\0'), Buffer.alloc(6000000, 65)]))]).toString('base64');
  const source = promoteScreen({ version: 1, id: 'menu', title: 'TIDEFRAME', elements: [] });
  source.elements = Array.from({ length: 16 }, (_, i) => ({ id: `art${i}`, text: '', x: 0, y: 0, width: .4, height: .2, kind: 'label', action: '',
    shape: 'rectangle', border: true, messageCondition: null, background: false, image, style: { opacity: 1, fillColor: '#28384b', borderColor: '#ffffff', textColor: '#ffffff', shadow: false }, layer: 0, actionArea: null }));
  serializeScreen(source);
  const raw = encodeClipboard(source, ['art0', 'art1']), before = JSON.stringify(source);
  assert.throws(() => pasteClipboard(source, raw), /128 MiB/i);
  assert.equal(JSON.stringify(source), before);
});

function pending() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

test('overlapping PNG loads install only the newest request even if the older load completes first', async () => {
  assert.equal(typeof interaction.createPublicationController, 'function', 'PNG publication must sequence asynchronous requests');
  const older = pending(), newer = pending(), applied = [], messages = [];
  const controller = interaction.createPublicationController();
  const handlers = { apply: image => applied.push(image), report: message => messages.push(message) };
  const first = controller.run(() => older.promise, handlers), second = controller.run(() => newer.promise, handlers);
  const beforeCompletion = messages.length;
  older.resolve('OLDER_IMAGE'); assert.equal(await first, false);
  assert.deepEqual(applied, []); assert.equal(messages.length, beforeCompletion);
  newer.resolve('NEWER_IMAGE'); assert.equal(await second, true);
  assert.deepEqual(applied, ['NEWER_IMAGE']); assert.match(messages.at(-1), /Ready/);
});

test('a stale PNG failure cannot overwrite the latest request success status', async () => {
  assert.equal(typeof interaction.createPublicationController, 'function');
  const older = pending(), newer = pending(), applied = [], messages = [];
  const controller = interaction.createPublicationController();
  const handlers = { apply: image => applied.push(image), report: (...args) => messages.push(args) };
  const first = controller.run(() => older.promise, handlers), second = controller.run(() => newer.promise, handlers);
  newer.resolve('NEWER_IMAGE'); await second;
  const beforeStale = structuredClone(messages);
  older.reject(Error('older decode failed')); assert.equal(await first, false);
  assert.deepEqual(applied, ['NEWER_IMAGE']); assert.deepEqual(messages, beforeStale);
});

test('selection away and back, removal or import invalidate pending PNG completion without changing status', async () => {
  assert.equal(typeof interaction.createPublicationController, 'function');
  for (const invalidations of [1, 2]) {
    const delayed = pending(), applied = [], messages = [];
    const controller = interaction.createPublicationController();
    const job = controller.run(() => delayed.promise, { apply: image => applied.push(image), report: message => messages.push(message) });
    for (let i = 0; i < invalidations; i++) controller.invalidate();
    messages.push('Current selection status');
    delayed.resolve('STALE_IMAGE'); assert.equal(await job, false);
    assert.deepEqual(applied, []); assert.equal(messages.at(-1), 'Current selection status');
  }
});

const incompletePng = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACUlEQVR4nGMAAAABAAFe/335AAAAAElFTkSuQmCC';
test('CRC-valid PNG with incomplete inflated scanlines is rejected before browser decoding', async () => {
  assert.equal(typeof images.validatePngContent, 'function');
  images.validatePngBase64(incompletePng);
  let decoded = false;
  await assert.rejects(images.validatePngContent(incompletePng, async () => { decoded = true; }), /scanline|inflated/i);
  assert.equal(decoded, false);
});

test('strict PNG content validation rejects overexpansion, invalid filters and zlib trailing bytes', async () => {
  assert.equal(typeof images.validatePngContent, 'function');
  const compressed = [deflateSync(Buffer.alloc(500000)), deflateSync(Buffer.from([5, 12, 34, 56, 78])),
    Buffer.concat([deflateSync(Buffer.from([0, 12, 34, 56, 78])), Buffer.from([0])])];
  for (const data of compressed)
    await assert.rejects(images.validatePngContent(png(1, 1, [], data).toString('base64'), async () => {}), /scanline|inflated|filter|deflate|zlib/i);
});

test('strict PNG content validation accepts complete interlaced Adam7 scanlines', async () => {
  assert.equal(typeof images.validatePngContent, 'function');
  const header = Buffer.alloc(13); header.writeUInt32BE(2); header.writeUInt32BE(2, 4); header[8] = 8; header[9] = 6; header[12] = 1;
  // 2x2 Adam7 has a 1-pixel row in passes 1 and 6, then a 2-pixel row in pass 7.
  const scanlines = Buffer.from([0, 1, 2, 3, 4, 0, 5, 6, 7, 8, 0, 9, 10, 11, 12, 13, 14, 15, 16]);
  const interlaced = Buffer.concat([signature, chunk('IHDR', header), chunk('IDAT', deflateSync(scanlines)), chunk('IEND')]).toString('base64');
  await images.validatePngContent(interlaced, async () => {});
  await assert.rejects(images.validatePngContent(Buffer.concat([signature, chunk('IHDR', header), chunk('IDAT', deflateSync(scanlines.subarray(0, -1))), chunk('IEND')]).toString('base64'), async () => {}), /scanline|inflated/i);
});

test('JSON import and clipboard publication reject malformed PNG atomically and decode each distinct image', async () => {
  assert.equal(typeof model.parseScreenForPublication, 'function');
  assert.equal(typeof interaction.pasteClipboardForPublication, 'function');
  const original = promoteScreen({ version: 1, id: 'menu', title: 'TIDEFRAME', elements: [
    { id: 'art', text: '', x: 0, y: 0, width: .4, height: .2, kind: 'button', action: '' }
  ] });
  original.elements[0].image = incompletePng;
  let published = 'OLD_SCREEN'; const reports = [];
  const controller = interaction.createPublicationController();
  await controller.run(() => model.parseScreenForPublication(JSON.stringify(original), async () => {}), { apply: value => { published = value; }, report: (...args) => reports.push(args) });
  assert.equal(published, 'OLD_SCREEN'); assert.equal(reports.at(-1)[1], true);
  await assert.rejects(interaction.pasteClipboardForPublication(promoteScreen({ ...original, elements: [] }), encodeClipboard(original, ['art']), async () => {}), /scanline|inflated/i);
  original.elements[0].image = fixture;
  original.elements.push({ ...original.elements[0], id: 'art2', x: .5 });
  let decodes = 0;
  const accepted = await model.parseScreenForPublication(JSON.stringify(original), async () => { decodes++; });
  assert.equal(accepted.elements.length, 2); assert.equal(decodes, 1);
});

function completePng(width, height) {
  const rows = Buffer.alloc(height * (1 + width * 4));
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const at = y * (1 + width * 4) + 1 + x * 4;
    rows[at] = x % 256; rows[at + 1] = y % 256; rows[at + 2] = 127; rows[at + 3] = 128;
  }
  return png(width, height, [], deflateSync(rows));
}
function photoFile(name = 'photo.jpg', type = 'image/jpeg', bytes = Buffer.from([255, 216, 255, 224])) {
  return { name, type, size: bytes.length, slice: (start, end) => ({ arrayBuffer: async () => bytes.subarray(start, end) }) };
}

test('photo dimensions fit 1024 pixels, preserve aspect ratio and never enlarge small images', () => {
  assert.equal(typeof images.fitImageSize, 'function');
  assert.deepEqual(images.fitImageSize(4000, 3000), { width: 1024, height: 768 });
  assert.deepEqual(images.fitImageSize(500, 1000), { width: 500, height: 1000 });
  assert.deepEqual(images.fitImageSize(1, 40000000), { width: 1, height: 1024 });
  for (const dimensions of [[0, 1], [1, -1], [NaN, 2], [Infinity, 1], [3.5, 4], [8001, 5000]])
    assert.throws(() => images.fitImageSize(...dimensions), /dimensions|40 megapixels/i);
});

test('photo importer routes browser-supported formats through one static PNG normalization (codec fakes)', async () => {
  assert.equal(typeof images.loadImageFile, 'function');
  for (const [name, type] of [['art.png', 'image/png'], ['photo.jpg', 'image/jpeg'], ['photo.webp', 'image/webp'],
    ['photo.avif', 'image/avif'], ['photo.bmp', 'image/bmp'], ['animation.gif', 'image/gif']]) {
    let closed = 0, encoded = 0, checked = 0;
    const file = photoFile(name, type), normalized = completePng(1024, 576);
    const result = await images.loadImageFile(file, {
      decode: async source => { assert.equal(source, file); return { width: 4096, height: 2304, close: () => closed++ }; },
      encode: async (source, size) => { assert.equal(source.width, 4096); assert.deepEqual(size, { width: 1024, height: 576 }); encoded++; return normalized; },
      verifyPng: async (base64, size) => { checked++; assert.equal(size.width, 1024); assert.equal(size.height, 576); assert.equal(base64, normalized.toString('base64')); }
    });
    assert.equal(result, normalized.toString('base64')); assert.equal(closed, 1); assert.equal(encoded, 1); assert.equal(checked, 1);
  }
});

test('photo importer refuses over-64MiB sources and SVG before decode (codec fakes)', async () => {
  assert.equal(typeof images.loadImageFile, 'function');
  assert.equal(images.MAX_SOURCE_BYTES, 67108864); assert.equal(images.MAX_SOURCE_PIXELS, 40000000);
  let decoded = false;
  const options = { decode: async () => { decoded = true; throw Error('Must not decode'); } };
  await assert.rejects(images.loadImageFile({ size: 67108865, slice: () => { throw Error('Must not read'); } }, options), /64 MiB/i);
  for (const file of [photoFile('art.svg', 'image/svg+xml'), photoFile('hidden.jpg', 'image/jpeg', Buffer.from('<?xml version="1.0"?><svg xmlns="http://www.w3.org/2000/svg"/>'))])
    await assert.rejects(images.loadImageFile(file, options), /SVG.*PNG|SVG.*JPG/i);
  assert.equal(decoded, false);
});

test('40MP and decoder failures produce clear errors and release decoded sources (codec fakes)', async () => {
  assert.equal(typeof images.loadImageFile, 'function');
  let closed = 0, encoded = false;
  await assert.rejects(images.loadImageFile(photoFile(), {
    decode: async () => ({ width: 8001, height: 5000, close: () => closed++ }), encode: async () => { encoded = true; }
  }), /40 megapixels/i);
  assert.equal(closed, 1); assert.equal(encoded, false);
  for (const extension of ['heic', 'tiff'])
    await assert.rejects(images.loadImageFile(photoFile(`photo.${extension}`, `image/${extension}`), {
      decode: async () => { throw Error('No installed browser decoder'); }
    }), /save.*JPG.*PNG/i);
});

test('small-photo normalization keeps dimensions and rejects a bad encoder result atomically (codec fakes)', async () => {
  assert.equal(typeof images.loadImageFile, 'function');
  let closed = 0;
  const options = { decode: async () => ({ width: 64, height: 32, close: () => closed++ }),
    encode: async (source, size) => { assert.deepEqual(size, { width: 64, height: 32 }); return completePng(64, 32); }, verifyPng: async () => {} };
  const result = await images.loadImageFile(photoFile(), options);
  assert.equal(images.validatePngBase64(result).width, 64); assert.equal(closed, 1);
  await assert.rejects(images.loadImageFile(photoFile(), { ...options, encode: async () => Buffer.from('not PNG') }), /PNG/i);
  assert.equal(closed, 2);
  await assert.rejects(images.loadImageFile(photoFile(), { ...options, encode: async () => completePng(1, 1) }), /dimensions/i);
  assert.equal(closed, 3);
});

test('PNG transparency chunks match backend legality before JSON publication', async () => {
  const rgbaWithTransparency = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAAXRSTlMAQObYZgAAAA1JREFUeJxj4FGy8AMAAVkAtUbLKgcAAAAASUVORK5CYII=';
  assert.throws(() => images.validatePngBase64(rgbaWithTransparency), /transparency|tRNS/i);
  const screen = model.createScreen(); screen.elements[0].image = rgbaWithTransparency;
  let decoded = false; await assert.rejects(model.parseScreenForPublication(JSON.stringify(screen), async () => { decoded = true; }), /transparency|tRNS/i); assert.equal(decoded, false);
  const gray = (depth, extras) => { const header = Buffer.alloc(13); header.writeUInt32BE(1); header.writeUInt32BE(1, 4); header[8] = depth; header[9] = 0; return Buffer.concat([signature, chunk('IHDR', header), ...extras, chunk('IDAT', deflateSync(Buffer.from([0, 0]))), chunk('IEND')]).toString('base64'); };
  assert.equal(images.validatePngBase64(gray(8, [chunk('tRNS', Buffer.from([0, 128]))])).colorType, 0);
  for (const extras of [[chunk('tRNS', Buffer.from([1, 0]))], [chunk('tRNS', Buffer.alloc(1))], [chunk('tRNS', Buffer.alloc(2)), chunk('tRNS', Buffer.alloc(2))]]) assert.throws(() => images.validatePngBase64(gray(8, extras)), /transparency|tRNS/i);
});

test('4K static RGBA scanlines validate while pixel cap rejects taller or square overflow', async () => {
  const bytes = completePng(3840, 2160), metadata = await images.validatePngContent(bytes.toString('base64'), async () => {});
  assert.equal(metadata.width, 3840); assert.equal(metadata.height, 2160); assert.equal(metadata.colorType, 6);
  for (const [width, height] of [[3840, 2161], [2881, 2881]]) assert.throws(() => images.validatePngBase64(png(width, height).toString('base64')), /8294400|dimensions/i);
  assert.equal(images.validatePngBase64(png(2880, 2880).toString('base64')).width, 2880);
});
