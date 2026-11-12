'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const { PLUGIN_DIR, readPluginFile, loadBackground } = require('./harness.js');

const manifest = JSON.parse(readPluginFile('manifest.json'));

function exists(relativePath) {
  return fs.existsSync(path.join(PLUGIN_DIR, relativePath));
}

test('manifest.json is Manifest V3', () => {
  assert.equal(manifest.manifest_version, 3);
});

test('every file the manifest names exists', () => {
  const files = [
    manifest.background.service_worker,
    manifest.action.default_popup,
    ...Object.values(manifest.icons || {}),
    ...Object.values((manifest.action && manifest.action.default_icon) || {}),
    ...(manifest.content_scripts || []).flatMap(entry => entry.js || [])
  ];
  for (const file of files) {
    assert.ok(exists(file), `${file} is referenced by manifest.json but missing`);
  }
});

test('scripts loaded by popup.html exist', () => {
  const html = readPluginFile('popup.html');
  const sources = [...html.matchAll(/<script[^>]*src="([^"]+)"/g)].map(m => m[1]);
  assert.ok(sources.includes('popup.js'));
  for (const src of sources) assert.ok(exists(src), `${src} missing`);
});

test('notifications use an icon that exists', async () => {
  const { chrome, send } = loadBackground();
  await send({ action: 'showNotification', message: 'hello' });
  assert.equal(chrome.calls.notifications.length, 1);
  const { iconUrl, type, message } = chrome.calls.notifications[0];
  assert.equal(type, 'basic');
  assert.equal(message, 'hello');
  assert.ok(iconUrl && exists(iconUrl), `notification icon ${iconUrl} missing`);
});
