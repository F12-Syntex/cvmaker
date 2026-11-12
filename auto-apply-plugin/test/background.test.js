'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const { loadBackground, flush } = require('./harness.js');

const CONFIGURED = { openaiApiKey: 'sk-test-key', userData: 'Jane Doe, jane@example.com, engineer' };

test('the context menu is registered on install', () => {
  const { chrome } = loadBackground();
  chrome.runtime.onInstalled.fire({ reason: 'install' });
  assert.equal(chrome.calls.contextMenus.length, 1);
  assert.equal(chrome.calls.contextMenus[0].id, 'ai-fill-element');
  assert.deepEqual([...chrome.calls.contextMenus[0].contexts], ['editable']);
});

test('context menu click injects into the clicked frame and sends no coordinates or secrets', async () => {
  const { chrome } = loadBackground({ local: CONFIGURED });
  chrome.contextMenus.onClicked.fire({ menuItemId: 'ai-fill-element', frameId: 3, pageX: 10, pageY: 20 }, { id: 7 });
  await flush();
  assert.equal(chrome.calls.executeScript.length, 1);
  assert.deepEqual(JSON.parse(JSON.stringify(chrome.calls.executeScript[0])), {
    target: { tabId: 7, frameIds: [3] }, files: ['lib/fields.js', 'content.js']
  });
  const fill = chrome.calls.tabMessages.find(m => m.message.action === 'fillElementWithAI');
  assert.ok(fill, 'fill message sent');
  assert.equal(fill.tabId, 7);
  assert.deepEqual({ ...fill.options }, { frameId: 3 });
  assert.deepEqual({ ...fill.message }, { action: 'fillElementWithAI' });
});

test('context menu click on a page that cannot be scripted shows a notification', async () => {
  const { chrome } = loadBackground({ local: CONFIGURED, executeScriptError: 'Cannot access a chrome:// URL' });
  chrome.contextMenus.onClicked.fire({ menuItemId: 'ai-fill-element', frameId: 0 }, { id: 7 });
  await flush();
  assert.equal(chrome.calls.tabMessages.length, 0);
  assert.match(chrome.calls.notifications[0].message, /Could not fill fields/);
});

test('context menu click without configuration shows a notification and handles openPopup failure', async () => {
  const { chrome } = loadBackground({ local: {} });
  chrome.contextMenus.onClicked.fire({ menuItemId: 'ai-fill-element', frameId: 0 }, { id: 7 });
  await flush();
  assert.equal(chrome.calls.tabMessages.length, 0);
  assert.equal(chrome.calls.notifications.length, 1);
  // openPopup rejects in the harness; the test file would fail on an unhandled rejection.
});

test('the "LinkedIn loaded" notice to a closed popup does not leave a rejected promise', async () => {
  const { chrome } = loadBackground({ session: { jobSearch: { tabId: 5, started: false, jobTitle: 'x', maxPages: 1 } } });
  chrome.tabs.onUpdated.fire(5, { status: 'complete' }, { id: 5, url: 'https://www.linkedin.com/jobs/search/?keywords=x' });
  await flush();
  const notices = chrome.calls.runtimeMessages.filter(r => r.message.action === 'linkedinLoaded');
  assert.ok(notices.length > 0);
  for (const record of notices) assert.equal(record.handled, true, 'rejection handled');
});
