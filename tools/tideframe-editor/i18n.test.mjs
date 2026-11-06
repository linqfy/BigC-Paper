import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { es } from './i18n.mjs';

const MODULES = ['model.mjs', 'workspace.mjs', 'interaction.mjs', 'images.mjs', 'authoring.mjs', 'templates.mjs', 'presentation-tools.mjs', 'advanced-settings.mjs', 'serve.mjs', 'play.mjs', 'multi-edit.mjs', 'resolution.mjs', 'checker.mjs', 'page-templates.mjs'];

test('every error message written in the editor modules has a Spanish translation', () => {
  const missing = [];
  for (const file of MODULES) {
    const source = readFileSync(new URL(`./${file}`, import.meta.url), 'utf8');
    for (const match of source.matchAll(/Error\((`[^`]*`|'[^']*')/g)) {
      // Placeholders become a sample id, as at run time.
      // A ternary placeholder keeps its first text, as at run time.
      const message = match[1].slice(1, -1).replace(/\$\{[^}?]*\? '([^']*)' : '[^']*'\}/g, '$1').replace(/\$\{[^}]*\}/g, 'heading');
      if (!/[A-Za-z]{3}/.test(message.replaceAll('heading', ''))) continue;
      const shown = es(message);
      if (shown === message) missing.push(`${file}: ${message}`);
    }
  }
  assert.deepEqual(missing, []);
});

test('composed messages translate each known part and keep ids and values', () => {
  assert.equal(es('Draft not saved: Duplicate element id: start.'), 'Borrador sin guardar: ID de elemento repetido: start.');
  assert.equal(es('heading keyframes need 2 to 16 steps.'), 'heading: los fotogramas clave necesitan de 2 a 16 pasos.');
  assert.equal(es('Button action areas overlap: start and close. Move or resize the action area before export.'),
    'Las áreas de clic de start y close se superponen. Movelas o achicalas antes de exportar.');
  assert.equal(es('Move selection'), 'Mover selección');
  assert.equal(es('Edit screen logic'), 'Editar lógica de la página', 'the longer phrase wins over "Edit screen"');
});

test('Spanish text and unknown words pass through unchanged', () => {
  for (const text of ['Pantalla válida.', 'start', '42', '', 'Unknown thing nobody wrote']) assert.equal(es(text), text);
  assert.equal(es(null), '');
});
