// The Bedrock look of BigCasares' forms, taken from the same Tideframe screens Java players see (bigc_tienda,
// bigc_misiones): panel and row colours, title and text colours, and the progress-bar glyphs. Bedrock cannot draw the
// Java HUD, so the shop and missions stay Floodgate forms; this pack part only changes how those forms are drawn.
//
// Only forms whose title starts with the tag below get this look (BedrockFormStyle.TAG in Java). The pack adds a second
// factory to Bedrock's server form screen: its panel covers the stock form when the tag is there and stays hidden
// otherwise, so every other form (other plugins, Don Pollos) keeps Bedrock's own look, and the stock buttons underneath
// answer exactly like ours if the panel ever fails to draw.
//
//   node tools/tideframe-screens/build-bedrock-ui.mjs
// writes resourcepack/bedrock/{ui,font,textures/ui/bigc}.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const bedrock = join(root, 'resourcepack', 'bedrock');
export const TAG = '§b§g§c§r';
/** Private-use page of the progress glyphs: U+E500..U+E5FF, font/glyph_E5.png. */
export const GLYPH_PAGE = 'E5';

const screen = id => JSON.parse(readFileSync(join(root, 'src', 'main', 'resources', 'tideframe', 'screens', `${id}.json`), 'utf8'));
const element = (page, id) => {
  const found = page.elements.find(candidate => candidate.id === id);
  if (!found) throw new Error(`${page.id} has no element ${id}`);
  return found.style;
};
const shop = screen('bigc_tienda');
const missions = screen('bigc_misiones');
const look = {
  panelFill: element(shop, 'panel').fillColor, panelBorder: element(shop, 'panel').borderColor,
  rowFill: element(shop, 'fila').fillColor, rowBorder: element(shop, 'fila').borderColor,
  rowHover: element(shop, 'fila').hoverColor, rowText: element(shop, 'nombre').textColor,
  pressedFill: element(shop, 'volver').fillColor, pressedBorder: element(shop, 'volver').borderColor,
  title: element(shop, 'titulo').textColor, body: element(shop, 'ayuda').textColor,
  progress: element(missions, 'progreso').textColor, ready: element(missions, 'premio').textColor,
  claimed: element(missions, 'col_premio').textColor, empty: element(shop, 'fila').borderColor
};

// ----- PNG -----
const CRC = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
const crc32 = bytes => {
  let c = 0xffffffff;
  for (const b of bytes) c = CRC[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};
const chunk = (type, data) => {
  const out = Buffer.alloc(12 + data.length);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, 'ascii');
  data.copy(out, 8);
  out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length);
  return out;
};
/** pixels: width * height RGBA bytes. */
function png(width, height, pixels) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header.set([8, 6, 0, 0, 0], 8);
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let row = 0; row < height; row++) {
    raw[row * (width * 4 + 1)] = 0;
    pixels.copy(raw, row * (width * 4 + 1) + 1, row * width * 4, (row + 1) * width * 4);
  }
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
const rgb = hex => [1, 3, 5].map(at => parseInt(hex.slice(at, at + 2), 16));
const float = hex => rgb(hex).map(value => Math.round(value / 255 * 1000) / 1000);

/** A 16 x 16 nine-slice box: 1 px border, cut corners (the screens' rounded panels), opaque fill. */
function box(fill, border) {
  const size = 16, pixels = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const edge = x === 0 || y === 0 || x === size - 1 || y === size - 1;
      const corner = (x === 0 || x === size - 1) && (y === 0 || y === size - 1);
      if (corner) continue;
      pixels.set([...rgb(edge ? border : fill), 255], (y * size + x) * 4);
    }
  }
  return png(size, size, pixels);
}

/** Progress glyphs, one 16 x 16 cell each (a Bedrock glyph is half its cell high on screen): filled, empty, ready, claimed. */
function glyphs() {
  const size = 256, pixels = Buffer.alloc(size * size * 4);
  [look.progress, look.empty, look.ready, look.claimed].forEach((colour, cell) => {
    for (let y = 3; y < 13; y++) {
      for (let x = 0; x < 6; x++) pixels.set([...rgb(colour), 255], (y * size + cell * 16 + x) * 4);
    }
  });
  return png(size, size, pixels);
}

// ----- JSON UI -----
const text = colour => ({
  '$default_text_color': float(colour), '$hover_text_color': float(colour), '$pressed_text_color': float(colour)
});
const taggedTitle = [
  { binding_name: '#title_text' },
  { binding_type: 'view', source_property_name: `(#title_text - '${TAG}')`, target_property_name: '#text' }
];
const forms = {
  namespace: 'bigc_forms',
  long_form: {
    type: 'panel',
    size: [300, 216],
    layer: 20,
    visible: false,
    bindings: [
      { binding_name: '#title_text' },
      { binding_type: 'view', source_property_name: `(not ((#title_text - '${TAG}') = #title_text))`, target_property_name: '#visible' }
    ],
    controls: [
      { background: { type: 'image', texture: 'textures/ui/bigc/panel', size: ['100%', '100%'], layer: 0 } },
      { 'close@common.close_button': { layer: 6 } },
      {
        title: {
          type: 'label', anchor_from: 'top_middle', anchor_to: 'top_middle', offset: [0, 7], size: ['100% - 56px', 12],
          text: '#text', text_alignment: 'center', color: float(look.title), font_scale_factor: 1.25, shadow: true,
          layer: 3, bindings: taggedTitle
        }
      },
      {
        divider: {
          type: 'image', texture: 'textures/ui/bigc/divider', anchor_from: 'top_middle', anchor_to: 'top_middle',
          offset: [0, 25], size: ['100% - 16px', 1], layer: 2
        }
      },
      {
        'content@common.scrolling_panel': {
          anchor_from: 'top_left', anchor_to: 'top_left', offset: [6, 29], size: ['100% - 12px', '100% - 35px'], layer: 2,
          '$show_background': false,
          '$scrolling_content': 'bigc_forms.scrolling_content',
          '$scroll_size': [5, '100% - 4px'],
          '$scrolling_pane_size': ['100% - 4px', '100% - 2px'],
          '$scrolling_pane_offset': [2, 0],
          '$scroll_bar_right_padding_size': [0, 0]
        }
      }
    ]
  },
  scrolling_content: {
    type: 'stack_panel', size: ['100% - 4px', '100%c'], orientation: 'vertical', anchor_from: 'top_left', anchor_to: 'top_left',
    controls: [
      {
        body: {
          type: 'panel', size: ['100%', '100%c'],
          controls: [{
            label: {
              type: 'label', offset: [2, 2], size: ['100%', 'default'], anchor_from: 'top_left', anchor_to: 'top_left',
              color: float(look.body), text: '#form_text'
            }
          }]
        }
      },
      { padding: { type: 'panel', size: ['100%', 6] } },
      { buttons: { type: 'panel', size: ['100%', '100%c'], controls: [{ 'list@bigc_forms.buttons_panel': {} }] } }
    ]
  },
  // Bedrock's own button list (server_form.long_form_dynamic_buttons_panel), with BigCasares' rows.
  buttons_panel: {
    type: 'stack_panel', size: ['100% - 4px', '100%c'], offset: [2, 0], orientation: 'vertical',
    anchor_from: 'top_middle', anchor_to: 'top_middle',
    factory: {
      name: 'buttons',
      control_ids: {
        button: 'bigc_forms.button',
        label: '@server_form.dynamic_label',
        header: '@server_form.dynamic_header',
        divider: '@settings_common.option_group_section_divider'
      }
    },
    collection_name: 'form_buttons',
    bindings: [{ binding_name: '#form_button_contents', binding_name_override: '#collection_length' }]
  },
  // Bedrock's own row (server_form.dynamic_button): same bindings and click, BigCasares' textures and colours.
  button: {
    type: 'stack_panel', size: ['100%', 34], orientation: 'horizontal',
    controls: [
      {
        icon_panel: {
          type: 'panel', size: [34, '100%c'],
          bindings: [{
            binding_type: 'view', source_control_name: 'image', resolve_sibling_scope: true,
            source_property_name: "(not (#texture = ''))", target_property_name: '#visible'
          }],
          controls: [{
            image: {
              type: 'image', layer: 2, size: [32, 32], offset: [0, 0],
              bindings: [
                { binding_name: '#form_button_texture', binding_name_override: '#texture', binding_type: 'collection', binding_collection_name: 'form_buttons' },
                { binding_name: '#form_button_texture_file_system', binding_name_override: '#texture_file_system', binding_type: 'collection', binding_collection_name: 'form_buttons' },
                { binding_type: 'view', source_property_name: "(not ((#texture = '') or (#texture = 'loading')))", target_property_name: '#visible' }
              ]
            }
          }]
        }
      },
      {
        'form_button@common_buttons.light_text_button': {
          '$pressed_button_name': 'button.form_button_click',
          anchor_from: 'top_left', anchor_to: 'top_left', size: ['fill', 32],
          '$default_button_texture': 'textures/ui/bigc/row',
          '$hover_button_texture': 'textures/ui/bigc/row_hover',
          '$pressed_button_texture': 'textures/ui/bigc/row_pressed',
          ...text(look.rowText),
          '$button_text': '#form_button_text',
          '$button_text_binding_type': 'collection',
          '$button_text_grid_collection_name': 'form_buttons',
          '$button_text_max_size': ['100%', 20],
          bindings: [{ binding_type: 'collection_details', binding_collection_name: 'form_buttons' }]
        }
      }
    ]
  }
};
const serverForm = {
  namespace: 'server_form',
  main_screen_content: {
    modifications: [{
      array_name: 'controls',
      operation: 'insert_back',
      value: [{ bigc_form_factory: { type: 'factory', control_ids: { long_form: '@bigc_forms.long_form' } } }]
    }]
  }
};

const write = (relative, data) => {
  const target = join(bedrock, relative);
  mkdirSync(dirname(target), { recursive: true });
  writeFileSync(target, typeof data === 'string' ? data : data);
};
const json = value => JSON.stringify(value, null, 2) + '\n';
const nineSlice = json({ nineslice_size: 2, base_size: [16, 16] });

write('ui/_ui_defs.json', json({ ui_defs: ['ui/bigc_forms.json'] }));
write('ui/server_form.json', json(serverForm));
write('ui/bigc_forms.json', json(forms));
for (const [name, fill, border] of [
  ['panel', look.panelFill, look.panelBorder],
  ['row', look.rowFill, look.rowBorder],
  ['row_hover', look.rowHover, look.rowBorder],
  ['row_pressed', look.pressedFill, look.pressedBorder]
]) {
  write(`textures/ui/bigc/${name}.png`, box(fill, border));
  write(`textures/ui/bigc/${name}.json`, nineSlice);
}
write('textures/ui/bigc/divider.png', png(1, 1, Buffer.from([...rgb(look.panelBorder), 255])));
write(`font/glyph_${GLYPH_PAGE}.png`, glyphs());
console.log('Bedrock UI written to ' + bedrock);
