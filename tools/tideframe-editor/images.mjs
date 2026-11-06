// Original, portable PNG structure validation. No filesystem, network or Node dependencies.
export const MAX_PNG_BYTES = 41943040;
export const MAX_PNG_SIDE = 3840;
export const MAX_SOURCE_BYTES = 67108864;
export const MAX_PNG_PIXELS = 8294400;
export const MAX_SOURCE_PIXELS = 40000000;
const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
const signature = [137, 80, 78, 71, 13, 10, 26, 10];

export function bytesToBase64(input) {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input), chunks = [];
  // Bounded flat chunks avoid millions of chained string nodes for 4K photos.
  for (let offset = 0; offset < bytes.length; offset += 12288) {
    const output = [], end = Math.min(bytes.length, offset + 12288);
    for (let i = offset; i < end; i += 3) {
      const word = (bytes[i] << 16) | ((bytes[i + 1] ?? 0) << 8) | (bytes[i + 2] ?? 0);
      output.push(alphabet[(word >>> 18) & 63], alphabet[(word >>> 12) & 63], i + 1 < bytes.length ? alphabet[(word >>> 6) & 63] : '=', i + 2 < bytes.length ? alphabet[word & 63] : '=');
    }
    chunks.push(output.join(''));
  }
  return chunks.join('');
}
function decodeBase64(raw) {
  if (typeof raw !== 'string') throw Error('Image must be canonical base64 PNG or null.');
  if (raw.length > Math.ceil(MAX_PNG_BYTES / 3) * 4) throw Error('PNG exceeds the 40 MiB byte limit.');
  if (!raw.length || raw.length % 4)
    throw Error('Image must contain canonical base64 PNG, without a data URL, path, URL or whitespace.');
  const padding = raw.endsWith('==') ? 2 : raw.endsWith('=') ? 1 : 0;
  for (let i = 0; i < raw.length - padding; i++)
    if (alphabet.indexOf(raw[i]) < 0) throw Error('Image must contain canonical base64 PNG, without a data URL, path, URL or whitespace.');
  const size = raw.length / 4 * 3 - padding;
  if (size > MAX_PNG_BYTES) throw Error('PNG exceeds the 40 MiB byte limit.');
  const bytes = new Uint8Array(size);
  for (let i = 0, at = 0; i < raw.length; i += 4) {
    const word = (alphabet.indexOf(raw[i]) << 18) | (alphabet.indexOf(raw[i + 1]) << 12)
      | ((raw[i + 2] === '=' ? 0 : alphabet.indexOf(raw[i + 2])) << 6) | (raw[i + 3] === '=' ? 0 : alphabet.indexOf(raw[i + 3]));
    if (at < size) bytes[at++] = (word >>> 16) & 255;
    if (at < size) bytes[at++] = (word >>> 8) & 255;
    if (at < size) bytes[at++] = word & 255;
  }
  if ((padding === 2 && (alphabet.indexOf(raw.at(-3)) & 15) !== 0) || (padding === 1 && (alphabet.indexOf(raw.at(-2)) & 3) !== 0)) throw Error('Image base64 is not canonical (discarded bits must be zero).');
  return bytes;
}
function crc(bytes, start, end) {
  let result = 0xffffffff;
  for (let i = start; i < end; i++) {
    result ^= bytes[i];
    for (let bit = 0; bit < 8; bit++) result = (result >>> 1) ^ ((result & 1) ? 0xedb88320 : 0);
  }
  return (result ^ 0xffffffff) >>> 0;
}
export function validatePngBase64(raw) {
  const bytes = decodeBase64(raw), view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (!signature.every((byte, i) => bytes[i] === byte)) throw Error('Image must have a PNG signature.');
  let at = 8, width = 0, height = 0, color = -1, depth = 0, interlace = 0, header = false, palette = false, transparency = false, paletteEntries = 0, data = false, dataEnded = false;
  const dataChunks = [];
  while (at < bytes.length) {
    if (bytes.length - at < 12) throw Error('PNG chunk header is truncated.');
    const size = view.getUint32(at), end = at + 12 + size;
    if (end > bytes.length) throw Error('PNG chunk length exceeds its byte bounds.');
    const type = String.fromCharCode(...bytes.subarray(at + 4, at + 8));
    if (!/^[A-Za-z]{2}[A-Z][A-Za-z]$/.test(type)) throw Error('PNG chunk type is invalid.');
    if (crc(bytes, at + 4, at + 8 + size) !== view.getUint32(at + 8 + size)) throw Error(`PNG ${type} CRC is invalid.`);
    if (!header && type !== 'IHDR') throw Error('PNG IHDR must be the first chunk.');
    if (['acTL', 'fcTL', 'fdAT'].includes(type)) throw Error('Only static PNG images are supported; animated PNG is rejected.');
    if (data && type !== 'IDAT') dataEnded = true;
    if (type === 'IHDR') {
      if (header || size !== 13) throw Error('PNG must contain exactly one 13-byte IHDR.');
      width = view.getUint32(at + 8); height = view.getUint32(at + 12); depth = bytes[at + 16]; color = bytes[at + 17];
      interlace = bytes[at + 20];
      if (width < 1 || height < 1 || width > MAX_PNG_SIDE || height > MAX_PNG_SIDE || width * height > MAX_PNG_PIXELS) throw Error('PNG dimensions require each side 1-3840 pixels and at most 8294400 pixels.');
      const depths = { 0: [1, 2, 4, 8, 16], 2: [8, 16], 3: [1, 2, 4, 8], 4: [8, 16], 6: [8, 16] };
      if (!depths[color]?.includes(depth) || bytes[at + 18] !== 0 || bytes[at + 19] !== 0 || bytes[at + 20] > 1)
        throw Error('PNG IHDR encoding is invalid.');
      header = true;
    } else if (type === 'PLTE') {
      if (palette || data || color === 0 || color === 4 || size < 3 || size > 768 || size % 3 || (color === 3 && size / 3 > 2 ** depth))
        throw Error('PNG palette is invalid.');
      palette = true; paletteEntries = size / 3;
    } else if (type === 'tRNS') {
      const valid = color === 0 ? size === 2 : color === 2 ? size === 6 : color === 3 ? palette && size > 0 && size <= paletteEntries : false;
      if (transparency || data || !valid) throw Error('PNG tRNS transparency chunk is invalid.');
      if (color === 0 && view.getUint16(at + 8) > 2 ** depth - 1) throw Error('PNG grayscale transparency sample is invalid.');
      transparency = true;
    } else if (type === 'IDAT') {
      if (dataEnded || (color === 3 && !palette)) throw Error('PNG IDAT order or palette is invalid.');
      dataChunks.push(bytes.subarray(at + 8, at + 8 + size));
      data = true;
    } else if (type === 'IEND') {
      if (!data || size !== 0 || end !== bytes.length) throw Error('PNG IEND must end the file without trailing bytes.');
      return { bytes, width, height, bitDepth: depth, colorType: color, interlace, dataChunks };
    } else if (type[0] === type[0].toUpperCase()) throw Error(`Unsupported critical PNG chunk: ${type}.`);
    at = end;
  }
  throw Error('PNG is missing IEND.');
}

function scanlineSizes({ width, height, bitDepth, colorType, interlace }) {
  const samples = { 0: 1, 2: 3, 3: 1, 4: 2, 6: 4 }[colorType], rows = [];
  const passes = interlace ? [[0, 0, 8, 8], [4, 0, 8, 8], [0, 4, 4, 8], [2, 0, 4, 4], [0, 2, 2, 4], [1, 0, 2, 2], [0, 1, 1, 2]] : [[0, 0, 1, 1]];
  for (const [x, y, dx, dy] of passes) {
    const columns = Math.max(0, Math.ceil((width - x) / dx)), count = Math.max(0, Math.ceil((height - y) / dy));
    if (columns) for (let row = 0; row < count; row++) rows.push(1 + Math.ceil(columns * samples * bitDepth / 8));
  }
  return rows;
}
async function validateScanlines(metadata) {
  if (typeof DecompressionStream !== 'function' || typeof ReadableStream !== 'function')
    throw Error('Strict PNG validation requires browser DecompressionStream support.');
  const rows = scanlineSizes(metadata), expected = rows.reduce((sum, size) => sum + size, 0);
  // Bound even RGBA16/Adam7 to the 4K pixel ceiling before allocation.
  if (expected > MAX_PNG_PIXELS * 8 + MAX_PNG_SIDE * 8) throw Error('PNG inflated scanlines exceed the supported limit.');
  let part = 0, offset = 0;
  const source = new ReadableStream({ pull(controller) {
    while (part < metadata.dataChunks.length && offset === metadata.dataChunks[part].length) { part++; offset = 0; }
    if (part === metadata.dataChunks.length) { controller.close(); return; }
    const chunk = metadata.dataChunks[part], end = Math.min(chunk.length, offset + 128);
    controller.enqueue(chunk.subarray(offset, end)); offset = end;
  } });
  const reader = source.pipeThrough(new DecompressionStream('deflate')).getReader();
  const result = new Uint8Array(expected);
  let size = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (size + value.length > expected) throw Error('PNG inflated scanline bytes exceed the exact IHDR dimensions.');
      result.set(value, size); size += value.length;
    }
  } catch (error) {
    try { await reader.cancel(); } catch { /* The failing decoder may already be closed. */ }
    throw Error(`PNG deflate/scanline validation failed: ${error.message}`);
  } finally { reader.releaseLock(); }
  if (size !== expected) throw Error('PNG inflated scanline bytes do not match the exact IHDR dimensions.');
  for (let row = 0, at = 0; row < rows.length; at += rows[row++])
    if (result[at] > 4) throw Error('PNG scanline filter must be from 0 through 4.');
}

export async function validatePngContent(base64, decode = verifyBrowserPng) {
  const metadata = validatePngBase64(base64);
  await validateScanlines(metadata);
  await decode(base64, metadata);
  return metadata;
}
export async function validateScreenImages(screen, decode = verifyBrowserPng) {
  // Hover images too: the plugin decodes both (E3).
  for (const image of new Set(screen.elements.flatMap(element => [element.image, element.hoverImage]).filter(image => image !== null && image !== undefined)))
    await validatePngContent(image, decode);
  return screen;
}

// Browser decode is separate from portable structural checks. Canvas reads every
// decoded RGBA pixel; we keep the original PNG bytes, preserving its alpha.
export async function verifyBrowserPng(base64, { width, height }) {
  const image = new Image(); image.decoding = 'async'; image.src = `data:image/png;base64,${base64}`;
  try { await image.decode(); } catch { throw Error('PNG could not be decoded by this browser.'); }
  if (image.naturalWidth !== width || image.naturalHeight !== height) throw Error('PNG decoded dimensions do not match IHDR.');
  const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
  const context = canvas.getContext('2d', { willReadFrequently: true });
  if (!context) throw Error('PNG decoding requires a canvas context.');
  context.drawImage(image, 0, 0);
  context.getImageData(0, 0, width, height);
}
export async function loadPngFile(file, decode = verifyBrowserPng) {
  if (!file || !Number.isFinite(file.size) || file.size > MAX_PNG_BYTES) throw Error('PNG exceeds the 40 MiB byte limit.');
  const base64 = bytesToBase64(new Uint8Array(await file.arrayBuffer()));
  await validatePngContent(base64, decode);
  return base64;
}

/** Browser import only. The server still receives strictly bounded static PNG. */
export function fitImageSize(width, height, quality = { width: 1024 }) {
  if (!Number.isSafeInteger(width) || !Number.isSafeInteger(height) || width < 1 || height < 1)
    throw Error('Image dimensions must be positive whole pixels.');
  if (width > MAX_SOURCE_PIXELS / height) throw Error('Image exceeds the 40 megapixels limit.');
  const side = quality?.width;
  if (!Number.isSafeInteger(side) || side < 256 || side > MAX_PNG_SIDE) throw Error('Photo quality width must be an integer from 256 to 3840.');
  const scale = Math.min(1, side / Math.max(width, height), Math.sqrt(MAX_PNG_PIXELS / (width * height)));
  return { width: Math.max(1, Math.floor(width * scale)), height: Math.max(1, Math.floor(height * scale)) };
}

export async function decodeBrowserImage(file) {
  let bitmapError;
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
      return { source: bitmap, width: bitmap.width, height: bitmap.height, close: () => bitmap.close() };
    } catch (error) { bitmapError = error; }
  }
  if (typeof Image !== 'function' || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function')
    throw bitmapError ?? Error('Browser image decoding is unavailable.');
  const url = URL.createObjectURL(file), image = new Image(); image.decoding = 'async'; image.src = url;
  try {
    await image.decode();
    return { source: image, width: image.naturalWidth, height: image.naturalHeight, close: () => URL.revokeObjectURL(url) };
  } catch (error) { URL.revokeObjectURL(url); throw error; }
}

export async function encodeBrowserPng(decoded, { width, height }) {
  const canvas = typeof OffscreenCanvas === 'function' ? new OffscreenCanvas(width, height) : document.createElement('canvas');
  canvas.width = width; canvas.height = height;
  const context = canvas.getContext('2d', { alpha: true, willReadFrequently: true });
  if (!context) throw Error('Photo conversion requires a browser canvas.');
  context.imageSmoothingEnabled = true; context.imageSmoothingQuality = 'high';
  context.drawImage(decoded.source, 0, 0, width, height);
  context.getImageData(0, 0, width, height);
  const blob = typeof canvas.convertToBlob === 'function'
    ? await canvas.convertToBlob({ type: 'image/png' })
    : await new Promise((resolve, reject) => canvas.toBlob(value => value ? resolve(value) : reject(Error('PNG conversion failed.')), 'image/png'));
  return new Uint8Array(await blob.arrayBuffer());
}

/** Injectable codecs permit policy tests without claiming a real browser codec ran. */
export async function loadImageFile(file, { decode = decodeBrowserImage, encode = encodeBrowserPng, verifyPng = verifyBrowserPng, quality = { width: 1024 } } = {}) {
  if (!file || !Number.isFinite(file.size) || file.size <= 0 || file.size > MAX_SOURCE_BYTES)
    throw Error('Image input must be nonempty and at most 64 MiB.');
  const name = String(file.name ?? '').toLowerCase(), type = String(file.type ?? '').toLowerCase();
  const svgError = () => Error('SVG is not a photo format accepted here. Save it as PNG or JPG first.');
  if (/\.svgz?$/.test(name) || type.includes('svg')) throw svgError();
  const header = new Uint8Array(await file.slice(0, 4096).arrayBuffer());
  // Vector/XML masquerading under a photo filename is rejected before decoding.
  if (/^\s*</.test(new TextDecoder().decode(header))) throw svgError();
  let decoded;
  try { decoded = await decode(file); }
  catch { throw Error('This browser could not decode the image. Save it as JPG or PNG and try again. HEIC/TIFF support depends on the browser.'); }
  try {
    const size = fitImageSize(decoded.width, decoded.height, quality);
    const bytes = await encode(decoded, size);
    if (!(bytes instanceof Uint8Array) || bytes.length > MAX_PNG_BYTES) throw Error('Converted PNG exceeds the 40 MiB byte limit.');
    const base64 = bytesToBase64(bytes), metadata = await validatePngContent(base64, verifyPng);
    if (metadata.width !== size.width || metadata.height !== size.height) throw Error('Converted PNG dimensions differ from the fitted image.');
    return base64;
  } finally { decoded?.close?.(); }
}
