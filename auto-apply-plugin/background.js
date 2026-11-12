// Pure helpers shared with the content script and the tests.
importScripts('lib/fields.js');

// Security model:
// - The OpenAI API key and the user's profile are read here, in the service worker, and are
//   never sent to a web page or a content script. Content scripts send field descriptions and
//   get back only the generated text for one field.
// - Content scripts are not declared in the manifest. They are injected on demand into the tab
//   the user acted on (activeTab) or into the LinkedIn search tab (host permission).
// - storage.local is limited to extension pages and this worker, so a content script could not
//   read the key even if it tried.
if (chrome.storage.local.setAccessLevel) {
  chrome.storage.local.setAccessLevel({ accessLevel: 'TRUSTED_CONTEXTS' })
    .catch(error => console.error('Could not restrict storage access:', error));
}

const OPENAI_URL = 'https://api.openai.com/v1/chat/completions';

// Create context menu when extension is installed or updated
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.removeAll(() => {
    chrome.contextMenus.create({
      id: "ai-fill-element",
      title: "🤖 AI Fill This Field",
      // Right-clicking a form field focuses it, which is how the injected content script
      // finds it: the script is not on the page before the click.
      contexts: ["editable"]
    });
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

// Injects the content script (and the helpers it needs) into a tab. Safe to call repeatedly:
// content.js ignores a second injection.
function injectContentScript(tabId, frameIds) {
  const target = { tabId };
  if (Array.isArray(frameIds)) target.frameIds = frameIds;
  return chrome.scripting.executeScript({ target, files: AutoApplyLib.CONTENT_SCRIPT_FILES });
}

async function getAiSettings() {
  const stored = await chrome.storage.local.get(['openaiApiKey', 'userData', 'openaiModel']);
  return {
    apiKey: stored.openaiApiKey || '',
    userData: stored.userData || '',
    model: AutoApplyLib.normalizeModel(stored.openaiModel)
  };
}

async function callOpenAI(prompt) {
  const settings = await getAiSettings();
  if (!settings.apiKey) throw new Error('No OpenAI API key saved. Open the extension popup to add one.');

  const response = await fetch(OPENAI_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${settings.apiKey}`
    },
    body: JSON.stringify(AutoApplyLib.buildChatCompletionRequest(settings.model, prompt))
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const detail = errorData && errorData.error && errorData.error.message ? errorData.error.message : 'Unknown error';
    throw new Error(`OpenAI API error: ${response.status} - ${detail}`);
  }
  return AutoApplyLib.parseChatCompletion(await response.json());
}

// Content script asks for the value of one text field.
async function completeField(request) {
  const { userData } = await getAiSettings();
  const prompt = AutoApplyLib.buildFieldPrompt(userData, request.field, request.jobContext);
  return { success: true, text: AutoApplyLib.cleanAIResponse(await callOpenAI(prompt)) };
}

// Content script asks which option of a <select> to choose.
async function chooseOption(request) {
  const options = AutoApplyLib.sanitizeOptions(request.options);
  if (options.length === 0) return { success: true, text: '' };
  const { userData } = await getAiSettings();
  const field = request.field || {};
  const prompt = AutoApplyLib.buildSelectPrompt(userData, options, field.context, request.jobContext);
  return { success: true, text: AutoApplyLib.cleanAIResponse(await callOpenAI(prompt)) };
}

// Handle context menu clicks
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId !== "ai-fill-element" || !tab) return;
  try {
    const settings = await getAiSettings();
    if (!settings.apiKey || !settings.userData) {
      showNotification('Open the extension popup and save your API key and profile first.');
      // openPopup is not available in every Chrome version and needs a user gesture.
      if (chrome.action.openPopup) chrome.action.openPopup().catch(() => {});
      return;
    }

    // The click granted activeTab for this tab; inject into the frame that was clicked.
    const frameId = typeof info.frameId === 'number' ? info.frameId : 0;
    await injectContentScript(tab.id, [frameId]);
    // No key, no profile and no coordinates: the content script fills the focused field and
    // asks this worker for each value.
    await chrome.tabs.sendMessage(tab.id, { action: "fillElementWithAI" }, { frameId });
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
    // Allowed by the www.linkedin.com host permission.
    await injectContentScript(tabId);
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
      // Error text may reach a page's content script; never let a key fragment through.
      sendResponse({ success: false, error: AutoApplyLib.redactSecrets(error.message) });
    });
  return true; // keep the message channel open for the async response
}

// Handle messages from content script and popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (!request || sender.id !== chrome.runtime.id) return undefined;

  switch (request.action) {
    case "aiFillField":
    case "aiChooseOption":
      // Only the injected content script asks for completions; it runs in a tab.
      if (!sender.tab) {
        sendResponse({ success: false, error: 'AI requests must come from a page' });
        return undefined;
      }
      return respond(request.action === "aiFillField" ? completeField(request) : chooseOption(request), sendResponse);
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
