'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { loadContent, createElement, createDocument, flush } = require('./harness.js');

function openAiReply(text) {
  return () => Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve({ choices: [{ message: { content: text } }] })
  });
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
  const content = loadContent({ document: page.document, fetch: openAiReply('jane@example.com') });
  page.document.fire('contextmenu', { target: page.section, pageX: 500, pageY: 300 });

  const result = await content.send({
    action: 'fillElementWithAI', apiKey: 'sk-test', userData: 'Jane', clickX: 500, clickY: 300
  });

  assert.equal(result.success, true, JSON.stringify(result));
  assert.equal(result.fieldsCount, 1);
  assert.equal(page.email.value, 'jane@example.com');
  assert.equal(page.otherInput.value, '');
});

test('fill-element without a right-clicked element reports an error', async () => {
  const content = loadContent({ document: createDocument() });
  const result = await content.send({ action: 'fillElementWithAI', apiKey: 'sk-test', userData: 'Jane' });
  assert.equal(result.success, false);
  assert.match(result.error, /target element/);
});

test('a detached right-clicked element is not used', async () => {
  const page = pageWithForm();
  const content = loadContent({ document: page.document, fetch: openAiReply('x') });
  page.document.fire('contextmenu', { target: page.section });
  page.section.isConnected = false;
  const result = await content.send({ action: 'fillElementWithAI', apiKey: 'sk-test', userData: 'Jane' });
  assert.equal(result.success, false);
});

test('a custom combobox does not hit the undefined fillCustomDropdownWithAI', async () => {
  const content = loadContent({ document: createDocument() });
  const combobox = createElement({ tagName: 'INPUT', type: 'text', role: 'combobox' });
  const fillSelectWithAI = content.run('fillSelectWithAI');
  assert.equal(await fillSelectWithAI('sk-test', 'Jane', combobox, ''), false);
});

test('messages to a closed popup do not leave rejected promises', async () => {
  const content = loadContent({ document: createDocument() });
  await content.send({ action: 'fillElementWithAI', apiKey: 'sk-test', userData: 'Jane' });
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
