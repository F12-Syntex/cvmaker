'use strict';

// LinkedIn job search: started by the background when the tab has loaded, stoppable, and
// downloads without leaking blob URLs.
const test = require('node:test');
const assert = require('node:assert/strict');

const { loadBackground, loadContent, createDocument, createElement, flush } = require('./harness.js');
const {
  normalizeSearchRequest,
  shouldStartScraping,
  buildJobsFilename,
  textToDataUrl
} = require('../lib/fields.js');

// ---- pure helpers ----

test('normalizeSearchRequest fills defaults and clamps pages', () => {
  assert.deepEqual(normalizeSearchRequest(undefined), {
    jobTitle: 'Software Engineer', location: '', experienceLevel: '', maxPages: 5
  });
  assert.deepEqual(
    normalizeSearchRequest({ maxPages: '99', params: { jobTitle: '  Dev ', location: 'Leeds', experienceLevel: 'mid' } }),
    { jobTitle: 'Dev', location: 'Leeds', experienceLevel: 'mid', maxPages: 25 }
  );
  assert.equal(normalizeSearchRequest({ maxPages: 0 }).maxPages, 1);
  assert.equal(normalizeSearchRequest({ maxPages: 'abc' }).maxPages, 5);
  assert.equal(normalizeSearchRequest({ params: { jobTitle: 42 } }).jobTitle, 'Software Engineer');
});

test('shouldStartScraping only fires once, for the search tab, when loading completes', () => {
  const search = { tabId: 3, started: false };
  assert.equal(shouldStartScraping(search, 3, { status: 'complete' }), true);
  assert.equal(shouldStartScraping(search, 3, { status: 'loading' }), false);
  assert.equal(shouldStartScraping(search, 4, { status: 'complete' }), false);
  assert.equal(shouldStartScraping({ ...search, started: true }, 3, { status: 'complete' }), false);
  assert.equal(shouldStartScraping(null, 3, { status: 'complete' }), false);
});

test('buildJobsFilename is safe for missing or odd titles', () => {
  const day = new Date('2026-03-04T12:00:00Z');
  assert.equal(buildJobsFilename('C++ / Rust dev', day), 'linkedin_jobs_C_Rust_dev_2026-03-04.txt');
  assert.equal(buildJobsFilename(undefined, day), 'linkedin_jobs_search_2026-03-04.txt');
  assert.equal(buildJobsFilename('../../etc', day), 'linkedin_jobs_etc_2026-03-04.txt');
  assert.ok(buildJobsFilename('x'.repeat(300), day).length < 100);
});

test('textToDataUrl round-trips UTF-8 text', () => {
  const text = 'Café – 東京\nline 2';
  const url = textToDataUrl(text);
  assert.match(url, /^data:text\/plain;charset=utf-8;base64,/);
  const decoded = Buffer.from(url.split(',')[1], 'base64').toString('utf8');
  assert.equal(decoded, text);
});

// ---- background ----

async function startSearch(options) {
  const background = loadBackground(options);
  const response = await background.send({
    action: 'startJobSearch', maxPages: 3, params: { jobTitle: 'Dev', location: 'Leeds', experienceLevel: 'entry' }
  });
  return { ...background, response };
}

function scrapeMessages(chrome) {
  return chrome.calls.tabMessages.filter(m => m.message.action === 'startLinkedInScraping');
}

test('starting a search opens LinkedIn and waits for the tab to load', async () => {
  const { chrome, response } = await startSearch();
  assert.equal(response.success, true);
  assert.equal(chrome.calls.tabsCreated.length, 1);
  const tab = chrome.calls.tabsCreated[0];
  assert.equal(response.tabId, tab.id);
  assert.match(tab.url, /^https:\/\/www\.linkedin\.com\/jobs\/search\/\?keywords=Dev&location=Leeds&f_E=2/);
  assert.deepEqual(
    { ...chrome.storage.session.data.jobSearch, createdAt: 0 },
    { jobTitle: 'Dev', location: 'Leeds', experienceLevel: 'entry', maxPages: 3, tabId: tab.id, started: false, createdAt: 0 }
  );
  assert.equal(scrapeMessages(chrome).length, 0, 'no scraping before the page has loaded');
});

test('scraping starts from the background exactly once when the search tab completes', async () => {
  const { chrome, response } = await startSearch();
  const tabId = response.tabId;

  chrome.tabs.onUpdated.fire(tabId, { status: 'loading' }, { id: tabId });
  chrome.tabs.onUpdated.fire(tabId + 1, { status: 'complete' }, { id: tabId + 1 });
  await flush();
  assert.equal(scrapeMessages(chrome).length, 0);

  chrome.tabs.onUpdated.fire(tabId, { status: 'complete' }, { id: tabId });
  await flush();
  chrome.tabs.onUpdated.fire(tabId, { status: 'complete' }, { id: tabId });
  await flush();

  const sent = scrapeMessages(chrome);
  assert.equal(sent.length, 1);
  assert.equal(sent[0].tabId, tabId);
  assert.deepEqual({ ...sent[0].message }, { action: 'startLinkedInScraping', maxPages: 3, jobTitle: 'Dev', location: 'Leeds' });
  assert.equal(chrome.storage.session.data.jobSearch.started, true);
});

test('a tab that already finished loading starts at once', async () => {
  const { chrome } = await startSearch({ tabStatus: 'complete' });
  await flush();
  assert.equal(scrapeMessages(chrome).length, 1);
});

test('a failed start clears the search and tells the popup', async () => {
  const { chrome, response } = await startSearch({
    tabSendMessage: () => { throw new Error('Receiving end does not exist'); }
  });
  chrome.tabs.onUpdated.fire(response.tabId, { status: 'complete' }, { id: response.tabId });
  await flush();
  assert.equal(chrome.storage.session.data.jobSearch, undefined);
  const notice = chrome.calls.runtimeMessages.find(r => r.message.action === 'jobSearchComplete');
  assert.equal(notice.message.success, false);
});

test('stop tells the search tab to stop and clears the search', async () => {
  const { chrome, response, send } = await startSearch();
  const stopped = await send({ action: 'stopJobSearch' });
  assert.equal(stopped.success, true);
  const stop = chrome.calls.tabMessages.find(m => m.message.action === 'stopLinkedInScraping');
  assert.ok(stop, 'stop message sent');
  assert.equal(stop.tabId, response.tabId);
  assert.equal(chrome.storage.session.data.jobSearch, undefined);

  // A late "complete" for the old tab does not start anything.
  chrome.tabs.onUpdated.fire(response.tabId, { status: 'complete' }, { id: response.tabId });
  await flush();
  assert.equal(scrapeMessages(chrome).length, 0);
});

test('getJobSearchState reports the running search for a reopened popup', async () => {
  const { send, response } = await startSearch();
  const state = await send({ action: 'getJobSearchState' });
  assert.equal(state.search.tabId, response.tabId);
});

test('completion from the search tab, or closing it, clears the search', async () => {
  const first = await startSearch();
  await first.send({ action: 'jobSearchComplete', success: true }, { id: first.chrome.runtime.id, tab: { id: first.response.tabId } });
  await flush();
  assert.equal(first.chrome.storage.session.data.jobSearch, undefined);

  const second = await startSearch();
  second.chrome.tabs.onRemoved.fire(second.response.tabId, {});
  await flush();
  assert.equal(second.chrome.storage.session.data.jobSearch, undefined);
  const notice = second.chrome.calls.runtimeMessages.find(r => r.message.action === 'jobSearchComplete');
  assert.equal(notice.message.success, false);
});

test('downloadJobs downloads a data URL with a safe file name', async () => {
  const { chrome, send } = loadBackground();
  const result = await send({ action: 'downloadJobs', content: 'Jobs: 1\nCafé', jobTitle: '../Dev' });
  assert.equal(result.success, true);
  const download = chrome.calls.downloads[0];
  assert.match(download.filename, /^linkedin_jobs_Dev_\d{4}-\d{2}-\d{2}\.txt$/);
  assert.equal(download.saveAs, true);
  assert.equal(Buffer.from(download.url.split(',')[1], 'base64').toString('utf8'), 'Jobs: 1\nCafé');
});

// ---- content script ----

function immediateTimers() {
  return { setTimeout: (fn) => setImmediate(fn) };
}

function jobCard(title, company, id) {
  const card = createElement({ tagName: 'LI', className: 'job-search-card' });
  const parts = {
    '.base-search-card__title a': { textContent: title },
    '.base-search-card__subtitle a': { textContent: company },
    '.job-search-card__location': { textContent: 'Leeds' },
    'a[href*="/jobs/view/"]': { href: `https://www.linkedin.com/jobs/view/${id}/` },
    'time': { textContent: '1 day ago' }
  };
  card.querySelector = (selector) => parts[selector] || null;
  return card;
}

function linkedInPage(cards) {
  const document = createDocument();
  document.querySelectorAll = (selector) => (selector === '.job-search-card' ? cards : []);
  document.querySelector = () => null; // no "next page" button
  return document;
}

function contentWithRuntime(document) {
  return loadContent({
    document,
    globals: immediateTimers(),
    runtimeSendMessage: (message) => Promise.resolve(message.action === 'downloadJobs' ? { success: true } : undefined)
  });
}

// Scraping reports its end with a jobSearchComplete message, not with the start reply.
async function waitForCompletion(content) {
  for (let i = 0; i < 200; i++) {
    const done = content.chrome.calls.runtimeMessages.find(r => r.message.action === 'jobSearchComplete');
    if (done) return done.message;
    await flush(1);
  }
  throw new Error('scraping did not complete');
}

test('content: scraping collects jobs and asks the background to download them', async () => {
  const content = contentWithRuntime(linkedInPage([jobCard('Dev', 'Acme', 11), jobCard('QA', 'Beta', 12)]));
  const result = await content.send({ action: 'startLinkedInScraping', maxPages: 3, jobTitle: 'Dev', location: 'Leeds' });
  assert.equal(result.success, true);
  await waitForCompletion(content);

  const messages = content.chrome.calls.runtimeMessages.map(r => r.message);
  const download = messages.find(m => m.action === 'downloadJobs');
  assert.ok(download, 'download requested');
  assert.equal(download.jobTitle, 'Dev');
  assert.match(download.content, /Total Jobs Found: 2/);
  assert.match(download.content, /Job ID: 11/);
  assert.equal('url' in download, false, 'no blob URL is created');

  const done = messages.find(m => m.action === 'jobSearchComplete');
  assert.equal(done.success, true);
  assert.equal(done.totalJobs, 2);
});

test('content: missing search parameters do not crash the download', async () => {
  const content = contentWithRuntime(linkedInPage([]));
  await content.send({ action: 'startLinkedInScraping' });
  await waitForCompletion(content);
  const messages = content.chrome.calls.runtimeMessages.map(r => r.message);
  const done = messages.find(m => m.action === 'jobSearchComplete');
  assert.equal(done.success, true, done.error);
  assert.ok(messages.find(m => m.action === 'downloadJobs'));
});

test('content: stop ends the scraping loop', async () => {
  const content = contentWithRuntime(linkedInPage([]));
  const started = await content.send({ action: 'startLinkedInScraping', maxPages: 5, jobTitle: 'Dev' });
  assert.equal(started.success, true);
  // A second start while running is refused.
  const second = await content.send({ action: 'startLinkedInScraping', maxPages: 5, jobTitle: 'Dev' });
  assert.equal(second.success, false);
  const stopReply = await content.send({ action: 'stopLinkedInScraping' });
  assert.equal(stopReply.success, true);
  assert.equal(stopReply.wasSearching, true);
  await waitForCompletion(content);

  const messages = content.chrome.calls.runtimeMessages.map(r => r.message);
  const progress = messages.filter(m => m.action === 'jobSearchProgress');
  assert.equal(progress.length, 1, 'stopped after the first page');
  const done = messages.find(m => m.action === 'jobSearchComplete');
  assert.equal(done.stopped, true);
  assert.equal(messages.some(m => m.action === 'downloadJobs'), false, 'nothing found, nothing downloaded');

  // A new search can start after a stop.
  const again = await content.send({ action: 'startLinkedInScraping', maxPages: 1, jobTitle: 'Dev' });
  assert.equal(again.success, true);
});
