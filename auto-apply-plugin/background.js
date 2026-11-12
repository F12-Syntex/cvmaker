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

// Handle context menu clicks
chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (info.menuItemId === "ai-fill-element") {
    try {
      // Get stored configuration
      const result = await chrome.storage.local.get(['openaiApiKey', 'userData']);
      
      if (!result.openaiApiKey || !result.userData) {
        // Show popup to configure if not set up
        chrome.action.openPopup();
        return;
      }
      
      // Send message to content script to fill fields in the clicked element
      chrome.tabs.sendMessage(tab.id, {
        action: "fillElementWithAI",
        apiKey: result.openaiApiKey,
        userData: result.userData,
        clickX: info.pageX || 0,
        clickY: info.pageY || 0
      });
      
    } catch (error) {
      console.error('Error handling context menu click:', error);
    }
  }
});

// Handle messages from content script and popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "showNotification") {
    // Create a notification for the result
    chrome.notifications.create({
      type: 'basic',
      iconUrl: 'icon.png',
      title: 'AI Auto Applier',
      message: request.message
    });
  } else if (request.action === "downloadFile") {
    chrome.downloads.download({
      url: request.url,
      filename: request.filename,
      saveAs: true
    });
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
          });
        }, 3000);
      }
    });
  }
});

// Listen for tab updates to know when LinkedIn has loaded
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete' && tab.url && tab.url.includes('linkedin.com/jobs/search')) {
    // Notify popup that LinkedIn has loaded
    chrome.runtime.sendMessage({
      action: "linkedinLoaded",
      tabId: tabId
    });
  }
});