'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { readPluginFile, loadPopup, flush } = require('./harness.js');

const CONFIGURED = {
  openaiApiKey: 'sk-test-key-123456',
  openaiModel: 'gpt-4.1-mini',
  userData: 'Jane Doe, jane@example.com, software engineer with ten years of experience in Java.'
};

test('popup, content and background scripts never assign innerHTML', () => {
  for (const file of ['popup.js', 'content.js', 'background.js']) {
    assert.doesNotMatch(readPluginFile(file), /\.innerHTML\s*=/, file);
  }
});

test('search log entries are text, not HTML', async () => {
  const popup = loadPopup({ local: CONFIGURED, runtimeSendMessage: () => ({ success: true, tabId: 4 }) });
  await flush();
  popup.el('jobTitle').value = '<img src=x onerror=alert(1)>';
  await popup.el('searchJobs').click();
  await flush();
  const logs = popup.el('searchLogs').appended;
  assert.ok(logs.length > 0);
  const texts = logs.map(entry => entry.appended.map(part => part.textContent).join(''));
  assert.ok(texts.some(text => text.includes('<img src=x onerror=alert(1)>')), 'markup kept as literal text');
});

test('Fill injects the content script, then sends no API key or profile to the page', async () => {
  const popup = loadPopup({
    local: CONFIGURED,
    activeTab: { id: 9 },
    tabSendMessage: () => ({ success: true, fieldsCount: 2, errors: [] })
  });
  await flush();
  await popup.el('fillForm').click();

  const { executeScript, tabMessages } = popup.chrome.calls;
  assert.equal(executeScript.length, 1);
  assert.equal(executeScript[0].target.tabId, 9);
  assert.deepEqual([...executeScript[0].files], ['lib/fields.js', 'content.js']);
  assert.equal(tabMessages.length, 1);
  assert.deepEqual({ ...tabMessages[0].message }, { action: 'fillFormWithAI' });
  assert.match(popup.el('applyStatus').textContent, /filled 2 fields/);
});

test('Fill reports the first AI error when nothing was filled', async () => {
  const popup = loadPopup({
    local: CONFIGURED,
    activeTab: { id: 9 },
    tabSendMessage: () => ({ success: true, fieldsCount: 0, errors: ['Field error: OpenAI API error: 401'] })
  });
  await flush();
  await popup.el('fillForm').click();
  assert.match(popup.el('applyStatus').textContent, /401/);
});

test('Clear and Analyze report a missing tab', async () => {
  const popup = loadPopup({ local: CONFIGURED });
  await flush();
  await popup.el('clearForm').click();
  assert.match(popup.el('applyStatus').textContent, /Unable to access current tab/);
  await popup.el('analyzeForm').click();
  assert.match(popup.el('applyStatus').textContent, /Unable to access current tab/);
  assert.equal(popup.el('clearForm').disabled, false);
});

test('a page that cannot be scripted gets a clear error', async () => {
  const popup = loadPopup({ local: CONFIGURED, activeTab: { id: 9 }, executeScriptError: 'Cannot access a chrome:// URL' });
  await flush();
  await popup.el('analyzeForm').click();
  assert.match(popup.el('applyStatus').textContent, /Cannot access this page/);
  assert.equal(popup.chrome.calls.tabMessages.length, 0);
});

test('Save stores the model, and rejects an invalid one', async () => {
  const popup = loadPopup({ local: {} });
  await flush();
  popup.el('apiKey').value = 'sk-new-key';
  popup.el('userData').value = 'x'.repeat(60);
  popup.el('model').value = 'gpt-5-mini';
  await popup.el('saveConfig').click();
  assert.equal(popup.chrome.storage.local.data.openaiModel, 'gpt-5-mini');

  popup.el('model').value = 'bad model; drop';
  popup.chrome.storage.local.data.openaiModel = 'unchanged';
  await popup.el('saveConfig').click();
  assert.equal(popup.chrome.storage.local.data.openaiModel, 'unchanged');
  assert.match(popup.el('configStatus').textContent, /valid model/);
});

test('the search is started and stopped through the background', async () => {
  const messages = [];
  const popup = loadPopup({
    local: CONFIGURED,
    runtimeSendMessage: (message) => {
      messages.push(message);
      if (message.action === 'startJobSearch') return { success: true, tabId: 4 };
      return { success: true };
    }
  });
  await flush();
  await popup.el('searchJobs').click();
  await flush();
  const start = messages.find(m => m.action === 'startJobSearch');
  assert.ok(start);
  assert.equal(start.params.jobTitle, popup.el('jobTitle').value.trim() || 'Software Engineer');
  assert.equal(messages.some(m => m.action === 'startJobScraping'), false, 'no popup timer');
  assert.equal(popup.el('searchJobs').disabled, false, 'Stop must be clickable');

  await popup.el('searchJobs').click();
  await flush();
  assert.ok(messages.find(m => m.action === 'stopJobSearch'));
});
