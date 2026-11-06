import test from 'node:test';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
import { createScreen, upgradeLogic, upgradeGradient, validateScreen, serializeScreen } from './model.mjs';

/** A plain RGBA PNG in Base64, {@code size} pixels square. */
function pngBase64(size) {
  const raw = Buffer.alloc((size * 4 + 1) * size, 0xff);
  for (let y = 0; y < size; y++) raw[y * (size * 4 + 1)] = 0;
  const chunk = (type, body) => { const head = Buffer.alloc(8); head.writeUInt32BE(body.length); head.write(type, 4, 'latin1'); const crc = Buffer.alloc(4); crc.writeUInt32BE(zlib.crc32(Buffer.concat([Buffer.from(type, 'latin1'), body]))); return Buffer.concat([head, body, crc]); };
  const header = Buffer.alloc(13); header.writeUInt32BE(size); header.writeUInt32BE(size, 4); header[8] = 8; header[9] = 6;
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', header), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]).toString('base64');
}
const framed = (style, version9 = true) => {
  const s = version9 ? upgradeLogic(createScreen()) : upgradeGradient(createScreen());
  s.elements[0] = { ...s.elements[0], image: pngBase64(12), style: { ...s.elements[0].style, ...style } };
  return s;
};

test('9-slice is schema 9: a fixed border that leaves the image a middle, 1 to 4 image pixels per menu pixel', () => {
  assert.match(serializeScreen(validateScreen(framed({ slice: 3, sliceScale: 2 }))), /"slice": 3,\s+"sliceScale": 2/);
  assert.doesNotThrow(() => validateScreen(framed({ slice: 5 })), '5 + 5 leaves 2 of 12 pixels');
  assert.throws(() => validateScreen(framed({ slice: 6 })), /must leave the image a middle \(it is 12 x 12\)/);
  assert.throws(() => validateScreen(framed({ slice: 65 })), /slice must be an integer 0-64/);
  assert.throws(() => validateScreen(framed({ slice: 2.5 })), /slice must be an integer 0-64/);
  assert.throws(() => validateScreen(framed({ slice: 3, sliceScale: 5 })), /sliceScale must be an integer 1-4/);
  assert.throws(() => validateScreen(framed({ slice: 3 }, false)), 'schema 8 has no slice');
  const noImage = upgradeLogic(createScreen()); noImage.elements[0] = { ...noImage.elements[0], style: { ...noImage.elements[0].style, slice: 4 } };
  assert.doesNotThrow(() => validateScreen(noImage), 'without an image the border waits for one, as in the plugin');
});
