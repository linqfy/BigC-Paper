// Reads single files out of the player's own Minecraft client jar (a zip) for the editor's item-icon preview and the
// item-icons table. Nothing is extracted to disk or copied into a pack. Built-in modules only.
import zlib from 'node:zlib';
import os from 'node:os';
import path from 'node:path';

const LOCAL = 0x04034b50, CENTRAL = 0x02014b50, END = 0x06054b50, END64 = 0x06064b50, LOCATOR64 = 0x07064b50;

/** The 26.2 client jar of the standard launcher, unless TIDEFRAME_CLIENT_JAR names another. */
export function defaultClientJar(env = process.env) {
  if (env.TIDEFRAME_CLIENT_JAR) return path.resolve(env.TIDEFRAME_CLIENT_JAR);
  const minecraft = env.APPDATA ? path.join(env.APPDATA, '.minecraft') : path.join(os.homedir(), '.minecraft');
  return path.join(minecraft, 'versions', '26.2', '26.2.jar');
}

/** A zip in memory: {@code has(name)} and {@code read(name)} (bytes, checked against the stored size and CRC-32). */
export function openJar(buffer) {
  let end = -1;
  for (let i = buffer.length - 22; i >= Math.max(0, buffer.length - 22 - 0xffff); i--)
    if (buffer.readUInt32LE(i) === END && i + 22 + buffer.readUInt16LE(i + 20) === buffer.length) { end = i; break; }
  if (end < 0) throw new Error('Not a zip file');
  let count = buffer.readUInt16LE(end + 10), offset = buffer.readUInt32LE(end + 16);
  if (count === 0xffff || offset === 0xffffffff) {
    if (end < 20 || buffer.readUInt32LE(end - 20) !== LOCATOR64) throw new Error('Zip64 locator missing');
    const record = Number(buffer.readBigUInt64LE(end - 12));
    if (buffer.readUInt32LE(record) !== END64) throw new Error('Zip64 end record missing');
    count = Number(buffer.readBigUInt64LE(record + 32)); offset = Number(buffer.readBigUInt64LE(record + 48));
  }
  const entries = new Map();
  for (let i = 0, p = offset; i < count; i++) {
    if (buffer.readUInt32LE(p) !== CENTRAL) throw new Error('Bad zip directory');
    const nameLength = buffer.readUInt16LE(p + 28), extraLength = buffer.readUInt16LE(p + 30), commentLength = buffer.readUInt16LE(p + 32);
    const entry = { method: buffer.readUInt16LE(p + 10), crc: buffer.readUInt32LE(p + 16), packed: buffer.readUInt32LE(p + 20), size: buffer.readUInt32LE(p + 24), local: buffer.readUInt32LE(p + 42) };
    // Zip64 sizes and offset follow in the extra field, in this order, for the fields set to 0xffffffff.
    for (let q = p + 46 + nameLength, stop = q + extraLength; q + 4 <= stop; q += 4 + buffer.readUInt16LE(q + 2)) {
      if (buffer.readUInt16LE(q) !== 1) continue;
      let r = q + 4;
      if (entry.size === 0xffffffff) { entry.size = Number(buffer.readBigUInt64LE(r)); r += 8; }
      if (entry.packed === 0xffffffff) { entry.packed = Number(buffer.readBigUInt64LE(r)); r += 8; }
      if (entry.local === 0xffffffff) entry.local = Number(buffer.readBigUInt64LE(r));
      break;
    }
    const name = buffer.toString('utf8', p + 46, p + 46 + nameLength);
    if (!entries.has(name)) entries.set(name, entry);
    p += 46 + nameLength + extraLength + commentLength;
  }
  return {
    has: name => entries.has(name),
    read(name) {
      const entry = entries.get(name);
      if (!entry) return null;
      if (buffer.readUInt32LE(entry.local) !== LOCAL) throw new Error(`Bad zip entry ${name}`);
      const start = entry.local + 30 + buffer.readUInt16LE(entry.local + 26) + buffer.readUInt16LE(entry.local + 28);
      const packed = buffer.subarray(start, start + entry.packed);
      const data = entry.method === 0 ? packed : entry.method === 8 ? zlib.inflateRawSync(packed) : null;
      if (!data || data.length !== entry.size || zlib.crc32(data) !== entry.crc) throw new Error(`Unreadable zip entry ${name}`);
      return data;
    }
  };
}

/** Width, height and an alpha reader for a PNG (all colour types; not interlaced), as the client reads it into RGBA. */
export function decodePng(png) {
  if (png.readUInt32BE(0) !== 0x89504e47) throw new Error('Not a PNG');
  let pos = 8, header = null, palette = null, transparency = null; const chunks = [];
  while (pos + 8 <= png.length) {
    const length = png.readUInt32BE(pos), type = png.toString('latin1', pos + 4, pos + 8), body = png.subarray(pos + 8, pos + 8 + length);
    if (type === 'IHDR') header = { width: body.readUInt32BE(0), height: body.readUInt32BE(4), depth: body[8], color: body[9], interlace: body[12] };
    else if (type === 'PLTE') palette = body; else if (type === 'tRNS') transparency = body; else if (type === 'IDAT') chunks.push(body); else if (type === 'IEND') break;
    pos += 12 + length;
  }
  const { width, height, depth, color, interlace } = header;
  if (interlace) throw new Error('Interlaced PNG');
  const channels = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[color];
  if (!channels || color === 3 && !palette) throw new Error('Unsupported PNG');
  const bits = channels * depth, stride = Math.ceil(width * bits / 8), step = Math.max(1, bits >> 3);
  const raw = zlib.inflateSync(Buffer.concat(chunks)), rows = Buffer.alloc(stride * height);
  for (let y = 0; y < height; y++) {
    const filter = raw[y * (stride + 1)], line = y * (stride + 1) + 1;
    for (let i = 0; i < stride; i++) {
      const a = i >= step ? rows[y * stride + i - step] : 0, b = y ? rows[(y - 1) * stride + i] : 0, c = y && i >= step ? rows[(y - 1) * stride + i - step] : 0;
      let value = raw[line + i];
      if (filter === 1) value += a; else if (filter === 2) value += b; else if (filter === 3) value += (a + b) >> 1;
      else if (filter === 4) { const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c); value += pa <= pb && pa <= pc ? a : pb <= pc ? b : c; }
      rows[y * stride + i] = value & 255;
    }
  }
  const sample = (x, y, channel) => {
    if (depth >= 8) return rows[y * stride + (x * channels + channel) * (depth >> 3)];
    const bit = (x * channels + channel) * depth;
    return (rows[y * stride + (bit >> 3)] >> (8 - depth - (bit & 7))) & ((1 << depth) - 1);
  };
  const alpha = (x, y) => {
    if (color === 6) return sample(x, y, 3);
    if (color === 4) return sample(x, y, 1);
    if (color === 3) { const index = sample(x, y, 0); return transparency && index < transparency.length ? transparency[index] : 255; }
    if (!transparency) return 255;
    // Grey or RGB with one transparent colour.
    for (let ch = 0; ch < channels; ch++) {
      const value = depth === 16 ? rows.readUInt16BE(y * stride + (x * channels + ch) * 2) : sample(x, y, ch);
      if (value !== transparency.readUInt16BE(ch * 2)) return 255;
    }
    return 0;
  };
  return { width, height, alpha };
}
/** Columns the client counts for a glyph cell at (0, 0): up to the last column with any non-transparent pixel. */
export function inkWidth(image, cellWidth, cellHeight) {
  for (let x = cellWidth - 1; x >= 0; x--) for (let y = 0; y < cellHeight; y++) if (image.alpha(x, y) !== 0) return x + 1;
  return 0;
}
/** item-icons.tsv (see tools/item-icons-table.mjs) as a map: item -> { texture, width, height, rows, ink, block, tinted }. */
export function parseIconTable(text) {
  const icons = new Map();
  for (const line of text.split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const [item, texture, width, height, rows, ink, flags = ''] = line.split('\t');
    icons.set(item, { texture, width: Number(width), height: Number(height), rows: Number(rows), ink: Number(ink), block: flags.includes('b'), tinted: flags.includes('t') });
  }
  return icons;
}
