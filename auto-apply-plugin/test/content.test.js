'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { loadContent, createElement, createDocument, flush } = require('./harness.js');

// The background answers the content script's AI requests.
function backgroundReply(text) {
  return (message) => Promise.resolve(
    message.action === 'aiFillField' || message.action === 'aiChooseOption' ? { success: true, text } : undefined
  );
}

function pageWithForm() {
  const document = createDocument();
  const section = createElement({ tagName: 'DIV', textContent: 'Contact details' });
  const email = createElement({ tagName: 'INPUT', type: 'email', id: 'email', name: 'email' });
  section.appendChild(email);
  const other = createElement({ tagName: 'DIV' });
  const otherInput = createElement({ tagName: 'INPUT', type: 'text', id: 'city', name: 'city' });
  other.appendChild(otherInput);
  document.body.appendChild(section);
  document.body.appendChild(other);
  // The bug: page coordinates were passed to elementFromPoint, which hit the wrong element.
  document.elementFromPoint = () => other;
  return { document, section, email, other, otherInput };
}

test('fill-element uses the right-clicked element, not coordinates', async () => {
  const page = pageWithForm();
  const content = loadContent({ document: page.document, runtimeSendMessage: backgroundReply('jane@example.com') });
  page.document.fire('contextmenu', { target: page.section, pageX: 500, pageY: 300 });

  const result = await content.send({ action: 'fillElementWithAI', clickX: 500, clickY: 300 });

  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(result.fieldsCount, 1);
  assert.equal(page.email.value, 'jane@example.com');
  assert.equal(page.otherInput.value, '');
});

test('fill-element without a right-clicked element reports an error', async () => {
  const content = loadContent({ document: createDocument() });
  const result = await content.send({ action: 'fillElementWithAI' });
  assert.equal(result.success, false);
  assert.match(result.error, /target element/);
});

test('a detached right-clicked element is not used', async () => {
  const page = pageWithForm();
  const content = loadContent({ document: page.document, runtimeSendMessage: backgroundReply('x') });
  page.document.fire('contextmenu', { target: page.section });
  page.section.isConnected = false;
  const result = await content.send({ action: 'fillElementWithAI' });
  assert.equal(result.success, false);
});

test('a custom combobox is filled as text, not through the undefined fillCustomDropdownWithAI', async () => {
  const document = createDocument();
  const combobox = createElement({ tagName: 'INPUT', type: 'text', role: 'combobox', className: 'rcmpaginatedselectinput', name: 'country' });
  document.body.appendChild(combobox);
  const content = loadContent({ document, runtimeSendMessage: backgroundReply('United Kingdom') });
  document.fire('contextmenu', { target: combobox });
  const result = await content.send({ action: 'fillElementWithAI' });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(combobox.value, 'United Kingdom');
  assert.deepEqual(result.errors.length, 0);
});

test('without a recorded right-click, the focused field is filled', async () => {
  // The content script is injected after the right-click, so it may not have seen it.
  const page = pageWithForm();
  page.document.activeElement = page.email;
  const content = loadContent({ document: page.document, runtimeSendMessage: backgroundReply('jane@example.com') });
  const result = await content.send({ action: 'fillElementWithAI' });
  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(page.email.value, 'jane@example.com');
});

test('messages to a closed popup do not leave rejected promises', async () => {
  const content = loadContent({ document: createDocument() });
  await content.send({ action: 'fillElementWithAI' });
  await flush();
  assert.ok(content.chrome.calls.runtimeMessages.length > 0);
  for (const record of content.chrome.calls.runtimeMessages) {
    assert.equal(record.handled, true, `${record.message.action} rejection handled`);
  }
});

test('analyzePage counts fields', async () => {
  const page = pageWithForm();
  const content = loadContent({ document: page.document });
  page.document.querySelectorAll = (selector) => {
    if (selector === 'input, textarea, select') return [page.email, page.otherInput];
    return [];
  };
  const result = await content.send({ action: 'analyzePage' });
  assert.deepEqual({ ...result }, { success: true, inputFields: 2, forms: 0, buttons: 0 });
});
