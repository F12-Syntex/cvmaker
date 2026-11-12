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

// Handle messages from content script and popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "showNotification") {
    showNotification(request.message);
  } else if (request.action === "downloadFile") {
    chrome.downloads.download({
      url: request.url,
      filename: request.filename,
      saveAs: true
    }).catch(error => console.error('Download failed:', error));
  } else if (request.action === "openLinkedInSearch") {
    // Open LinkedIn search in new tab
    const url = AutoApplyLib.buildLinkedInSearchUrl(request.params);
    chrome.tabs.create({ url: url }, (tab) => {
      // Store the search tab ID for coordination
      chrome.storage.local.set({ searchTabId: tab.id });
      
      // Send response back to popup
      sendResponse({ success: true, tabId: tab.id });
    });
    return true; // Keep message channel open
  } else if (request.action === "startJobScraping") {
    // Start the job scraping process
    chrome.storage.local.get(['searchTabId'], (result) => {
      if (result.searchTabId) {
        // Wait a moment for LinkedIn to load, then start scraping
        setTimeout(() => {
          chrome.tabs.sendMessage(result.searchTabId, {
            action: "startLinkedInScraping",
            maxPages: request.maxPages,
            jobTitle: request.jobTitle,
            location: request.location
          }).catch(error => console.error('Could not start scraping:', error));
        }, 3000);
      }
    });
  }
});

// Listen for tab updates to know when LinkedIn has loaded
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url && tab.url.includes('linkedin.com/jobs/search')) {
    // Notify popup that LinkedIn has loaded
    notifyExtension({
      action: "linkedinLoaded",
      tabId: tabId
    });
  }
});