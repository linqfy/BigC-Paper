import test from 'node:test';
import assert from 'node:assert/strict';
import { mountAdvancedSettings } from './advanced-ui.mjs';
import { createScreen } from './model.mjs';
import { writeSetting } from './advanced-settings.mjs';

// Only the DOM operations used by this controller; no browser or dependencies.
class TestNode {
  constructor(tag, ownerDocument) {
    Object.assign(this, { tag, ownerDocument, children: [], listeners: {}, attributes: {}, dataset: {}, disabled: false, textContent: '' });
  }
  append(...children) { for (const child of children) { child.parentElement = this; this.children.push(child); } }
  replaceChildren(...children) { this.children = []; this.append(...children); }
  get options() { return this.children; }
  get value() { return this.storedValue ?? (this.tag === 'select' ? this.children[0]?.value ?? '' : ''); }
  set value(value) { this.storedValue = String(value); }
  addEventListener(type, listener) { (this.listeners[type] ??= []).push(listener); }
  setAttribute(key, value) { this.attributes[key] = String(value); }
  getAttribute(key) { return this.attributes[key] ?? null; }
  removeAttribute(key) { delete this.attributes[key]; }
  focus() { this.ownerDocument.activeElement = this; }
  querySelectorAll(selector) {
    const matches = node => selector.split(',').some(part => part.startsWith('.') ? node.className?.split(' ').includes(part.slice(1)) : node.tag === part);
    return this.children.flatMap(child => [...(matches(child) ? [child] : []), ...child.querySelectorAll(selector)]);
  }
  fire(type) { for (const listener of this.listeners[type] ?? []) listener({ target: this }); }
  click() { if (!this.disabled) this.fire('click'); }
}

function setup({ callback = true } = {}) {
  const document = { createElement: tag => new TestNode(tag, document), activeElement: null };
  const screenRoot = document.createElement('div'), elementRoot = document.createElement('div');
  const reports = [], draftChanges = [];
  let state = { screen: createScreen(), elementId: 'start', kind: 'button', locked: false, editable: true };
  let rejectApply = false, applied = 0, controller;
  controller = mountAdvancedSettings({
    screenRoot, elementRoot, getState: () => state,
    apply(next) {
      if (rejectApply) throw Error('Application rejected');
      state = { ...state, screen: next }; applied++;
      // app.mjs renders synchronously while handling a successful setting.
      controller.sync();
    },
    report: (...args) => reports.push(args),
    ...(callback ? { onDraftChange: (...args) => draftChanges.push(args) } : {})
  });
  controller.sync();
  const control = id => {
    const found = [...screenRoot.querySelectorAll('input,select,textarea'), ...elementRoot.querySelectorAll('input,select,textarea')].find(node => node.id === id);
    assert.ok(found, `Missing control ${id}`); return found;
  };
  const button = (input, label) => {
    const found = input.parentElement.querySelectorAll('button').find(node => node.textContent === label);
    assert.ok(found, `Missing button ${label}`); return found;
  };
  const type = (input, value) => { input.value = value; input.fire('input'); };
  return { controller, control, button, type, reports, draftChanges,
    get state() { return state; }, get applied() { return applied; },
    setState(patch) { state = { ...state, ...patch }; }, rejectApply(value) { rejectApply = value; } };
}

function pending(ui, expected) {
  assert.equal(ui.controller.pendingDraftCount(), expected);
  assert.equal(ui.controller.hasPendingDrafts(), expected > 0);
}

test('a sibling simple control cannot replace pending custom keyframe steps', () => {
  const ui = setup(), steps = ui.control('advanced-element-keyframes-steps'), trigger = ui.control('advanced-element-keyframes-trigger');
  const raw = '[{"at":0,"x":-99},{"at":700,"x":12}]';
  ui.type(steps, raw); trigger.value = 'action'; trigger.fire('change');
  pending(ui, 1); assert.equal(steps.value, raw); assert.equal(ui.applied, 0);
  ui.button(steps, 'Aplicar cambios').click(); pending(ui, 0);
  assert.deepEqual(ui.state.screen.elements.find(e => e.id === 'start').logic.keyframes.steps, JSON.parse(raw));
});

test('advanced controller reports clean state and tracks independent JSON drafts', () => {
  const ui = setup(), actions = ui.control('advanced-element-actions'), variables = ui.control('advanced-screen-variables');
  pending(ui, 0);
  const notifications = ui.draftChanges.length;
  ui.type(actions, '[{"type":"close"}]'); pending(ui, 1);
  assert.ok(ui.draftChanges.length > notifications, 'input must notify application guards');
  ui.type(actions, '[{"type":"message","value":"Hello"}]'); pending(ui, 1);
  ui.type(variables, '{"coins":"10"}'); pending(ui, 2);
  assert.equal(ui.applied, 0, 'typing must not save JSON');
  ui.button(actions, 'Descartar borrador').click(); pending(ui, 1);
  assert.equal(actions.value, '');
  assert.equal(variables.value, '{"coins":"10"}');
  ui.button(variables, 'Descartar borrador').click(); pending(ui, 0);
});

test('Example marks a pending draft and successful Apply clears it', () => {
  const ui = setup(), variables = ui.control('advanced-screen-variables');
  const notifications = ui.draftChanges.length;
  ui.button(variables, 'Ejemplo → borrador').click();
  pending(ui, 1); assert.ok(ui.draftChanges.length > notifications);
  assert.deepEqual(JSON.parse(variables.value), { coins: '100', '*total': '0' });
  assert.equal(ui.state.screen.logic, undefined);
  ui.button(variables, 'Aplicar cambios').click();
  pending(ui, 0); assert.equal(ui.applied, 1);
  assert.deepEqual(ui.state.screen.logic.variables, { coins: '100', '*total': '0' });
});

test('action composer marks a pending draft and preserves whitespace in its fields', () => {
  const ui = setup(), actions = ui.control('advanced-element-actions');
  const message = ui.control('advanced-element-actions-recipe-value');
  message.value = '  Hello player  ';
  const notifications = ui.draftChanges.length;
  ui.button(actions, 'Agregar al borrador').click();
  pending(ui, 1); assert.ok(ui.draftChanges.length > notifications);
  assert.deepEqual(JSON.parse(actions.value), [{ type: 'message', value: '  Hello player  ' }]);
  assert.equal(ui.applied, 0);
  ui.button(actions, 'Aplicar cambios').click(); pending(ui, 0);
  assert.equal(ui.state.screen.elements.find(element => element.id === 'start').logic.actions[0].value, '  Hello player  ');
});

test('syntax and application failures retain the pending draft until successful Apply', () => {
  const ui = setup(), actions = ui.control('advanced-element-actions');
  ui.type(actions, '  [{broken  ');
  ui.button(actions, 'Aplicar cambios').click();
  pending(ui, 1); assert.equal(actions.value, '  [{broken  ');
  assert.equal(actions.getAttribute('aria-invalid'), 'true'); assert.equal(ui.applied, 0);
  const raw = '  [{"type":"message","value":"  typed  "}]  ';
  ui.type(actions, raw); ui.rejectApply(true);
  ui.button(actions, 'Aplicar cambios').click();
  pending(ui, 1); assert.equal(actions.value, raw); assert.equal(ui.applied, 0);
  ui.rejectApply(false); ui.button(actions, 'Aplicar cambios').click();
  pending(ui, 0); assert.equal(ui.applied, 1);
  assert.equal(ui.state.screen.elements.find(element => element.id === 'start').logic.actions[0].value, '  typed  ');
});

test('unrelated synchronization and temporarily invalid screen keep exact draft text', () => {
  const ui = setup(), actions = ui.control('advanced-element-actions');
  const raw = '\n  [{"type":"message", "value":"  pending  "}]  \n';
  ui.type(actions, raw);
  ui.setState({ screen: writeSetting(ui.state.screen, 'screen', 'command', 'shop') }); ui.controller.sync();
  pending(ui, 1); assert.equal(actions.value, raw);
  ui.setState({ editable: false }); ui.controller.sync();
  pending(ui, 1); assert.equal(actions.value, raw); assert.equal(actions.disabled, true);
  ui.setState({ editable: true }); ui.controller.sync();
  pending(ui, 1); assert.equal(actions.value, raw); assert.equal(actions.disabled, false);
  ui.button(actions, 'Descartar borrador').click(); pending(ui, 0); assert.equal(actions.value, '');
  assert.equal(ui.state.screen.logic.command, 'shop');
});

test('simple text settings preserve spaces while typing without creating JSON drafts', () => {
  const ui = setup(), condition = ui.control('advanced-element-visible-if');
  ui.type(condition, '{coins} ');
  assert.equal(condition.value, '{coins} ');
  assert.equal(ui.state.screen.elements.find(element => element.id === 'start').logic['visible-if'], '{coins} ');
  ui.type(condition, `${condition.value}>= 10 `);
  assert.equal(condition.value, '{coins} >= 10 ');
  assert.equal(ui.state.screen.elements.find(element => element.id === 'start').logic['visible-if'], '{coins} >= 10 ');
  assert.equal(ui.applied, 2);
});

test('draft notifications remain optional', () => {
  const ui = setup({ callback: false }), actions = ui.control('advanced-element-actions');
  ui.type(actions, '[{"type":"close"}]');
  ui.button(actions, 'Aplicar cambios').click();
  assert.equal(ui.applied, 1);
  assert.deepEqual(ui.state.screen.elements.find(element => element.id === 'start').logic.actions, [{ type: 'close' }]);
});
