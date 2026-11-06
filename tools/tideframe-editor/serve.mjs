import http from 'node:http';
import { readFile, writeFile, rename, mkdir, rm, readdir, lstat, realpath, stat } from 'node:fs/promises';
import { randomBytes, timingSafeEqual } from 'node:crypto';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import { openJar, defaultClientJar, parseIconTable } from './client-jar.mjs';

const folder = path.dirname(fileURLToPath(import.meta.url));
const files = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']], ['/index.html', ['index.html', 'text/html; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']], ['/app.mjs', ['app.mjs', 'text/javascript; charset=utf-8']],
  ['/model.mjs', ['model.mjs', 'text/javascript; charset=utf-8']], ['/tideframe.woff', ['tideframe.woff', 'font/woff']],
  ['/advanced-settings.mjs', ['advanced-settings.mjs', 'text/javascript; charset=utf-8']],
  ['/advanced-ui.mjs', ['advanced-ui.mjs', 'text/javascript; charset=utf-8']],
  ['/interaction.mjs', ['interaction.mjs', 'text/javascript; charset=utf-8']],
  ['/authoring.mjs', ['authoring.mjs', 'text/javascript; charset=utf-8']],
  ['/workspace.mjs', ['workspace.mjs', 'text/javascript; charset=utf-8']],
  ['/templates.mjs', ['templates.mjs', 'text/javascript; charset=utf-8']],
  ['/presentation-tools.mjs', ['presentation-tools.mjs', 'text/javascript; charset=utf-8']],
  ['/themes.mjs', ['themes.mjs', 'text/javascript; charset=utf-8']],
  ['/images.mjs', ['images.mjs', 'text/javascript; charset=utf-8']],
  ['/i18n.mjs', ['i18n.mjs', 'text/javascript; charset=utf-8']],
  ['/play.mjs', ['play.mjs', 'text/javascript; charset=utf-8']],
  ['/play-ui.mjs', ['play-ui.mjs', 'text/javascript; charset=utf-8']],
  ['/multi-edit.mjs', ['multi-edit.mjs', 'text/javascript; charset=utf-8']],
  ['/resolution.mjs', ['resolution.mjs', 'text/javascript; charset=utf-8']],
  ['/checker.mjs', ['checker.mjs', 'text/javascript; charset=utf-8']],
  ['/page-templates.mjs', ['page-templates.mjs', 'text/javascript; charset=utf-8']],
  // 0.23, ideas 21-45.
  ['/expressions.mjs', ['expressions.mjs', 'text/javascript; charset=utf-8']],
  ['/richtext.mjs', ['richtext.mjs', 'text/javascript; charset=utf-8']],
  // Text that shrinks to fit its box.
  ['/textfit.mjs', ['textfit.mjs', 'text/javascript; charset=utf-8']],
  ['/style-clipboard.mjs', ['style-clipboard.mjs', 'text/javascript; charset=utf-8']],
  ['/layers.mjs', ['layers.mjs', 'text/javascript; charset=utf-8']],
  ['/project-map.mjs', ['project-map.mjs', 'text/javascript; charset=utf-8']],
  ['/icon-library.mjs', ['icon-library.mjs', 'text/javascript; charset=utf-8']],
  ['/tideframe.ttf', ['tideframe.ttf', 'font/ttf']]
]);
// Probar reads frame animations from plugins/BigCasares/tideframe/engine/images (next to screens), read only.
// Names follow the plugin's own rule for "frames.folder"; nothing outside that folder or behind a link is served.
const FRAMES_FOLDER = /^[a-z0-9_-]{1,32}(\/[a-z0-9_-]{1,32}){0,3}$/, FRAME_FILE = /^[A-Za-z0-9_-][A-Za-z0-9_.-]{0,63}\.(png|gif)$/i;
const natural = (a, b) => a.localeCompare(b, 'en', { numeric: true, sensitivity: 'base' });
async function inside(root, relative) {
  const target = path.resolve(root, relative), base = await realpath(root).catch(() => null);
  if (!base || !target.startsWith(path.resolve(root) + path.sep)) return null;
  try { const info = await lstat(target); if (info.isSymbolicLink()) return null; const real = await realpath(target); return real.startsWith(base + path.sep) ? { real, info } : null; }
  catch { return null; }
}
async function serverImage(pathname, imagesDir) {
  const rest = pathname.slice('/server-images/'.length);
  // The listing for the editor's checker: folders with frames and GIF names, without the extension.
  if (rest === '') {
    const names = new Set(), base = await realpath(imagesDir).catch(() => null);
    if (!base) return { json: { folders: [] } };
    const walk = async (relative, depth) => {
      for (const entry of await readdir(path.join(base, relative), { withFileTypes: true }).catch(() => [])) {
        const name = relative ? `${relative}/${entry.name}` : entry.name;
        if (entry.isDirectory() && FRAMES_FOLDER.test(name)) { names.add(name); if (depth < 3) await walk(name, depth + 1); }
        else if (entry.isFile() && /\.gif$/i.test(entry.name) && FRAMES_FOLDER.test(name.slice(0, -4))) names.add(name.slice(0, -4));
      }
    };
    await walk('', 0);
    return { json: { folders: [...names].sort().slice(0, 4096) } };
  }
  if (FRAMES_FOLDER.test(rest)) {
    // Like the plugin (frameImages): the folder's PNG files in natural order, else a GIF inside it; without the folder,
    // images/{folder}.gif.
    const dir = await inside(imagesDir, rest);
    if (!dir?.info.isDirectory()) {
      const gif = await inside(imagesDir, `${rest}.gif`);
      return gif?.info.isFile() ? { json: { type: 'gif', url: `/server-images/${rest}.gif` } } : null;
    }
    const names = (await readdir(dir.real)).filter(name => FRAME_FILE.test(name)).sort(natural);
    const pngs = names.filter(name => /\.png$/i.test(name)), inner = names.find(name => /\.gif$/i.test(name));
    if (pngs.length) return { json: { type: 'png', frames: pngs.slice(0, 1024).map(name => `/server-images/${rest}/${name}`) } };
    if (inner) return { json: { type: 'gif', url: `/server-images/${rest}/${inner}` } };
    return null;
  }
  const slash = rest.lastIndexOf('/'), dirPart = slash < 0 ? '' : rest.slice(0, slash), file = rest.slice(slash + 1);
  if (!FRAME_FILE.test(file) || dirPart && !FRAMES_FOLDER.test(dirPart)) return null;
  const found = await inside(imagesDir, rest);
  if (!found?.info.isFile() || found.info.size > 33554432) return null;
  return { file: found.real, type: /\.gif$/i.test(file) ? 'image/gif' : 'image/png' };
}
const MAX_PUBLISH_BYTES = 134217728;
function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = []; let size = 0;
    request.on('data', chunk => { size += chunk.length; if (size > MAX_PUBLISH_BYTES) { reject(Object.assign(new Error('Screen exceeds 128 MiB.'), { status: 413 })); request.destroy(); } else chunks.push(chunk); });
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8'))); request.on('error', reject);
  });
}
/** Uploads (idea 15): the raw bytes of one file, at most {@code limit}. */
function readBytes(request, limit, tooBig = 'Image exceeds 32 MiB.') {
  return new Promise((resolve, reject) => {
    const chunks = []; let size = 0, over = false;
    request.on('data', chunk => {
      size += chunk.length;
      // Too big: answer 413 and drop the rest (a client that keeps sending far past the limit is cut off).
      if (over) { if (size > limit * 4) request.destroy(); return; }
      if (size > limit) { over = true; chunks.length = 0; reject(Object.assign(new Error(tooBig), { status: 413 })); } else chunks.push(chunk);
    });
    request.on('end', () => resolve(Buffer.concat(chunks))); request.on('error', reject);
  });
}
const MAX_IMAGE_BYTES = 33554432;
/**
 * POST /upload-image?name=fire (a GIF: images/fire.gif) or ?name=fire/01.png (a frame: images/fire/01.png). Same guard
 * as publishing (this page's origin and token); only real GIF or PNG files, names of the plugin's frames rule, and no
 * replacing an existing file unless &replace=1. The plugin reads images/ again on its next reload.
 */
/** {@code folder}, with every link resolved, is {@code root} or inside it (a linked subfolder cannot lead outside). */
async function insideFolder(root, folder, message) {
  const [base, real] = await Promise.all([realpath(root), realpath(folder)]);
  if (real !== base && !real.startsWith(base + path.sep)) throw Object.assign(new Error(message), { status: 400 });
}
async function upload(request, screensDir, token, query) {
  const origin = request.headers.origin, host = request.headers.host;
  if (!host || origin !== `http://${host}`) throw Object.assign(new Error('Cross-origin upload rejected.'), { status: 403 });
  if (!sameToken(request.headers['x-tideframe-token'], token)) throw Object.assign(new Error('Missing or wrong publish token.'), { status: 403 });
  const name = query.get('name') ?? '', slash = name.lastIndexOf('/'), folderPart = slash < 0 ? '' : name.slice(0, slash), file = name.slice(slash + 1);
  const gifName = !name.includes('.') && FRAMES_FOLDER.test(name), frameName = FRAME_FILE.test(file) && /\.png$/i.test(file) && FRAMES_FOLDER.test(folderPart);
  if (!gifName && !frameName) throw Object.assign(new Error('Invalid image name: use a-z, 0-9, _ and - (fire, or fire/01.png).'), { status: 400 });
  const bytes = await readBytes(request, MAX_IMAGE_BYTES);
  const gif = bytes.length > 6 && /^GIF8[79]a$/.test(bytes.subarray(0, 6).toString('latin1'));
  const png = bytes.length > 8 && bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  if (gifName ? !gif : !png) throw Object.assign(new Error(gifName ? 'Expected a GIF file.' : 'Expected a PNG file.'), { status: 415 });
  const imagesDir = path.join(screensDir, '..', 'images'), relative = gifName ? `${name}.gif` : name;
  const target = path.resolve(imagesDir, relative);
  if (!target.startsWith(path.resolve(imagesDir) + path.sep)) throw Object.assign(new Error('Invalid image name.'), { status: 400 });
  await mkdir(path.dirname(target), { recursive: true });
  await insideFolder(imagesDir, path.dirname(target), 'Invalid image name.');
  const existing = await lstat(target).catch(() => null);
  if (existing && (existing.isSymbolicLink() || !existing.isFile())) throw Object.assign(new Error('Invalid image name.'), { status: 400 });
  if (existing && query.get('replace') !== '1') throw Object.assign(new Error('Image already exists.'), { status: 409 });
  const temp = `${target}.${randomBytes(6).toString('hex')}.tmp`;
  try { await writeFile(temp, bytes); await rename(temp, target); } finally { await rm(temp, { force: true }); }
  return { ok: true, file: relative, folder: gifName ? name : folderPart, bytes: bytes.length };
}
// Custom sounds (idea 30): plugins/BigCasares/tideframe/engine/sounds, next to screens; packed as tideframe:NAME.
const SOUND_NAME = /^[a-z0-9_]{1,32}$/, MAX_SOUND_BYTES = 2097152;
/** Channels of an Ogg Vorbis file (CustomSounds.vorbisChannels): 1 mono, 2 stereo; 0 when the bytes are not one. */
export function vorbisChannels(bytes) {
  if (!bytes || bytes.length < 58 || bytes.subarray(0, 4).toString('latin1') !== 'OggS' || bytes[4] !== 0) return 0;
  const packet = 27 + bytes[26];
  if (bytes.length < packet + 30 || bytes[packet] !== 1 || bytes.subarray(packet + 1, packet + 7).toString('latin1') !== 'vorbis') return 0;
  const channels = bytes[packet + 11];
  return channels >= 1 && channels <= 8 ? channels : 0;
}
/** The sounds the plugin would take: name, size and channels, in name order. */
async function serverSounds(soundsDir) {
  const base = await realpath(soundsDir).catch(() => null), out = [];
  if (!base) return out;
  for (const entry of (await readdir(base, { withFileTypes: true }).catch(() => [])).sort((a, b) => a.name.localeCompare(b.name))) {
    const name = entry.name.replace(/\.ogg$/, '');
    if (!entry.isFile() || !entry.name.endsWith('.ogg') || !SOUND_NAME.test(name)) continue;
    const found = await inside(soundsDir, entry.name);
    if (!found?.info.isFile() || found.info.size > MAX_SOUND_BYTES) continue;
    const channels = vorbisChannels(await readFile(found.real).catch(() => null));
    if (channels) out.push({ name, id: `tideframe:${name}`, bytes: found.info.size, channels });
    if (out.length >= 64) break;
  }
  return out;
}
/** POST /upload-sound?name=click: an Ogg Vorbis file (at most 2 MiB) becomes sounds/click.ogg. Same guard as publishing. */
async function uploadSound(request, screensDir, token, query) {
  const origin = request.headers.origin, host = request.headers.host;
  if (!host || origin !== `http://${host}`) throw Object.assign(new Error('Cross-origin upload rejected.'), { status: 403 });
  if (!sameToken(request.headers['x-tideframe-token'], token)) throw Object.assign(new Error('Missing or wrong publish token.'), { status: 403 });
  const name = query.get('name') ?? '';
  if (!SOUND_NAME.test(name)) throw Object.assign(new Error('Invalid sound name: use a-z, 0-9 and _ (up to 32).'), { status: 400 });
  const bytes = await readBytes(request, MAX_SOUND_BYTES, 'Sound exceeds 2 MiB.');
  const channels = vorbisChannels(bytes);
  if (!channels) throw Object.assign(new Error('Expected an Ogg Vorbis file (the only audio Minecraft plays).'), { status: 415 });
  const soundsDir = path.join(screensDir, '..', 'sounds'), target = path.resolve(soundsDir, `${name}.ogg`);
  if (!target.startsWith(path.resolve(soundsDir) + path.sep)) throw Object.assign(new Error('Invalid sound name: use a-z, 0-9 and _ (up to 32).'), { status: 400 });
  await mkdir(soundsDir, { recursive: true });
  await insideFolder(soundsDir, path.dirname(target), 'Invalid sound name: use a-z, 0-9 and _ (up to 32).');
  const existing = await lstat(target).catch(() => null);
  if (existing && (existing.isSymbolicLink() || !existing.isFile())) throw Object.assign(new Error('Invalid sound name: use a-z, 0-9 and _ (up to 32).'), { status: 400 });
  if (existing && query.get('replace') !== '1') throw Object.assign(new Error('Sound already exists.'), { status: 409 });
  const temp = `${target}.${randomBytes(6).toString('hex')}.tmp`;
  try { await writeFile(temp, bytes); await rename(temp, target); } finally { await rm(temp, { force: true }); }
  return { ok: true, name, id: `tideframe:${name}`, bytes: bytes.length, channels };
}
/**
 * The runtime's side of the editor (EditorBridge.java): GET /server-status reads engine/editor-status.json (the
 * live screens, their warnings and file times, or why the last load failed); POST /open-in-game writes editor-open.json,
 * which the plugin reads once to open a screen for a player, and answers in the status after the request's id. Same
 * guard as publishing.
 */
async function serverStatus(screensDir) {
  try { return JSON.parse(await readFile(path.join(screensDir, '..', 'editor-status.json'), 'utf8')); }
  catch { return { ok: null }; }
}
async function openInGame(request, screensDir, token) {
  const origin = request.headers.origin, host = request.headers.host;
  if (!host || origin !== `http://${host}`) throw Object.assign(new Error('Cross-origin publish rejected.'), { status: 403 });
  if (!sameToken(request.headers['x-tideframe-token'], token)) throw Object.assign(new Error('Missing or wrong publish token.'), { status: 403 });
  let body; try { body = JSON.parse(await readBody(request)); } catch { body = null; }
  if (!body || typeof body.screen !== 'string' || !/^[a-z][a-z0-9_-]{0,31}$/.test(body.screen) || body.player !== undefined && body.player !== '' && !/^[A-Za-z0-9_]{1,16}$/.test(body.player))
    throw Object.assign(new Error('Invalid open request.'), { status: 400 });
  const folder = path.join(screensDir, '..'), target = path.join(folder, 'editor-open.json'), temp = path.join(folder, `.editor-open.${randomBytes(6).toString('hex')}.tmp`);
  await mkdir(folder, { recursive: true });
  const requestId = randomBytes(8).toString('hex');
  try { await writeFile(temp, JSON.stringify({ screen: body.screen, ...(body.player ? { player: body.player } : {}), request: requestId }), 'utf8'); await rename(temp, target); } finally { await rm(temp, { force: true }); }
  return { ok: true, request: requestId };
}
function sameToken(given, expected) {
  const a = Buffer.from(String(given ?? '')), b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}
/**
 * Publishing is off unless --server ROOT or --screens PATH selects a screens folder. Then POST /publish writes
 * the validated screen to PATH/<id>.json; the plugin's auto-reload picks it up. Requests must come from this
 * page: same Origin and the per-run token embedded in index.html (other sites can read neither).
 */
/** {@code icons}: the plugin's item table (or null when it cannot be read): the plugin refuses a page naming an item it lacks. */
async function publish(request, screensDir, token, icons = null) {
  const origin = request.headers.origin, host = request.headers.host;
  if (!host || origin !== `http://${host}`) throw Object.assign(new Error('Cross-origin publish rejected.'), { status: 403 });
  if (!sameToken(request.headers['x-tideframe-token'], token)) throw Object.assign(new Error('Missing or wrong publish token.'), { status: 403 });
  if (!String(request.headers['content-type'] ?? '').startsWith('application/json')) throw Object.assign(new Error('Expected application/json.'), { status: 415 });
  const raw = await readBody(request);
  const { parseScreenForPublication, serializeScreen } = await import('./model.mjs');
  let screen;
  // Structural PNG checks here; the plugin decodes every image again when it loads the file.
  try { screen = await parseScreenForPublication(raw, async () => {}); } catch (error) { throw Object.assign(new Error(error.message), { status: 422 }); }
  const unknown = icons ? screen.elements.filter(e => typeof e.item === 'string' && !icons.has(e.item)) : [];
  if (unknown.length) throw Object.assign(new Error(`${unknown[0].id}: the game has no item "${unknown[0].item}" (the plugin would refuse the page). Pick one from the list.`), { status: 422 });
  await mkdir(screensDir, { recursive: true });
  const file = `${screen.id}.json`, target = path.join(screensDir, file), temp = path.join(screensDir, `.${screen.id}.${randomBytes(6).toString('hex')}.tmp`);
  try { await writeFile(temp, serializeScreen(screen), 'utf8'); await rename(temp, target); } finally { await rm(temp, { force: true }); }
  // The file's time in whole milliseconds: the plugin reports the same number once this file is live (editor-status.json).
  const stamp = Number((await stat(target, { bigint: true })).mtimeNs / 1000000n);
  return { ok: true, file, id: screen.id, stamp };
}
// The icon table: next to the editor in a server's copy, else the plugin's own in the repository.
const ICON_TABLES = [path.join(folder, 'item-icons.tsv'), path.join(folder, '..', '..', 'src', 'main', 'resources', 'tideframe', 'engine', 'item-icons.tsv')];
export function createServer({ screensDir = null, token = randomBytes(24).toString('hex'), clientJar = defaultClientJar(), iconTable = null } = {}) {
  // Item icons (element "item"): the table ships with the plugin; each texture is read from the player's own client
  // jar for the preview only (the pack never carries it). Both load once, on first use, and missing files mean none.
  let icons, jar;
  const iconMap = () => (icons ??= (async () => {
    for (const file of iconTable ? [iconTable] : ICON_TABLES) { try { return parseIconTable(await readFile(file, 'utf8')); } catch { /* try the next place */ } }
    return null;
  })());
  const clientFiles = () => (jar ??= readFile(clientJar).then(openJar).catch(() => null));
  return http.createServer(async (request, response) => {
    const headers = {
      'content-security-policy': `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src data: 'self'; connect-src ${screensDir ? "'self'" : "'none'"}; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'`,
      'x-content-type-options': 'nosniff', 'referrer-policy': 'no-referrer', 'cache-control': 'no-store'
    };
    // Only this machine's own names for this port: a web page whose domain is rebound to 127.0.0.1 (DNS rebinding)
    // sends its own Host, so it can neither read the publish token nor pass the origin checks below.
    const port = request.socket.localPort;
    if (!new Set([`127.0.0.1:${port}`, `localhost:${port}`, `[::1]:${port}`]).has(request.headers.host ?? '')) {
      response.writeHead(421, headers); response.end(); return;
    }
    const pathname = (request.url ?? '').split('?')[0];
    if (pathname === '/server-status' && screensDir && (request.method === 'GET' || request.method === 'HEAD')) {
      const body = JSON.stringify(await serverStatus(screensDir));
      response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(request.method === 'HEAD' ? undefined : body); return;
    }
    if (request.method === 'POST' && pathname === '/open-in-game' && screensDir) {
      try { const result = await openInGame(request, screensDir, token); response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify(result)); }
      catch (error) { response.writeHead(error.status ?? 500, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify({ ok: false, error: error.message })); }
      return;
    }
    if (request.method === 'POST' && pathname === '/upload-image' && screensDir) {
      try {
        const result = await upload(request, screensDir, token, new URL(request.url, 'http://localhost').searchParams);
        response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify(result));
      } catch (error) {
        response.writeHead(error.status ?? 500, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify({ ok: false, error: error.message }));
      }
      return;
    }
    if (request.method === 'POST' && pathname === '/upload-sound' && screensDir) {
      try {
        const result = await uploadSound(request, screensDir, token, new URL(request.url, 'http://localhost').searchParams);
        response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify(result));
      } catch (error) {
        response.writeHead(error.status ?? 500, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify({ ok: false, error: error.message }));
      }
      return;
    }
    if (request.method === 'POST' && pathname === '/publish' && screensDir) {
      try {
        const result = await publish(request, screensDir, token, await iconMap());
        response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify(result));
      } catch (error) {
        response.writeHead(error.status ?? 500, { ...headers, 'content-type': 'application/json' }); response.end(JSON.stringify({ ok: false, error: error.message }));
      }
      return;
    }
    if (request.method !== 'GET' && request.method !== 'HEAD') {
      response.writeHead(405, { ...headers, allow: 'GET, HEAD' }); response.end(); return;
    }
    if (pathname === '/item-icons.mjs') {
      const table = await iconMap();
      if (!table) { response.writeHead(404, headers); response.end(); return; }
      const listed = Object.fromEntries([...table].map(([item, icon]) => [item, { texture: icon.texture, rows: icon.rows, block: icon.block, tinted: icon.tinted }]));
      const body = Buffer.from(`export const ITEM_ICONS = Object.freeze(${JSON.stringify(listed)});\n`, 'utf8');
      response.writeHead(200, { ...headers, 'content-type': 'text/javascript; charset=utf-8', 'content-length': body.length }); response.end(request.method === 'HEAD' ? undefined : body); return;
    }
    const itemTexture = /^\/item-texture\/([a-z0-9_]{1,64})$/.exec(pathname);
    if (itemTexture) {
      const icon = (await iconMap())?.get(itemTexture[1]);
      let png = null;
      try { png = icon ? (await clientFiles())?.read(`assets/minecraft/textures/${icon.texture}.png`) ?? null : null; } catch { png = null; }
      if (!png) { response.writeHead(404, headers); response.end(); return; }
      response.writeHead(200, { ...headers, 'content-type': 'image/png', 'content-length': png.length }); response.end(request.method === 'HEAD' ? undefined : png); return;
    }
    if (screensDir && pathname === '/server-sounds') {
      const body = JSON.stringify({ sounds: await serverSounds(path.join(screensDir, '..', 'sounds')).catch(() => []) });
      response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(request.method === 'HEAD' ? undefined : body); return;
    }
    const sound = screensDir && /^\/server-sounds\/([a-z0-9_]{1,32})\.ogg$/.exec(pathname);
    if (sound) {
      const found = await inside(path.join(screensDir, '..', 'sounds'), `${sound[1]}.ogg`).catch(() => null);
      if (!found?.info.isFile() || found.info.size > MAX_SOUND_BYTES) { response.writeHead(404, headers); response.end(); return; }
      try { const body = await readFile(found.real); response.writeHead(200, { ...headers, 'content-type': 'audio/ogg', 'content-length': body.length }); response.end(request.method === 'HEAD' ? undefined : body); }
      catch { response.writeHead(404, headers); response.end(); }
      return;
    }
    if (screensDir && pathname.startsWith('/server-images/')) {
      const found = await serverImage(pathname, path.join(screensDir, '..', 'images')).catch(() => null);
      if (!found) { response.writeHead(404, headers); response.end(); return; }
      if (found.json) { const body = JSON.stringify(found.json); response.writeHead(200, { ...headers, 'content-type': 'application/json' }); response.end(request.method === 'HEAD' ? undefined : body); return; }
      try { const body = await readFile(found.file); response.writeHead(200, { ...headers, 'content-type': found.type, 'content-length': body.length }); response.end(request.method === 'HEAD' ? undefined : body); }
      catch { response.writeHead(404, headers); response.end(); }
      return;
    }
    // Match the raw pathname: no decoding, filesystem joining or normalization of user input.
    const selected = files.get(pathname);
    if (!selected) { response.writeHead(404, headers); response.end(); return; }
    try {
      let body = await readFile(path.join(folder, selected[0]));
      // The page learns the publish token from a meta tag; cross-origin pages cannot read it.
      if (screensDir && selected[0] === 'index.html')
        body = Buffer.from(body.toString('utf8').replace('</head>', `<meta name="tideframe-publish" content="${token}">\n</head>`), 'utf8');
      response.writeHead(200, { ...headers, 'content-type': selected[1], 'content-length': body.length });
      response.end(request.method === 'HEAD' ? undefined : body);
    } catch { response.writeHead(500, headers); response.end(); }
  });
}
export function resolveScreensDir(args = process.argv.slice(2), env = process.env) {
  const directory = flag => {
    const index = args.indexOf(flag);
    if (index < 0) return null;
    const value = args[index + 1];
    if (!value || value.startsWith('--')) throw new Error('Directory option requires a path.');
    return path.resolve(value);
  };
  const screens = directory('--screens'), server = directory('--server');
  return screens ?? (server ? path.join(server, 'plugins', 'BigCasares', 'tideframe', 'engine', 'screens')
    : env.TIDEFRAME_SCREENS ? path.resolve(env.TIDEFRAME_SCREENS) : null);
}
if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  const screensDir = resolveScreensDir();
  const server = createServer({ screensDir });
  server.on('error', error => { console.error(`Editor server failed: ${error.message}`); process.exitCode = 1; });
  server.listen(4176, '127.0.0.1', () => {
    console.log('Tideframe editor: http://127.0.0.1:4176 (loopback only)');
    console.log(screensDir ? `Publish writes to: ${screensDir}` : 'Publish is off. Start with --server <server path> or --screens <screens path> to enable it.');
  });
}
