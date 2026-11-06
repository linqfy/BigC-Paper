import { SETTINGS, ACTION_RECIPES, readSetting, writeSetting, removeFeature, appendAction } from './advanced-settings.mjs';
import { es, GROUP_TEXT, SETTING_TEXT, OPTION_TEXT, RECIPE_TEXT } from './i18n.mjs';

/**
 * Mount once, synchronize after selection/history changes. Does not own or mutate the workspace. {@code place} picks the
 * container of each group (the inspector tabs); by default every group goes to its scope's root.
 */
export function mountAdvancedSettings({ screenRoot, elementRoot, getState, apply, report, onDraftChange = () => {}, place = null }) {
  const document = screenRoot.ownerDocument, widgets = [], sections = [];
  const pendingDraftCount = () => widgets.filter(widget => widget.draft).length;
  const notifyDrafts = () => onDraftChange(pendingDraftCount());
  const node = (tag, text, className) => { const n = document.createElement(tag); if (text) n.textContent = text; if (className) n.className = className; return n; };
  function run(control, action, applyDraft = false) {
    const state = getState();
    if (!applyDraft && pendingDraftCount() > 0) { report('Primero aplicá o descartá los borradores. Tu texto se conserva.', true); sync(); return false; }
    if (!state.editable || state.scopeLocked) { report('Primero terminá o corregí el cambio actual. El borrador se conserva.', true); return false; }
    try { apply(action(state.screen, state.elementId)); control.removeAttribute('aria-invalid'); report('Ajuste guardado. Se puede deshacer.'); return true; }
    catch (error) { control.setAttribute('aria-invalid', 'true'); report(es(error.message), true); return false; }
  }
  for (const scope of ['screen', 'element']) {
    const root = scope === 'screen' ? screenRoot : elementRoot;
    if (scope === 'screen') root.append(node('p', 'AJUSTES DE LA PÁGINA', 'advanced-eyebrow'));
    if (scope === 'screen') root.append(node('p', 'Todo es opcional: vacío o "Por defecto" deja el comportamiento de siempre. Los cambios quedan en el proyecto hasta que exportes o publiques.', 'hint'));
    const groups = [...new Set(SETTINGS.filter(s => s.scope === scope).map(s => s.group))];
    for (const group of groups) {
      const section = node('details', null, 'advanced-section'), summary = node('summary', GROUP_TEXT[group] ?? group);
      section.append(summary); section.open = place ? scope === 'element' : group === 'Access' || group === 'Visibility and interaction'; (place?.(scope, group) ?? root).append(section);
      sections.push({ section, scope, group, summary });
      const specs = SETTINGS.filter(s => s.scope === scope && s.group === group);
      if (scope === 'element' && ['Keyframe animation', 'PNG / GIF animation', 'Data list'].includes(group)) {
        const feature = specs[0].path.split('.')[0], remove = node('button', `Quitar: ${(GROUP_TEXT[group] ?? group).toLowerCase()}`, 'secondary feature-reset'); remove.type = 'button';
        remove.addEventListener('click', () => run(remove, (s, id) => removeFeature(s, scope, feature, id))); section.append(remove);
      }
      for (const spec of specs) {
        const wrap = node('div', null, 'advanced-field'), id = `advanced-${scope}-${spec.path.replaceAll('.', '-')}`;
        const text = SETTING_TEXT[`${scope}:${spec.path}`] ?? [spec.label, spec.help], shown = value => OPTION_TEXT[String(value)] ?? String(value);
        const label = node('label', text[0]); label.htmlFor = id; wrap.append(label);
        let control;
        if (['select', 'boolean', 'element'].includes(spec.type)) {
          control = node('select');
          const option = (label, value) => { const o = node('option', label); o.value = value; return o; };
          control.append(option(spec.type === 'element' ? 'Ninguno' : `Por defecto${spec.default !== undefined ? ` (${shown(spec.default)})` : ''}`, ''));
          for (const value of spec.type === 'boolean' ? [true, false] : spec.options ?? []) control.append(option(spec.type === 'boolean' ? value ? 'Sí' : 'No' : shown(value), String(value)));
        } else if (['json', 'actions'].includes(spec.type)) { control = node('textarea'); control.rows = 4; control.spellcheck = false; }
        else { control = node('input'); control.type = spec.type; control.spellcheck = false; if (spec.type === 'number') { control.min = spec.min; control.max = spec.max; control.step = 1; } control.placeholder = spec.default === undefined ? 'Opcional' : `Por defecto: ${spec.default}`; }
        control.id = id; wrap.append(control);
        const error = node('p', null, 'advanced-error'); error.id = `${id}-error`; error.setAttribute('aria-live', 'polite');
        const save = () => {
          error.textContent = '';
          return run(control, (s, elementId) => {
            try {
              const raw = control.value;
              const value = !raw.trim() ? undefined : ['json', 'actions'].includes(spec.type) ? JSON.parse(raw) : spec.type === 'boolean' ? raw === 'true' : spec.type === 'number' ? Number(raw) : raw;
              return writeSetting(s, scope, spec.path, value, elementId);
            } catch (failure) { error.textContent = es(failure.message); throw failure; }
          }, ['json', 'actions'].includes(spec.type));
        };
        const widget = { spec, control, wrap, error, identity: null, draft: false, saved: undefined }; widgets.push(widget);
        if (['json', 'actions'].includes(spec.type)) {
          const tools = node('div', null, 'advanced-draft-actions'), applyButton = node('button', 'Aplicar', 'secondary'); applyButton.type = 'button';
          control.addEventListener('input', () => { widget.draft = true; applyButton.textContent = 'Aplicar cambios'; notifyDrafts(); });
          applyButton.addEventListener('click', () => { if (save()) { widget.draft = false; applyButton.textContent = 'Aplicar'; notifyDrafts(); } }); tools.append(applyButton);
          if (spec.example) {
            const example = node('button', 'Ejemplo → borrador', 'secondary'); example.type = 'button';
            example.addEventListener('click', () => { control.value = JSON.stringify(spec.example, null, 2); widget.draft = true; applyButton.textContent = 'Aplicar cambios'; control.focus(); notifyDrafts(); }); tools.append(example);
          }
          const revert = node('button', 'Descartar borrador', 'secondary'); revert.type = 'button';
          revert.addEventListener('click', () => { widget.draft = false; control.removeAttribute('aria-invalid'); error.textContent = ''; sync(); applyButton.textContent = 'Aplicar'; notifyDrafts(); }); tools.append(revert); wrap.append(tools);
          widget.applyButton = applyButton;
          if (spec.type === 'actions') {
            const composer = node('details', null, 'action-composer'); composer.append(node('summary', 'Agregar una acción'));
            const choice = node('select'), choiceLabel = node('label', 'Tipo de acción'); choice.id = `${id}-recipe`; choiceLabel.htmlFor = choice.id;
            for (const recipe of ACTION_RECIPES) { const o = node('option', RECIPE_TEXT[recipe.id] ?? recipe.label); o.value = recipe.id; choice.append(o); }
            const fields = node('div', null, 'action-recipe-fields'), add = node('button', 'Agregar al borrador', 'secondary'); add.type = 'button';
            const recipeInputs = [];
            const showRecipe = () => {
              recipeInputs.length = 0; fields.replaceChildren();
              const action = ACTION_RECIPES.find(r => r.id === choice.value).action;
              for (const [key, value] of Object.entries(action)) {
                if (key === 'type') continue;
                const label = node('label', key), input = node(typeof value === 'object' ? 'textarea' : typeof value === 'boolean' ? 'select' : 'input');
                input.id = `${id}-recipe-${key}`; label.htmlFor = input.id;
                if (typeof value === 'boolean') for (const v of ['true', 'false']) { const option = node('option', v); option.value = v; input.append(option); }
                if (typeof value === 'number') input.type = 'number';
                input.value = typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
                fields.append(label, input); recipeInputs.push({ key, input, type: typeof value });
              }
            };
            choice.addEventListener('change', showRecipe); showRecipe();
            add.addEventListener('click', () => {
              try {
                const actions = appendAction(control.value, choice.value), action = actions.at(-1);
                for (const { key, input, type } of recipeInputs) action[key] = type === 'object' ? JSON.parse(input.value) : type === 'boolean' ? input.value === 'true' : type === 'number' ? Number(input.value) : input.value;
                control.value = JSON.stringify(actions, null, 2); widget.draft = true; applyButton.textContent = 'Aplicar cambios'; control.focus(); error.textContent = ''; report('Acción agregada al borrador. Tocá Aplicar para guardar este evento.');
                notifyDrafts();
              } catch (errorValue) { error.textContent = es(errorValue.message); report(es(errorValue.message), true); }
            });
            composer.append(choiceLabel, choice, fields, add); wrap.append(composer);
          }
        } else control.addEventListener(['text', 'number'].includes(spec.type) ? 'input' : 'change', save);
        if (text[1]) { const help = node('p', text[1], 'hint'); help.id = `${id}-help`; wrap.append(help); control.setAttribute('aria-describedby', `${help.id} ${error.id}`); }
        else control.setAttribute('aria-describedby', error.id);
        wrap.append(error); section.append(wrap);
      }
    }
  }
  function sync() {
    const state = getState();
    for (const widget of widgets) {
      const { spec, control } = widget, identity = `${state.screen.id}/${spec.scope === 'element' ? state.elementId : ''}`;
      if (widget.identity !== identity) { widget.draft = false; widget.identity = identity; control.removeAttribute('aria-invalid'); widget.error.textContent = ''; }
      const value = readSetting(state.screen, spec.scope, spec.path, state.elementId);
      // The runtime uses no exit when close is absent, but defaults an existing close object to fade.
      if (spec.path === 'animation.close.effect') control.options[0].textContent = `Por defecto (${readSetting(state.screen, 'screen', 'animation.close') ? 'fundido' : 'ninguno'})`;
      const saved = JSON.stringify(value);
      // A new saved value from undo, import or the raw logic editor replaces stale local drafts.
      if (widget.saved !== saved) { widget.draft = false; control.removeAttribute('aria-invalid'); widget.error.textContent = ''; }
      widget.saved = saved;
      if (spec.type === 'element') {
        const chosen = control.value; control.replaceChildren(); const none = node('option', 'Ninguno'); none.value = ''; control.append(none);
        for (const element of state.screen.elements) if (element.id !== state.elementId) { const option = node('option', element.id); option.value = element.id; control.append(option); }
        control.value = chosen;
      }
      if (!widget.draft) {
        const rendered = value === undefined || value === null ? '' : ['json', 'actions'].includes(spec.type) ? JSON.stringify(value, null, 2) : String(value);
        if (control.value !== rendered) control.value = rendered;
        if (widget.applyButton) widget.applyButton.textContent = 'Aplicar';
      }
      const disabled = !state.editable || spec.scope === 'element' && (!state.elementId || state.locked || spec.buttonOnly && state.kind !== 'button');
      for (const input of widget.wrap.querySelectorAll('input,select,textarea,button')) input.disabled = disabled;
      widget.wrap.dataset.unavailable = String(disabled);
    }
    for (const { section, scope, group, summary } of sections) {
      const configured = SETTINGS.filter(s => s.scope === scope && s.group === group).filter(s => readSetting(state.screen, scope, s.path, state.elementId) !== undefined).length;
      summary.textContent = `${GROUP_TEXT[group] ?? group}${configured ? ` · ${configured}` : ''}`;
      for (const button of section.querySelectorAll('.feature-reset')) button.disabled = !state.editable || state.locked || !state.elementId;
    }
  }
  return { sync, pendingDraftCount, hasPendingDrafts: () => pendingDraftCount() > 0 };
}
