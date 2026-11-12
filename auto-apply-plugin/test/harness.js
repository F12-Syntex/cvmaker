'use strict';

// A small fake of the chrome.* APIs and of the DOM, enough to load background.js and content.js
// in a node:vm context and drive them through their message listeners. Not a test file itself.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const PLUGIN_DIR = path.join(__dirname, '..');

function readPluginFile(relativePath) {
  return fs.readFileSync(path.join(PLUGIN_DIR, relativePath), 'utf8');
}

function createEvent() {
  const listeners = [];
  return {
    listeners,
    addListener(fn) { listeners.push(fn); },
    removeListener(fn) {
      const index = listeners.indexOf(fn);
      if (index >= 0) listeners.splice(index, 1);
    },
    hasListener(fn) { return listeners.includes(fn); },
    fire(...args) { return listeners.map(fn => fn(...args)); }
  };
}

function pick(data, keys) {
  if (keys === undefined || keys === null) return { ...data };
  const list = Array.isArray(keys) ? keys : (typeof keys === 'string' ? [keys] : Object.keys(keys));
  const result = {};
  for (const key of list) {
    if (Object.prototype.hasOwnProperty.call(data, key)) result[key] = data[key];
    else if (keys && typeof keys === 'object' && !Array.isArray(keys)) result[key] = keys[key];
  }
  return result;
}

function createStorageArea(initial) {
  const area = {
    data: { ...(initial || {}) },
    accessLevel: null,
    get(keys, callback) {
      const result = pick(area.data, keys);
      if (callback) { callback(result); return undefined; }
      return Promise.resolve(result);
    },
    set(items, callback) {
      Object.assign(area.data, items);
      if (callback) { callback(); return undefined; }
      return Promise.resolve();
    },
    remove(keys, callback) {
      for (const key of [].concat(keys)) delete area.data[key];
      if (callback) { callback(); return undefined; }
      return Promise.resolve();
    },
    setAccessLevel(options) {
      area.accessLevel = options.accessLevel;
      return Promise.resolve();
    }
  };
  return area;
}

// A promise-like that records whether the caller attached a rejection handler, so tests can
// check that "no receiver" errors from runtime.sendMessage are not left unhandled.
function trackedRejection(error, record) {
  record.handled = false;
  const rejected = () => Promise.reject(error);
  return {
    then(onFulfilled, onRejected) {
      if (onRejected) record.handled = true;
      return rejected().then(onFulfilled, onRejected);
    },
    catch(onRejected) {
      record.handled = true;
      return rejected().catch(onRejected);
    },
    finally(onFinally) {
      return rejected().finally(onFinally);
    }
  };
}

function createChrome(options) {
  const opts = options || {};
  let nextTabId = 100;
  const calls = {
    runtimeMessages: [],
    tabMessages: [],
    tabsCreated: [],
    notifications: [],
    downloads: [],
    executeScript: [],
    contextMenus: [],
    openPopup: 0
  };

  const chrome = {
    calls,
    runtime: {
      id: 'test-extension-id',
      lastError: undefined,
      onMessage: createEvent(),
      onInstalled: createEvent(),
      onStartup: createEvent(),
      getURL: (p) => `chrome-extension://test-extension-id/${p}`,
      // Default: nobody else is listening, which is what Chrome reports when the popup is closed.
      sendMessage(message) {
        const record = { message, handled: null };
        calls.runtimeMessages.push(record);
        if (opts.runtimeSendMessage) return opts.runtimeSendMessage(message, record);
        return trackedRejection(new Error('Could not establish connection. Receiving end does not exist.'), record);
      }
    },
    storage: {
      local: createStorageArea(opts.local),
      session: createStorageArea(opts.session)
    },
    tabs: {
      onUpdated: createEvent(),
      onRemoved: createEvent(),
      create(createProperties, callback) {
        const tab = { id: nextTabId++, url: createProperties.url, status: 'loading' };
        calls.tabsCreated.push(tab);
        if (callback) { callback(tab); return undefined; }
        return Promise.resolve(tab);
      },
      query() {
        return Promise.resolve(opts.activeTab ? [opts.activeTab] : []);
      },
      sendMessage(tabId, message, sendOptions) {
        calls.tabMessages.push({ tabId, message, options: sendOptions });
        if (opts.tabSendMessage) return Promise.resolve(opts.tabSendMessage(tabId, message, sendOptions));
        return Promise.resolve(undefined);
      }
    },
    contextMenus: {
      onClicked: createEvent(),
      create(properties) { calls.contextMenus.push(properties); }
    },
    notifications: {
      create(idOrOptions, maybeOptions) {
        const notificationOptions = typeof idOrOptions === 'string' ? maybeOptions : idOrOptions;
        calls.notifications.push(notificationOptions);
        return Promise.resolve('notification-id');
      }
    },
    downloads: {
      download(downloadOptions) {
        calls.downloads.push(downloadOptions);
        return Promise.resolve(calls.downloads.length);
      }
    },
    scripting: {
      executeScript(injection) {
        calls.executeScript.push(injection);
        return Promise.resolve([]);
      }
    },
    action: {
      openPopup() {
        calls.openPopup++;
        return Promise.reject(new Error('openPopup needs a user gesture'));
      }
    }
  };
  return chrome;
}

// Delivers a runtime message to every onMessage listener the way Chrome does: resolves with the
// first sendResponse value, or undefined when no listener keeps the channel open.
function dispatchRuntimeMessage(chrome, message, sender) {
  return new Promise((resolve) => {
    let responded = false;
    let keepOpen = false;
    const sendResponse = (value) => {
      if (!responded) {
        responded = true;
        resolve(value);
      }
    };
    for (const listener of chrome.runtime.onMessage.listeners) {
      const result = listener(message, sender || { id: chrome.runtime.id }, sendResponse);
      if (result === true) keepOpen = true;
    }
    if (!keepOpen && !responded) resolve(undefined);
  });
}

// Lets pending promise callbacks and zero-delay timers run.
function flush(times) {
  let chain = Promise.resolve();
  for (let i = 0; i < (times || 5); i++) {
    chain = chain.then(() => new Promise(resolve => setImmediate(resolve)));
  }
  return chain;
}

function createContext(globals) {
  const context = vm.createContext({
    console: { log() {}, info() {}, warn() {}, error() {} },
    URL,
    URLSearchParams,
    Blob,
    TextEncoder,
    encodeURIComponent,
    setTimeout,
    clearTimeout,
    Promise,
    ...globals
  });
  context.globalThis = context;
  context.self = context;
  return context;
}

function loadBackground(options) {
  const opts = options || {};
  const chrome = createChrome(opts);
  const context = createContext({
    chrome,
    fetch: opts.fetch || (() => Promise.reject(new Error('network is not available in tests'))),
    importScripts: (...files) => {
      for (const file of files) vm.runInContext(readPluginFile(file), context, { filename: file });
    }
  });
  vm.runInContext(readPluginFile('background.js'), context, { filename: 'background.js' });
  return { chrome, context, send: (message, sender) => dispatchRuntimeMessage(chrome, message, sender) };
}

// --- Minimal DOM for content.js ---

class FakeEvent {
  constructor(type, init) {
    this.type = type;
    this.bubbles = Boolean(init && init.bubbles);
  }
}

function createElement(props) {
  const p = props || {};
  const attributes = { ...(p.attributes || {}) };
  const element = {
    tagName: (p.tagName || 'DIV').toUpperCase(),
    type: p.type || (String(p.tagName || '').toUpperCase() === 'INPUT' ? 'text' : undefined),
    id: p.id || '',
    name: p.name || '',
    placeholder: p.placeholder || '',
    className: p.className || '',
    value: p.value || '',
    textContent: p.textContent || '',
    disabled: Boolean(p.disabled),
    readOnly: Boolean(p.readOnly),
    role: p.role || null,
    labels: p.labels || null,
    children: [],
    parentElement: null,
    previousElementSibling: null,
    nextElementSibling: null,
    isConnected: true,
    style: {},
    events: [],
    focused: 0,
    classList: { contains: (name) => String(p.className || '').split(/\s+/).includes(name) },
    getAttribute: (name) => (Object.prototype.hasOwnProperty.call(attributes, name) ? attributes[name] : null),
    setAttribute: (name, value) => { attributes[name] = String(value); },
    removeAttribute: (name) => { delete attributes[name]; },
    hasAttribute: (name) => Object.prototype.hasOwnProperty.call(attributes, name),
    focus() { element.focused++; },
    click() { element.clicked = (element.clicked || 0) + 1; },
    dispatchEvent(event) { element.events.push(event.type); return true; },
    appendChild(child) {
      child.parentElement = element;
      element.children.push(child);
      return child;
    },
    descendants() {
      return element.children.flatMap(child => [child, ...child.descendants()]);
    },
    querySelector(selector) { return element.querySelectorAll(selector)[0] || null; },
    querySelectorAll(selector) {
      return element.descendants().filter(node => matchesSimpleSelector(node, selector));
    },
    closest() { return null; }
  };
  return element;
}

// Supports the handful of selector shapes content.js uses for form fields.
function matchesSimpleSelector(node, selectorList) {
  return selectorList.split(',').map(s => s.trim()).some(selector => {
    let m = selector.match(/^input\[type="([^"]+)"\]$/);
    if (m) return node.tagName === 'INPUT' && node.type === m[1];
    m = selector.match(/^input\[role="([^"]+)"\]$/);
    if (m) return node.tagName === 'INPUT' && node.role === m[1];
    m = selector.match(/^\.([\w-]+)$/);
    if (m) return node.classList.contains(m[1]);
    if (/^[a-z0-9]+$/i.test(selector)) return node.tagName === selector.toUpperCase();
    return false;
  });
}

function createDocument() {
  const listeners = {};
  const body = createElement({ tagName: 'BODY' });
  const document = {
    title: 'Test page',
    readyState: 'complete',
    body,
    activeElement: body,
    listeners,
    addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
    removeEventListener(type, fn) {
      listeners[type] = (listeners[type] || []).filter(l => l !== fn);
    },
    fire(type, event) { (listeners[type] || []).forEach(fn => fn(event)); },
    querySelector(selector) { return document.querySelectorAll(selector)[0] || null; },
    querySelectorAll(selector) { return body.querySelectorAll(selector); },
    elementFromPoint: () => null
  };
  return document;
}

function loadContent(options) {
  const opts = options || {};
  const chrome = createChrome(opts);
  const document = opts.document || createDocument();
  const windowListeners = {};
  const window = {
    addEventListener(type, fn) { (windowListeners[type] = windowListeners[type] || []).push(fn); }
  };
  const context = createContext({
    chrome,
    document,
    window,
    Event: FakeEvent,
    fetch: opts.fetch || (() => Promise.reject(new Error('network is not available in tests'))),
    ...(opts.globals || {})
  });
  context.window = context;
  Object.assign(context, { addEventListener: window.addEventListener });
  vm.runInContext(readPluginFile('lib/fields.js'), context, { filename: 'lib/fields.js' });
  vm.runInContext(readPluginFile('content.js'), context, { filename: 'content.js' });
  return {
    chrome,
    context,
    document,
    run: (source) => vm.runInContext(source, context),
    send: (message, sender) => dispatchRuntimeMessage(chrome, message, sender)
  };
}

module.exports = {
  PLUGIN_DIR,
  readPluginFile,
  createChrome,
  createElement,
  createDocument,
  loadBackground,
  loadContent,
  dispatchRuntimeMessage,
  flush
};
