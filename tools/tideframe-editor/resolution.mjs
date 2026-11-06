// Where a menu lands on a player's screen (idea 20): the same rules as the game and the plugin. Window size and GUI
// scale give the screen in GUI pixels (vanilla Window: the largest automatic scale keeping 320 x 240, sizes rounded up);
// a fixed menu size of q quarters draws the 256 x 144 canvas at 64q x 36q GUI pixels, centred sideways and placed
// vertically by the pack's frame (HudAssets.canvasTopGuiQuarters); auto-fit (display.auto-fit, 26.2) draws the 2x
// canvas and scales it about the screen centre by min(width / 464, height / 252) (FitShader.scale): the safe area of the
// 2x frame reaches 232 GUI pixels to each side and 124 up / 126 down from the centre (the frame sits 4 pixels low).

export const WINDOW_PRESETS = Object.freeze([
  { id: '1920x1080', width: 1920, height: 1080, label: '1920 × 1080 (16:9, Full HD)' },
  { id: '2560x1440', width: 2560, height: 1440, label: '2560 × 1440 (16:9, 2K)' },
  { id: '3840x2160', width: 3840, height: 2160, label: '3840 × 2160 (16:9, 4K)' },
  { id: '1366x768', width: 1366, height: 768, label: '1366 × 768 (notebook)' },
  { id: '1280x720', width: 1280, height: 720, label: '1280 × 720 (16:9, HD)' },
  { id: '2560x1080', width: 2560, height: 1080, label: '2560 × 1080 (21:9)' },
  { id: '3440x1440', width: 3440, height: 1440, label: '3440 × 1440 (21:9)' },
  { id: '1680x1050', width: 1680, height: 1050, label: '1680 × 1050 (16:10)' },
  { id: '1280x1024', width: 1280, height: 1024, label: '1280 × 1024 (5:4)' },
  { id: '1024x768', width: 1024, height: 768, label: '1024 × 768 (4:3)' },
  { id: '854x480', width: 854, height: 480, label: '854 × 480 (ventana chica)' }
].map(Object.freeze));
/** Menu sizes the default pack bakes (config.yml display sizes), in quarters. */
export const MENU_SIZES = Object.freeze([4, 7, 8, 11]);
/** The canvas rectangle every menu keeps its text and buttons in (logical pixels, ViewportFit.SAFE_*). */
export const SAFE = Object.freeze({ left: 12, right: 244, top: 8, bottom: 133 });
const SAFE_WIDTH = 464, SAFE_HEIGHT = 252, REFERENCE_QUARTERS = 8;

/** Vanilla automatic GUI scale: the largest one keeping at least 320 x 240 GUI pixels. */
export function autoGuiScale(width, height) {
  let maximum = 1;
  while (maximum < width && maximum < height && Math.floor(width / (maximum + 1)) >= 320 && Math.floor(height / (maximum + 1)) >= 240) maximum++;
  return maximum;
}
/** The GUI scale the game really uses: Auto (0) is the largest; a chosen one never goes above it. */
export function effectiveGuiScale(width, height, guiScale = 0) {
  const maximum = autoGuiScale(width, height);
  return guiScale === 0 ? maximum : Math.min(guiScale, maximum);
}
/** HudAssets.frameAscentQuarters / canvasTopGuiQuarters: the canvas top edge, in GUI pixels from the screen centre. */
export function canvasTopGui(quarters) {
  const ascent = quarters === 4 ? 15 : Math.min(Math.round(18 * quarters / 4 - 3), 4 * quarters);
  return -4 * (3 + ascent);
}

/**
 * The screen and the menu canvas on it, in GUI pixels, plus what falls off: {@code cut} in logical canvas pixels per
 * side, and whether the whole safe area is visible. {@code mode}: 'fixed' ({@code quarters}) or 'autofit'.
 */
export function simulate({ width, height, guiScale = 0, mode = 'fixed', quarters = 8 }) {
  if (!Number.isInteger(width) || !Number.isInteger(height) || width < 320 || height < 240) throw Error('Window size must be at least 320 x 240.');
  const gui = effectiveGuiScale(width, height, guiScale), screenWidth = Math.ceil(width / gui), screenHeight = Math.ceil(height / gui);
  const base = mode === 'autofit' ? REFERENCE_QUARTERS : quarters;
  let canvas = { x: (screenWidth - 64 * base) / 2, y: screenHeight / 2 + canvasTopGui(base), width: 64 * base, height: 36 * base };
  let factor = 1;
  if (mode === 'autofit') {
    factor = Math.min(screenWidth / SAFE_WIDTH, screenHeight / SAFE_HEIGHT);
    const cx = screenWidth / 2, cy = screenHeight / 2;
    canvas = { x: cx + (canvas.x - cx) * factor, y: cy + (canvas.y - cy) * factor, width: canvas.width * factor, height: canvas.height * factor };
  }
  const unit = canvas.width / 256, round = value => Math.round(value * 10) / 10;
  const cut = {
    left: round(Math.max(0, -canvas.x) / unit), right: round(Math.max(0, canvas.x + canvas.width - screenWidth) / unit),
    top: round(Math.max(0, -canvas.y) / unit), bottom: round(Math.max(0, canvas.y + canvas.height - screenHeight) / unit)
  };
  const safeVisible = canvas.x + SAFE.left * unit >= -1e-9 && canvas.x + SAFE.right * unit <= screenWidth + 1e-9
    && canvas.y + SAFE.top * unit >= -1e-9 && canvas.y + SAFE.bottom * unit <= screenHeight + 1e-9;
  return { gui, screen: { width: screenWidth, height: screenHeight }, canvas, unit, factor, cut, safeVisible };
}
