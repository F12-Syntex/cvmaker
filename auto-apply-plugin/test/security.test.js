'use strict';

// Item 32: the API key and the profile stay in the service worker; content scripts are injected
// on demand; host permissions are narrow; the model is configurable.
const test = require('node:test');
const assert = require('node:assert/strict');

const { readPluginFile, loadBackground, loadContent, createDocument, createElement, flush } = require('./harness.js');
const lib = require('../lib/fields.js');

const KEY = 'sk-test-secret-key-0123456789';
const PROFILE = 'Jane Doe, jane@example.com, +44 7000 000000, Java engineer';
const CONFIGURED = { openaiApiKey: KEY, userData: PROFILE, openaiModel: 'gpt-4.1-mini' };
const TAB_SENDER = { id: 'test-extension-id', tab: { id: 12 } };

function okFetch(text, seen) {
  return (url, init) => {
    seen.push({ url, init });
    return Promise.resolve({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ choices: [{ message: { content: text } }] })
    });
  };
}

// ---- manifest ----

test('manifest declares no content scripts, no <all_urls> and no tabs permission', () => {
  const manifest = JSON.parse(readPluginFile('manifest.json'));
  assert.equal(manifest.content_scripts, undefined);
  assert.ok(!JSON.stringify(manifest).includes('<all_urls>'));
  assert.ok(!manifest.permissions.includes('tabs'), 'activeTab is enough');
  assert.deepEqual([...manifest.host_permissions].sort(), ['https://api.openai.com/*', 'https://www.linkedin.com/*']);
  for (const permission of ['activeTab', 'scripting', 'storage']) {
    assert.ok(manifest.permissions.includes(permission), permission);
  }
});

// ---- content script ----

test('content.js has no network calls and never handles the key or the profile', () => {
  const source = readPluginFile('content.js');
  assert.doesNotMatch(source, /\bfetch\s*\(/);
  assert.doesNotMatch(source, /api\.openai\.com/);
  assert.doesNotMatch(source, /apiKey|openaiApiKey|userData/);
  assert.doesNotMatch(source, /chrome\.storage/);
});

test('content script asks the background for each field and fills the answer', async () => {
  const document = createDocument();
  const form = createElement({ tagName: 'FORM' });
  const email = createElement({ tagName: 'INPUT', type: 'email', name: 'email' });
  const select = createElement({ tagName: 'SELECT', name: 'country' });
  select.options = [{ text: 'Choose', value: '' }, { text: 'France', value: 'fr' }, { text: 'United Kingdom', value: 'uk' }];
  form.appendChild(email);
  document.body.appendChild(form);
  document.querySelectorAll = (selector) => {
    if (selector === 'select') return [select];
    if (selector.startsWith('input[type="text"]')) return [email];
    return [];
  };
  const requests = [];
  const content = loadContent({
    document,
    runtimeSendMessage: (message) => {
      requests.push(message);
      if (message.action === 'aiFillField') return Promise.resolve({ success: true, text: 'jane@example.com' });
      if (message.action === 'aiChooseOption') return Promise.resolve({ success: true, text: 'United Kingdom' });
      return Promise.resolve(undefined);
    }
  });

  const result = await content.send({ action: 'fillFormWithAI' });
  assert.equal(result.success, true);
  assert.equal(result.fieldsCount, 2, JSON.stringify(result));
  assert.equal(email.value, 'jane@example.com');
  assert.equal(select.value, 'uk');

  const field = requests.find(r => r.action === 'aiFillField');
  assert.equal(field.field.type, 'email');
  const choose = requests.find(r => r.action === 'aiChooseOption');
  assert.deepEqual([...choose.options], ['France', 'United Kingdom']);
  for (const request of requests) {
    assert.equal('apiKey' in request, false);
    assert.equal('userData' in request, false);
  }
});

test('an AI error from the background is reported per field', async () => {
  const document = createDocument();
  const input = createElement({ tagName: 'INPUT', type: 'text', name: 'city' });
  document.querySelectorAll = (selector) => (selector.startsWith('input[type="text"]') ? [input] : []);
  const content = loadContent({
    document,
    runtimeSendMessage: () => Promise.resolve({ success: false, error: 'OpenAI API error: 401 - bad key' })
  });
  const result = await content.send({ action: 'fillFormWithAI' });
  assert.equal(result.fieldsCount, 0);
  assert.match(result.errors[0], /401/);
});

test('injecting content.js twice registers one message listener', () => {
  const content = loadContent({ document: createDocument() });
  content.run(readPluginFile('content.js'));
  assert.equal(content.chrome.runtime.onMessage.listeners.length, 1);
});

// ---- background ----

test('the background restricts storage.local to trusted contexts', () => {
  const { chrome } = loadBackground();
  assert.equal(chrome.storage.local.accessLevel, 'TRUSTED_CONTEXTS');
});

test('the background calls OpenAI with the stored key, model and profile', async () => {
  const seen = [];
  const { send } = loadBackground({ local: CONFIGURED, fetch: okFetch('"jane@example.com"', seen) });
  const reply = await send({
    action: 'aiFillField',
    field: { type: 'email', context: 'Email', placeholder: '', label: 'Email' },
    jobContext: 'Page: Apply'
  }, TAB_SENDER);

  assert.deepEqual({ ...reply }, { success: true, text: 'jane@example.com' });
  assert.equal(seen.length, 1);
  assert.equal(seen[0].url, 'https://api.openai.com/v1/chat/completions');
  assert.equal(seen[0].init.headers.Authorization, `Bearer ${KEY}`);
  const body = JSON.parse(seen[0].init.body);
  assert.equal(body.model, 'gpt-4.1-mini');
  assert.match(body.messages[1].content, /Jane Doe/);
  assert.match(body.messages[1].content, /the user's email address/);
  assert.ok(!JSON.stringify(reply).includes(KEY));
});

test('the background chooses select options for the content script', async () => {
  const seen = [];
  const { send } = loadBackground({ local: CONFIGURED, fetch: okFetch('United Kingdom', seen) });
  const reply = await send({
    action: 'aiChooseOption', options: ['France', 'United Kingdom'], field: { context: 'Country' }, jobContext: ''
  }, TAB_SENDER);
  assert.equal(reply.text, 'United Kingdom');
  assert.match(JSON.parse(seen[0].init.body).messages[1].content, /France, United Kingdom/);
});

test('AI requests not coming from a tab are refused', async () => {
  const seen = [];
  const { send } = loadBackground({ local: CONFIGURED, fetch: okFetch('x', seen) });
  const reply = await send({ action: 'aiFillField', field: { type: 'email' } }, { id: 'test-extension-id' });
  assert.equal(reply.success, false);
  assert.equal(seen.length, 0);
});

test('messages from another extension are ignored', async () => {
  const seen = [];
  const { send } = loadBackground({ local: CONFIGURED, fetch: okFetch('x', seen) });
  const reply = await send({ action: 'aiFillField', field: { type: 'email' } }, { id: 'other-extension', tab: { id: 1 } });
  assert.equal(reply, undefined);
  assert.equal(seen.length, 0);
});

test('a missing key gives an error without calling OpenAI', async () => {
  const seen = [];
  const { send } = loadBackground({ local: { userData: PROFILE }, fetch: okFetch('x', seen) });
  const reply = await send({ action: 'aiFillField', field: { type: 'email' } }, TAB_SENDER);
  assert.equal(reply.success, false);
  assert.match(reply.error, /No OpenAI API key/);
  assert.equal(seen.length, 0);
});

test('OpenAI errors reach the page with any key fragment redacted', async () => {
  const fetch = () => Promise.resolve({
    ok: false,
    status: 401,
    json: () => Promise.resolve({ error: { message: `Incorrect API key provided: ${KEY}.` } })
  });
  const { send } = loadBackground({ local: CONFIGURED, fetch });
  const reply = await send({ action: 'aiFillField', field: { type: 'email' } }, TAB_SENDER);
  assert.equal(reply.success, false);
  assert.match(reply.error, /401/);
  assert.ok(!reply.error.includes(KEY));
  assert.ok(!reply.error.includes('secret-key'));
});

test('nothing the background sends to a tab contains the key or the profile', async () => {
  const { chrome, send } = loadBackground({ local: CONFIGURED, fetch: okFetch('x', []) });
  chrome.contextMenus.onClicked.fire({ menuItemId: 'ai-fill-element', frameId: 0 }, { id: 3 });
  const started = await send({ action: 'startJobSearch', maxPages: 1, params: { jobTitle: 'Dev' } });
  chrome.tabs.onUpdated.fire(started.tabId, { status: 'complete' }, { id: started.tabId });
  await flush();
  assert.ok(chrome.calls.tabMessages.length >= 2);
  const sent = JSON.stringify(chrome.calls.tabMessages);
  assert.ok(!sent.includes(KEY));
  assert.ok(!sent.includes('Jane Doe'));
});

test('the LinkedIn tab gets the content script injected before scraping starts', async () => {
  const { chrome, send } = loadBackground({ local: CONFIGURED });
  const started = await send({ action: 'startJobSearch', maxPages: 1, params: { jobTitle: 'Dev' } });
  chrome.tabs.onUpdated.fire(started.tabId, { status: 'complete' }, { id: started.tabId });
  await flush();
  assert.equal(chrome.calls.executeScript.length, 1);
  assert.equal(chrome.calls.executeScript[0].target.tabId, started.tabId);
  assert.ok(chrome.calls.tabMessages.some(m => m.message.action === 'startLinkedInScraping'));
});

// ---- pure helpers ----

test('normalizeModel accepts model names and falls back to the default', () => {
  assert.equal(lib.normalizeModel('gpt-5-mini'), 'gpt-5-mini');
  assert.equal(lib.normalizeModel(' gpt-4.1 '), 'gpt-4.1');
  assert.equal(lib.normalizeModel('ft:gpt-4.1-mini:org:name:id'), 'ft:gpt-4.1-mini:org:name:id');
  assert.equal(lib.normalizeModel(''), lib.DEFAULT_MODEL);
  assert.equal(lib.normalizeModel(undefined), lib.DEFAULT_MODEL);
  assert.equal(lib.normalizeModel('gpt 4; rm -rf'), lib.DEFAULT_MODEL);
  assert.notEqual(lib.DEFAULT_MODEL, 'gpt-3.5-turbo');
});

test('buildChatCompletionRequest adapts to reasoning models', () => {
  const classic = lib.buildChatCompletionRequest('gpt-4.1-mini', 'hello');
  assert.equal(classic.model, 'gpt-4.1-mini');
  assert.equal(classic.temperature, 0.3);
  assert.equal(classic.max_tokens, undefined);
  assert.ok(classic.max_completion_tokens > 0);
  assert.equal(classic.messages[1].content, 'hello');

  const reasoning = lib.buildChatCompletionRequest('gpt-5-mini', 'hello');
  assert.equal(reasoning.temperature, undefined);
  assert.equal(reasoning.reasoning_effort, 'low');
  assert.ok(reasoning.max_completion_tokens >= 1000);
  assert.equal(lib.isReasoningModel('o4-mini'), true);
});

test('parseChatCompletion rejects malformed responses', () => {
  assert.equal(lib.parseChatCompletion({ choices: [{ message: { content: ' hi ' } }] }), 'hi');
  assert.throws(() => lib.parseChatCompletion({}), /no text/);
  assert.throws(() => lib.parseChatCompletion({ choices: [{ message: { content: null } }] }), /no text/);
});

test('buildFieldPrompt limits page-supplied text and ignores unknown types', () => {
  const prompt = lib.buildFieldPrompt(PROFILE, {
    type: '__proto__', context: 'c'.repeat(5000), placeholder: 'p', label: 'l'
  }, 'j'.repeat(5000));
  assert.match(prompt, /Type: generic/);
  assert.ok(prompt.length < 2000, `prompt length ${prompt.length}`);
  assert.match(prompt, /Jane Doe/);
  assert.match(lib.buildFieldPrompt(PROFILE, { type: 'phone' }, ''), /phone number in standard format/);
  assert.doesNotMatch(lib.buildFieldPrompt(PROFILE, { type: 'phone' }, ''), /Page Context/);
});

test('buildSelectPrompt lists the options', () => {
  const prompt = lib.buildSelectPrompt(PROFILE, ['A', '', 'B', 42], 'ctx', 'job');
  assert.match(prompt, /choices: A, B, 42\n/);
  assert.equal(lib.sanitizeOptions(new Array(500).fill('x')).length, 200);
  assert.deepEqual(lib.sanitizeOptions('nope'), []);
});

test('matchOption prefers exact matches and ignores empty options', () => {
  assert.equal(lib.matchOption(['No', 'Not sure', 'Yes'], 'yes'), 2);
  assert.equal(lib.matchOption(['Notice period', 'No'], 'No'), 1);
  assert.equal(lib.matchOption(['', 'United Kingdom'], 'United Kingdom of Great Britain'), 1);
  assert.equal(lib.matchOption(['France'], 'Spain'), -1);
  assert.equal(lib.matchOption(['France'], ''), -1);
});

test('cleanAIResponse and redactSecrets', () => {
  assert.equal(lib.cleanAIResponse('"Jane"'), 'Jane');
  assert.equal(lib.cleanAIResponse(undefined), '');
  assert.equal(lib.redactSecrets('key sk-proj-abc*****wxyz bad'), 'key sk-[redacted] bad');
  assert.equal(lib.redactSecrets('no secrets'), 'no secrets');
});
