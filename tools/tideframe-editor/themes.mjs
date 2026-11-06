// Original one-click colour themes. Each recolours panels, buttons and text by role; images and live bars keep their meaning.
import { upgradeGradient, barSource, validateScreenBudget } from './model.mjs';

export const THEMES = Object.freeze([
  { id: 'tide', name: 'Tide (teal and navy)', panel: ['#10232c', '#0a1620'], edge: '#23404b', button: ['#275b64', '#173b48'], buttonEdge: '#61dfd1', buttonText: '#e9f4f0', hover: '#3a8a8f', text: '#e9f4f0', title: ['#f2cb8a', '#e0903a'], outline: '#1a1206' },
  { id: 'ember', name: 'Ember (fire)', panel: ['#2a1210', '#140808'], edge: '#6a2e1a', button: ['#a8341c', '#5a160c'], buttonEdge: '#ffb36b', buttonText: '#fff0e0', hover: '#d0502c', text: '#fff0e0', title: ['#ffe07a', '#ff6a1a'], outline: '#2a0800' },
  { id: 'forest', name: 'Forest', panel: ['#12281c', '#08140e'], edge: '#2f5a3a', button: ['#3a7a44', '#1a4024'], buttonEdge: '#a8e6a0', buttonText: '#eef8e8', hover: '#4a9a52', text: '#eef8e8', title: ['#e2f6a8', '#7ac04a'], outline: '#0a1a06' },
  { id: 'royal', name: 'Royal (purple and gold)', panel: ['#1e1636', '#0e0a1e'], edge: '#4a3a8a', button: ['#5a46b4', '#2a1f6a'], buttonEdge: '#e8d48a', buttonText: '#f6f0ff', hover: '#7a64d8', text: '#f0ecff', title: ['#ffe9a0', '#d49a2a'], outline: '#1a1004' },
  { id: 'mono', name: 'Mono (black and white)', panel: ['#1c1c1c', '#080808'], edge: '#ffffff', button: ['#ffffff', '#cfcfcf'], buttonEdge: '#000000', buttonText: '#111111', hover: '#ffffff', text: '#ffffff', title: ['#ffffff', '#bdbdbd'], outline: '#000000' },
  { id: 'candy', name: 'Candy (pink)', panel: ['#2c1430', '#160a1a'], edge: '#ff8ad8', button: ['#ff6fb5', '#c23f86'], buttonEdge: '#ffe0f2', buttonText: '#fff4fb', hover: '#ff9fd0', text: '#fff4fb', title: ['#fff27a', '#ff6fb5'], outline: '#2a0418' }
].map(Object.freeze));

/** Recolour {@code ids} (or every element when empty). Returns a new schema-8 screen. */
export function applyTheme(value, themeId, ids = []) {
  const theme = THEMES.find(t => t.id === themeId);
  if (!theme) throw Error('Unknown theme.');
  const next = upgradeGradient(value), wanted = new Set(ids);
  next.elements = next.elements.map(e => {
    if (wanted.size && !wanted.has(e.id)) return e;
    const style = { ...e.style };
    if (barSource(e)) {
      // A bar's fill colour carries meaning (red health, green XP): only its track follows the theme.
      style.fillColor = theme.panel[1]; style.borderColor = theme.edge; style.gradientColor = null;
    } else if (e.kind === 'button') {
      Object.assign(style, { fillColor: theme.button[0], gradientColor: theme.button[1], gradientDirection: 'vertical', borderColor: theme.buttonEdge, textColor: theme.buttonText, hoverColor: theme.hover });
    } else if (e.kind === 'decoration') {
      if (e.image === null) Object.assign(style, { fillColor: theme.panel[0], gradientColor: theme.panel[1], gradientDirection: 'vertical', borderColor: theme.edge });
    } else if (style.textScale >= 2) {
      // Titles: gradient lettering with a dark outline.
      Object.assign(style, { textColor: theme.title[0], textGradient: theme.title[1], textOutline: theme.outline });
    } else {
      style.textColor = theme.text;
      if (e.background && e.image === null) Object.assign(style, { fillColor: theme.panel[0], gradientColor: theme.panel[1], borderColor: theme.edge });
    }
    return { ...e, style };
  });
  validateScreenBudget(next);
  return next;
}
