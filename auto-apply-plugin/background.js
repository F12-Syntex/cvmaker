// Pure helpers shared with the content script and the tests.
importScripts('lib/fields.js');

// Create context menu when extension is installed
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: "ai-fill-element",
    title: "🤖 AI Fill Fields in This Element",
    contexts: ["all"]
  });
});

const NOTIFICATION_ICON = 'icons/icon128.png';

function showNotification(message) {
  chrome.notifications.create({
    type: 'basic',
    iconUrl: NOTIFICATION_ICON,
    title: 'AI Auto Applier',
    message: String(message || '')
  }).catch(error => console.error('Notification failed:', error));
}

// Sends a message to the popup. The popup is usually closed, and then Chrome rejects the
// promise with "Receiving end does not exist", which is expected and not an error.
function notifyExtension(message) {
  try {
    const pending = chrome.runtime.sendMessage(message);
    if (pending && typeof pending.catch === 'function') pending.catch(() => {});
  } catch (error) {
    // Extension context is going away; nothing to tell.
  }
}

// Handle context menu clicks
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId !== "ai-fill-element" || !tab) return;
  try {
    // Get stored configuration
    const result = await chrome.storage.local.get(['openaiApiKey', 'userData']);

    if (!result.openaiApiKey || !result.userData) {
      showNotification('Open the extension popup and save your API key and profile first.');
      // openPopup is not available in every Chrome version and needs a user gesture.
      if (chrome.action.openPopup) chrome.action.openPopup().catch(() => {});
      return;
    }

    // The content script fills the element it saw the right-click on. OnClickData has no
    // click coordinates, so none are sent.
    await chrome.tabs.sendMessage(tab.id, {
      action: "fillElementWithAI",
      apiKey: result.openaiApiKey,
      userData: result.userData
    });
  } catch (error) {
    console.error('Error handling context menu click:', error);
    showNotification(`Could not fill fields on this page: ${error.message}`);
  }
});

// --- LinkedIn job search ---------------------------------------------------------------
// The search state lives in storage.session so it survives the service worker being stopped,
// and scraping is started from here when the tab has loaded, so it no longer depends on the
// popup staying open.
const SEARCH_KEY = 'jobSearch';

async function getSearch() {
  const stored = await chrome.storage.session.get(SEARCH_KEY);
  return stored[SEARCH_KEY] || null;
}

function saveSearch(search) {
  return chrome.storage.session.set({ [SEARCH_KEY]: search });
}

function clearSearch() {
  return chrome.storage.session.remove(SEARCH_KEY);
}

async function startJobSearch(request) {
  const params = AutoApplyLib.normalizeSearchRequest(request);
  const previous = await getSearch();
  if (previous) await stopJobSearch();

  const tab = await chrome.tabs.create({ url: AutoApplyLib.buildLinkedInSearchUrl(params) });
  await saveSearch({ ...params, tabId: tab.id, started: false, createdAt: Date.now() });

  // The tab may already have finished loading before the state was saved.
  const current = await chrome.tabs.get(tab.id).catch(() => null);
  if (current && current.status === 'complete') {
    await maybeStartScraping(tab.id, { status: 'complete' });
  }
  return { success: true, tabId: tab.id };
}

async function maybeStartScraping(tabId, changeInfo) {
  const search = await getSearch();
  if (!AutoApplyLib.shouldStartScraping(search, tabId, changeInfo)) return;

  await saveSearch({ ...search, started: true });
  notifyExtension({ action: "linkedinLoaded", tabId: tabId });
  try {
    await chrome.tabs.sendMessage(tabId, {
      action: "startLinkedInScraping",
      maxPages: search.maxPages,
      jobTitle: search.jobTitle,
      location: search.location
    });
  } catch (error) {
    console.error('Could not start scraping:', error);
    await clearSearch();
    notifyExtension({ action: "jobSearchComplete", success: false, error: `Could not start scraping: ${error.message}` });
  }
}

async function stopJobSearch() {
  const search = await getSearch();
  await clearSearch();
  if (search && search.tabId !== undefined) {
    await chrome.tabs.sendMessage(search.tabId, { action: "stopLinkedInScraping" }).catch(() => {});
  }
  return { success: true, stopped: Boolean(search) };
}

async function downloadJobs(request) {
  const content = typeof request.content === 'string' ? request.content : '';
  const downloadId = await chrome.downloads.download({
    url: AutoApplyLib.textToDataUrl(content),
    filename: AutoApplyLib.buildJobsFilename(request.jobTitle),
    saveAs: true
  });
  return { success: true, downloadId };
}

function respond(promise, sendResponse) {
  promise
    .then(result => sendResponse(result))
    .catch(error => {
      console.error(error);
      sendResponse({ success: false, error: error.message });
    });
  return true; // keep the message channel open for the async response
}

// Handle messages from content script and popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (!request || sender.id !== chrome.runtime.id) return undefined;

  switch (request.action) {
    case "showNotification":
      showNotification(request.message);
      return undefined;
    case "downloadJobs":
      return respond(downloadJobs(request), sendResponse);
    case "startJobSearch":
      return respond(startJobSearch(request), sendResponse);
    case "stopJobSearch":
      return respond(stopJobSearch(), sendResponse);
    case "getJobSearchState":
      return respond(getSearch().then(search => ({ success: true, search })), sendResponse);
    case "jobSearchComplete":
      // Sent by the content script in the search tab; the popup gets it too.
      if (sender.tab) {
        getSearch().then(search => {
          if (search && search.tabId === sender.tab.id) return clearSearch();
          return undefined;
        }).catch(error => console.error(error));
      }
      return undefined;
    default:
      return undefined;
  }
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status !== 'complete') return;
  maybeStartScraping(tabId, changeInfo).catch(error => console.error(error));
});

chrome.tabs.onRemoved.addListener((tabId) => {
  getSearch().then(async (search) => {
    if (search && search.tabId === tabId) {
      await clearSearch();
      notifyExtension({ action: "jobSearchComplete", success: false, error: 'The LinkedIn tab was closed' });
    }
  }).catch(error => console.error(error));
});
