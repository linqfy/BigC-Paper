// The "Probar" view: draws a play session (play.mjs) with the editor's own element painter, and turns the mouse and
// keyboard into the game's hover, clicks and keys. The editor document is never changed while playing.
import { startPlay, page, shown, listRows, hover, click, key, resolve, timers, tick, damage, changeWorld, gifTimeline, variables, setVariableFromPanel, setSample, setPlaceholders, SAMPLES } from './play.mjs';
import { caseText, orderedElements } from './model.mjs';
import { runs as textRuns, hasTags } from './richtext.mjs';
import { bytesToBase64 } from './images.mjs';

const CURVES = { linear: 'linear', 'ease-in': 'cubic-bezier(.32,0,.67,0)', 'ease-out': 'cubic-bezier(.33,1,.68,1)', 'ease-in-out': 'cubic-bezier(.65,0,.35,1)' };
const KEYS = { w: 'W', a: 'A', s: 'S', d: 'D', ' ': 'SPACE', q: 'Q', f: 'F', l: 'L', 1: '1', 2: '2', 3: '3', 4: '4', 5: '5', 6: '6', 7: '7', 8: '8', 9: '9' };
const field = (object, name) => object && typeof object === 'object' ? object[Object.keys(object).find(k => k.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase() === name)] : undefined;

/**
 * Keyframes of the plugin's Motion as Web Animations keyframes (steps carry earlier values forward; y grows downwards):
 * {@code move} adds to the element's own position and rotation; {@code fade} multiplies its opacity.
 */
export function motionKeyframes(motion, unit, baseOpacity = 1) {
  const steps = field(motion, 'steps') ?? [], total = Math.max(1, steps.at(-1)?.at ?? 1), easing = CURVES[field(motion, 'easing') ?? 'ease-in-out'] ?? 'linear';
  const pose = { x: 0, y: 0, scale: 1, opacity: 1, rotation: 0 }, move = [], fade = [];
  for (const step of steps) {
    for (const name of Object.keys(pose)) if (Number.isFinite(step[name])) pose[name] = step[name];
    const offset = Math.min(1, (step.at ?? 0) / total);
    move.push({ offset, easing, translate: `${pose.x * unit}px ${pose.y * unit}px`, scale: String(pose.scale), rotate: `${pose.rotation}deg` });
    fade.push({ offset, easing, opacity: String(pose.opacity * baseOpacity) });
  }
  return { move, fade };
}
/** Both halves of a Motion on {@code button}; the returned handle controls them together. */
function play(button, motion, unit, baseOpacity, options) {
  const { move, fade } = motionKeyframes(motion, unit, baseOpacity);
  const parts = [button.animate(move, { ...options, composite: 'add' }), button.animate(fade, options)];
  return {
    pause: () => parts.forEach(a => a.pause()), play: () => parts.forEach(a => a.play()), cancel: () => parts.forEach(a => a.cancel()),
    set currentTime(value) { parts.forEach(a => { a.currentTime = value; }); }, set playbackRate(value) { parts.forEach(a => { a.playbackRate = value; }); }
  };
}
const duration = motion => Math.max(1, (field(motion, 'steps') ?? []).at(-1)?.at ?? 1);

export function mountPlay({ viewport, stage, controls, getProject, getPageId, createNode, paintNode, report, server = false, onExit = () => {} }) {
  const document = stage.ownerDocument, window = document.defaultView;
  const playStage = document.createElement('div'); playStage.className = 'stage play-stage'; playStage.setAttribute('role', 'application'); playStage.setAttribute('aria-label', 'Prueba del menú: usalo como en el juego');
  const layer = document.createElement('div'); layer.className = 'play-layer'; playStage.append(layer);
  const cursor = document.createElement('span'); cursor.className = 'game-cursor-preview'; cursor.setAttribute('aria-hidden', 'true'); playStage.append(cursor);
  const ended = document.createElement('div'); ended.className = 'play-ended'; ended.hidden = true; playStage.append(ended);
  let session = null, nodes = new Map(), shownTransition = null, logged = 0, frames = new Map(), framesWarned = new Set(), pointer = { x: 0, y: 0 };
  // The page's timers run for real while it stays open (ScreenRuntime starts them on open, one period first).
  let timerPage = null, timerHandles = [];
  function stopTimers() { timerHandles.forEach(handle => window.clearInterval(handle)); timerHandles = []; timerPage = null; }
  function syncTimers() {
    const pageKey = session && !session.closed ? `${session.pageId}#${session.transition?.at ?? 0}` : null;
    if (pageKey === timerPage) return;
    stopTimers(); timerPage = pageKey;
    if (!pageKey) return;
    timers(session).forEach((timer, index) => timerHandles.push(window.setInterval(() => { if (!session) return; tick(session, index, window.performance.now()); render(); }, timer.everyMs)));
  }
  const scale = () => playStage.getBoundingClientRect().width / 256;

  function state(text) { controls.state.textContent = text; }
  // The variables panel (idea 21): the run's variables, the simulated player and PlaceholderAPI values, all editable.
  const SAMPLE_TEXT = { player: 'Nombre', health: 'Vida', max_health: 'Vida máxima', food: 'Hambre', level: 'Nivel', xp: 'Experiencia', balance: 'Saldo',
    online: 'Conectados', max_players: 'Máximo de jugadores', ping: 'Ping', world: 'Mundo', x: 'X', y: 'Y', z: 'Z', time: 'Hora' };
  let shownNames = '';
  function panelField(labelText, value, onCommit, describe) {
    const label = document.createElement('label'); label.className = 'play-field';
    const caption = document.createElement('span'); caption.textContent = labelText;
    const input = document.createElement('input'); input.value = value; input.spellcheck = false; input.maxLength = 256; input.dataset.commit = '';
    if (describe) input.setAttribute('aria-label', describe);
    const commit = () => { if (!session) return; try { onCommit(input.value); render(); } catch (error) { report(error.message, true); } };
    input.addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); commit(); input.select(); } });
    input.addEventListener('change', commit);
    label.append(caption, input); return { label, input };
  }
  function renderVariables() {
    if (!controls.vars || !session) return;
    const list = variables(session), names = list.map(v => `${v.global ? '*' : ''}${v.name}`).join('\u0000');
    if (names !== shownNames) {
      shownNames = names;
      controls.vars.replaceChildren(...(list.length ? list.map(v => {
        const { label, input } = panelField(`${v.global ? '*' : ''}${v.name}`, v.value, value => setVariableFromPanel(session, `${v.global ? '*' : ''}${v.name}`, value), `Valor de ${v.name}`);
        input.dataset.variable = v.name; label.dataset.global = String(v.global); return label;
      }) : [Object.assign(document.createElement('p'), { className: 'hint', textContent: 'Esta página no tiene variables todavía. Agregá una abajo, o declaralas en "Variables y funciones".' })]));
    } else for (const input of controls.vars.querySelectorAll('input[data-variable]')) {
      const current = list.find(v => v.name === input.dataset.variable);
      if (current && document.activeElement !== input) input.value = current.value;
    }
  }
  function buildSamples() {
    if (!controls.samples) return;
    controls.samples.replaceChildren(...Object.keys(SAMPLES).map(name => {
      const value = name === 'balance' ? String(session.balance) : session.samples[name];
      const { label, input } = panelField(SAMPLE_TEXT[name] ?? name, value, text => setSample(session, name, text), `${SAMPLE_TEXT[name] ?? name} del jugador simulado ({${name}})`);
      input.dataset.sample = name; label.title = `{${name}}`; return label;
    }));
  }
  function refreshSamples() {
    for (const input of controls.samples?.querySelectorAll('input[data-sample]') ?? []) {
      if (document.activeElement === input) continue;
      input.value = input.dataset.sample === 'balance' ? String(session.balance) : session.samples[input.dataset.sample];
    }
  }
  function writeLog() {
    for (const entry of session.log.slice(logged)) {
      const item = document.createElement('li'); item.dataset.kind = entry.kind; item.textContent = entry.text; controls.log.append(item);
      // A custom sound of the server plays here too when the editor is connected (idea 30).
      const sound = server && /^Sonido: tideframe:([a-z0-9_]{1,32})(?:[\s,]+([0-9.]+))?/.exec(entry.text);
      if (sound) { const audio = new window.Audio(`/server-sounds/${sound[1]}.ogg`); audio.volume = Math.max(0, Math.min(1, Number(sound[2] ?? 1) || 1)); void audio.play().catch(() => {}); }
    }
    session.log.length = 0; logged = 0; while (controls.log.children.length > 200) controls.log.firstElementChild.remove();
    controls.log.lastElementChild?.scrollIntoView({ block: 'nearest' });
  }
  /** The drawn elements: list templates become their rows, like the plugin's ListRows. */
  function drawn(screen) {
    const out = [];
    for (const { element } of orderedElements(screen)) {
      const rows = listRows(session, element);
      if (!rows) { out.push({ key: element.id, element, id: element.id, row: null }); continue; }
      if (!rows.items.length && !framesWarned.has(`list:${element.id}`)) {
        framesWarned.add(`list:${element.id}`);
        session.log.push({ kind: 'game', text: `La lista ${element.id} no tiene datos: poné la variable ${field(field(element.logic, 'list'), 'source')} (por ejemplo Survival,Skyblock) en "Variables y funciones" de la página, o la llena otro plugin en el juego.` });
      }
      rows.items.forEach((row, i) => out.push({ key: `${element.id}#${i}`, id: element.id, row, element: { ...element, y: element.y - i * rows.spacing / 72, text: element.text.replaceAll('{item}', row.item).replaceAll('{index}', String(row.index)) } }));
    }
    return out;
  }
  function render() {
    if (!session) return;
    const screen = page(session), unit = scale(), keep = new Set(), presentation = screen?.presentation ?? {};
    state(session.closed ? 'Menú cerrado' : `Probando: ${screen.id} / ${screen.title}${presentation.mode === 'overlay' ? ' (HUD pasivo: sin cursor ni clics)' : ''}`);
    cursor.dataset.kind = presentation.cursor ?? 'arrow'; cursor.hidden = presentation.mode === 'overlay' || session.closed;
    // First the transition: a closing exit keeps the page drawn until its slide or fade ends.
    playTransition();
    if (screen && !(session.closed && !shownTransition?.running)) for (const entry of drawn(screen)) {
      const key = `${screen.id}/${entry.key}`; keep.add(key);
      let node = nodes.get(key);
      if (!node || node.image !== entry.element.image || node.hoverImage !== entry.element.hoverImage) { node?.button.remove(); node = { ...createNode(entry.element), play: null }; nodes.set(key, node); layer.append(node.button); }
      // The game resolves live text every few ticks: variables of this run, then the player samples. Words in their own
      // colour (schema 9) are filled in run by run, like LivePlaceholders; the painter shrinks a line that does not fit.
      const element = entry.element, textCase = element.style.textCase ?? 'upper';
      const runs = screen.version < 9 || !hasTags(element.text) ? [{ text: caseText(resolve(session, element.text), textCase), color: null }]
        : textRuns(element.text).map(run => ({ text: caseText(resolve(session, run.text), textCase), color: run.color }));
      paintNode(node, element, unit, { runs, version: screen.version });
      const { button } = node;
      button.dataset.playId = entry.id; button.dataset.row = entry.row ? String(entry.row.index) : ''; button.tabIndex = -1;
      button.setAttribute('aria-label', `${element.id}: ${element.text}`);
      button.hidden = !shown(session, element);
      const dx = session.view.offsets.get(entry.id) ?? 0; button.style.translate = dx ? `${dx * unit}px 0` : '';
      const tint = session.view.tints.get(entry.id); if (tint) { button.style.setProperty('--element-fill', tint); button.style.setProperty('--element-border', tint); }
      button.dataset.hovered = String(session.view.hover === entry.id);
      animate(node, element, entry.id, unit);
      showFrames(node, element, key);
    }
    for (const [key, node] of nodes) if (!keep.has(key)) { node.button.remove(); stopFrames(key); nodes.delete(key); }
    ended.hidden = !(session.closed && !shownTransition?.running);
    ended.textContent = 'El menú se cerró. Tocá Reiniciar para probar de nuevo.';
    syncTimers();
    renderVariables(); refreshSamples();
    writeLog();
  }
  /** Keyframes: open and loop start with the page, hover follows the cursor, action waits for an "animation" action. */
  function animate(node, element, id, unit) {
    const motion = field(element.logic, 'keyframes'); if (!motion) return;
    const trigger = field(motion, 'trigger') ?? 'open', started = session.view.plays.get(id);
    if (trigger === 'hover') {
      const on = session.view.hover === id;
      if (!node.play) { node.play = play(node.button, motion, unit, element.style.opacity ?? 1, { duration: duration(motion), fill: 'both' }); node.play.pause(); node.play.currentTime = 0; }
      if (node.hoverOn !== on) { node.hoverOn = on; node.play.playbackRate = on ? 1 : -1; node.play.play(); }
      return;
    }
    if (!started || node.started === started) return;
    node.started = started; node.play?.cancel();
    node.play = play(node.button, motion, unit, element.style.opacity ?? 1, { duration: duration(motion), fill: 'both', iterations: trigger === 'loop' ? Infinity : 1 });
  }
/** PNG folder or GIF of plugins/BigCasares/tideframe/engine/images, read from the selected server. */
  async function showFrames(node, element, key) {
    const spec = field(element.logic, 'frames'); if (!spec || frames.has(key)) return;
    const folder = String(field(spec, 'folder'));
    // Each load owns its slot: leaving Probar, restarting or redrawing the page aborts it, and a late answer is ignored.
    const slot = { timer: null, abort: new window.AbortController() }, current = () => session && frames.get(key) === slot;
    frames.set(key, slot);
    if (!server) {
      if (!framesWarned.has(folder)) { framesWarned.add(folder); session.log.push({ kind: 'game', text: `Animación "${folder}": sus imágenes están en el servidor. Abrí el editor con ABRIR_EDITOR.cmd para verla acá; en el juego se reproduce igual.` }); writeLog(); }
      return;
    }
    try {
    const response = await window.fetch(`/server-images/${folder}`, { signal: slot.abort.signal }); if (!response.ok) throw Error(response.status === 404 ? 'no existe en plugins/BigCasares/tideframe/engine/images' : `HTTP ${response.status}`);
      const found = await response.json(); if (!current()) return;
      const image = document.createElement('img'); image.className = 'element-image play-frames'; image.alt = ''; image.draggable = false;
      node.button.querySelector('.element-image')?.remove(); node.button.prepend(image);
      const requested = Number(field(spec, 'interval-ms')) || 0, loop = field(spec, 'loop') !== false;
      let shots = null, interval = requested || 50;
      if (found.type === 'gif') {
        // Decoded like the plugin (one picture per tick-sized step) so pause, resume and frame match; else the browser plays it.
        const decoded = await decodeGif(found.url, requested, slot.abort.signal).catch(() => null); if (!current()) return;
        if (decoded) { shots = decoded.urls; interval = decoded.interval; }
        else { image.src = found.url; if (!framesWarned.has(`gif:${folder}`)) { framesWarned.add(`gif:${folder}`); session.log.push({ kind: 'game', text: `GIF "${folder}": este navegador no deja pausarlo ni saltar de cuadro en Probar; en el juego funciona.` }); writeLog(); } return; }
      } else shots = found.frames;
      let i = 0, version = -1;
      image.src = shots[0];
      // Pause holds the frame; frame jumps (paused stays paused); a one-shot animation holds its last frame.
      slot.timer = window.setInterval(() => {
        const control = session?.view.playback.get(element.id);
        if (control && control.version !== version) { version = control.version; if (control.seek) { i = Math.min(shots.length, control.seek) - 1; image.src = shots[i]; return; } }
        if (control?.paused) return;
        if (i + 1 >= shots.length) { if (!loop) return; i = -1; }
        image.src = shots[++i];
      }, interval);
    } catch (error) { if (current()) { session.log.push({ kind: 'error', text: `Animación "${folder}": ${error.message}.` }); writeLog(); } }
  }
  /** A GIF as the plugin bakes it (GifFrames): composited frames sampled on its timeline, as data URLs (the page allows no blob: images); null without ImageDecoder. */
  async function decodeGif(url, requested, signal) {
    if (!window.ImageDecoder) return null;
    const bytes = await (await window.fetch(url, { signal })).arrayBuffer();
    const decoder = new window.ImageDecoder({ data: bytes, type: 'image/gif' });
    await decoder.tracks.ready;
    const count = decoder.tracks.selectedTrack.frameCount, pictures = [], delays = [];
    for (let f = 0; f < count && f < 512; f++) {
      const { image: frame } = await decoder.decode({ frameIndex: f });
      delays.push(Math.round((frame.duration ?? 0) / 1000));
      const canvas = new window.OffscreenCanvas(frame.displayWidth, frame.displayHeight); canvas.getContext('2d').drawImage(frame, 0, 0); frame.close();
      pictures.push(canvas);
    }
    decoder.close();
    const timeline = gifTimeline(delays, requested), cache = new Map();
    const urls = [];
    for (const source of timeline.sources) {
      if (!cache.has(source)) cache.set(source, `data:image/png;base64,${bytesToBase64(new Uint8Array(await (await pictures[source].convertToBlob({ type: 'image/png' })).arrayBuffer()))}`);
      urls.push(cache.get(source));
    }
    return { urls, interval: timeline.interval };
  }
  function stopFrames(key) { const slot = frames.get(key); if (slot?.timer) window.clearInterval(slot.timer); slot?.abort?.abort(); frames.delete(key); }
  /** Page entrance (fade or sideways slide) and closing exit, as the plugin plays them. */
  function playTransition() {
    const t = session.transition; if (!t || shownTransition?.source === t) return;
    shownTransition?.animation?.cancel();
    const unit = scale(), side = t.from === 'right' ? 1 : -1, ms = Math.max(0, t.duration), curve = CURVES[t.easing] ?? 'linear';
    let frames = null;
    if (t.effect === 'fade') frames = t.phase === 'open' ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 1 }, { opacity: 0 }];
    else if (t.effect === 'slide') frames = t.phase === 'open' ? [{ translate: `${side * t.distance * unit}px 0` }, { translate: '0 0' }] : [{ translate: '0 0' }, { translate: `${side * t.distance * unit}px 0` }];
    const record = { source: t, running: Boolean(frames && ms > 0), animation: null };
    shownTransition = record;
    if (record.running) { record.animation = layer.animate(frames, { duration: ms, easing: curve, fill: t.phase === 'close' ? 'forwards' : 'none' }); record.animation.finished.then(() => { record.running = false; if (session) render(); }, () => {}); }
    else if (t.phase === 'open') layer.getAnimations().forEach(a => a.cancel());
  }
  // A passive HUD has no cursor or buttons in the game, so nothing under the mouse counts.
  function target(event) {
    if (page(session)?.presentation?.mode === 'overlay') return null;
    const node = event.target.closest?.('[data-play-id]'); return node && node.dataset.kind === 'button' && !node.hidden ? node : null;
  }
  function move(event) {
    if (!session) return;
    const bounds = playStage.getBoundingClientRect();
    pointer = { x: (event.clientX - bounds.left) / bounds.width * 100, y: (event.clientY - bounds.top) / bounds.height * 100 };
    cursor.style.left = `${pointer.x}%`; cursor.style.top = `${pointer.y}%`;
    const node = target(event), id = node?.dataset.playId ?? null;
    if (id !== session.view.hover) { hover(session, id, window.performance.now()); render(); }
  }
  function press(event, right) {
    if (!session || event.button > 2) return;
    event.preventDefault(); const node = target(event); if (!node) return;
    const rows = node.dataset.row ? listRows(session, page(session).elements.find(e => e.id === node.dataset.playId)) : null;
    const row = rows?.items.find(r => String(r.index) === node.dataset.row) ?? null;
    click(session, node.dataset.playId, { right, row, now: window.performance.now() }); render();
  }
  function keys(event) {
    if (!session || event.target.closest?.('input,textarea,select')) return;
    if (event.key === 'Shift') { event.preventDefault(); key(session, 'SNEAK'); render(); return; }
    const name = KEYS[event.key.toLowerCase()]; if (!name) return;
    event.preventDefault(); key(session, `${event.shiftKey ? 'SHIFT_' : ''}${name}`); render();
  }
  function wheel(event) {
    if (!session) return; const node = event.target.closest?.('[data-play-id]'); if (!node?.dataset.row) return;
    event.preventDefault(); const id = node.dataset.playId; session.view.scroll.set(id, Math.max(0, (session.view.scroll.get(id) ?? 0) + Math.sign(event.deltaY))); render();
  }
  playStage.addEventListener('pointermove', move);
  playStage.addEventListener('pointerdown', event => press(event, event.button === 2));
  playStage.addEventListener('contextmenu', event => event.preventDefault());
  playStage.addEventListener('wheel', wheel, { passive: false });
  playStage.addEventListener('pointerleave', () => { if (session && session.view.hover) { hover(session, null); render(); } });

  function start() {
    stop(false);
    session = startPlay(getProject(), getPageId(), { permissions: controls.permissions.checked, now: window.performance.now(),
      ask: input => window.prompt(`${input.title || 'Texto'}\n${input.label || ''}`, input.value ?? ''),
      confirm: question => window.confirm(`${question.title}\n${question.message}\n\nAceptar: ${question.yes}\nCancelar: ${question.no}`) });
    if (controls.placeholders) setPlaceholders(session, controls.placeholders.value);
    shownNames = ''; buildSamples();
    if (controls.varsPanel) controls.varsPanel.hidden = false;
    nodes = new Map(); logged = 0; shownTransition = null; controls.log.replaceChildren(); layer.replaceChildren();
    stage.hidden = true; viewport.append(playStage); playStage.hidden = false;
    controls.bar.hidden = controls.logPanel.hidden = false; controls.editTools.hidden = controls.hint.hidden = true;
    controls.toggle.setAttribute('aria-pressed', 'true'); controls.toggle.textContent = '■ Dejar de probar';
    document.addEventListener('keydown', keys, true);
    render(); report('Probando la página: pasá el cursor, hacé clic y usá las teclas como en el juego. Nada de esto cambia el proyecto.');
  }
  function stop(notify = true) {
    if (!session) return;
    for (const key of [...frames.keys()]) stopFrames(key);
    stopTimers();
    layer.getAnimations().forEach(a => a.cancel()); session = null; nodes = new Map();
    playStage.remove(); stage.hidden = false;
    controls.bar.hidden = controls.logPanel.hidden = true; controls.editTools.hidden = controls.hint.hidden = false;
    if (controls.varsPanel) controls.varsPanel.hidden = true;
    controls.toggle.setAttribute('aria-pressed', 'false'); controls.toggle.textContent = '▶ Probar';
    document.removeEventListener('keydown', keys, true);
    if (notify) { report('Volviste al editor.'); onExit(); }
  }
  controls.restart.addEventListener('click', start);
  controls.addVariable?.addEventListener('submit', event => {
    event.preventDefault();
    if (!session) return;
    try { setVariableFromPanel(session, controls.newName.value, controls.newValue.value); controls.newName.value = controls.newValue.value = ''; render(); controls.newName.focus(); }
    catch (error) { report(error.message, true); }
  });
  controls.placeholders?.addEventListener('change', () => { if (session) { setPlaceholders(session, controls.placeholders.value); render(); } });
  controls.damage?.addEventListener('click', () => { if (session) { damage(session, 4, 'entity_attack'); render(); } });
  controls.world?.addEventListener('click', () => {
    if (!session) return;
    const world = window.prompt('¿A qué mundo pasa el jugador?', session.samples.world === 'world_nether' ? 'world' : 'world_nether');
    if (world && /^[A-Za-z0-9_.-]{1,64}$/.test(world.trim())) { changeWorld(session, world.trim()); render(); }
  });
  controls.exit.addEventListener('click', () => stop());
  controls.clear.addEventListener('click', () => { controls.log.replaceChildren(); });
  controls.permissions.addEventListener('change', () => { if (session) { session.permissions = controls.permissions.checked; report(controls.permissions.checked ? 'Probar: el jugador tiene los permisos.' : 'Probar: el jugador no tiene los permisos.'); } });
  window.addEventListener('resize', () => { if (session) render(); });
  return { start, stop, active: () => Boolean(session), render };
}
