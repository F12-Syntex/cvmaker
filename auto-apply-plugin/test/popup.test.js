'use strict';

// popup.js needs the full popup DOM, so these are source-level guards for the bugs fixed in it.
const test = require('node:test');
const assert = require('node:assert/strict');

const { readPluginFile } = require('./harness.js');

const popup = readPluginFile('popup.js');

test('popup and content scripts never assign innerHTML', () => {
  for (const file of ['popup.js', 'content.js', 'background.js']) {
    assert.doesNotMatch(readPluginFile(file), /\.innerHTML\s*=/, file);
  }
});

test('every tabs.sendMessage callback in the popup checks runtime.lastError', () => {
  const pattern = /chrome\.tabs\.sendMessage\([^]*?function\s*\(response\)\s*\{([^]*?)\n\s{6}\}\);/g;
  const callbacks = [...popup.matchAll(pattern)];
  assert.ok(callbacks.length >= 1, 'found tabs.sendMessage callbacks');
  for (const [, body] of callbacks) {
    assert.match(body, /chrome\.runtime\.lastError/);
  }
});

test('every tabs.query callback in the popup checks for a missing tab', () => {
  const callbacks = [...popup.matchAll(/chrome\.tabs\.query\([^)]*\)[^{]*\{([^]{0,200})/g)];
  assert.ok(callbacks.length >= 1);
  for (const [, start] of callbacks) {
    assert.match(start, /!tabs\[0\]/);
  }
});
