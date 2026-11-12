document.addEventListener('DOMContentLoaded', function() {
  // Tab management
  const navTabs = document.querySelectorAll('.nav-tab');
  const tabContents = document.querySelectorAll('.tab-content');
  
  navTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      const targetTab = tab.dataset.tab;
      
      // Update active tab
      navTabs.forEach(t => t.classList.remove('active'));
      tabContents.forEach(c => c.classList.remove('active'));
      
      tab.classList.add('active');
      document.getElementById(targetTab).classList.add('active');
    });
  });

  // Elements
  const fillBtn = document.getElementById('fillForm');
  const clearBtn = document.getElementById('clearForm');
  const saveBtn = document.getElementById('saveConfig');
  const searchBtn = document.getElementById('searchJobs');
  const analyzeBtn = document.getElementById('analyzeForm');
  
  const configStatus = document.getElementById('configStatus');
  const searchStatus = document.getElementById('searchStatus');
  const applyStatus = document.getElementById('applyStatus');
  
  const apiKeyInput = document.getElementById('apiKey');
  const modelInput = document.getElementById('model');
  const userDataInput = document.getElementById('userData');
  const jobTitleInput = document.getElementById('jobTitle');
  const locationInput = document.getElementById('location');
  const experienceLevelSelect = document.getElementById('experienceLevel');
  const maxPagesInput = document.getElementById('maxPages');
  
  const apiIndicator = document.getElementById('apiIndicator');
  const dataIndicator = document.getElementById('dataIndicator');
  const fillSpinner = document.getElementById('fillSpinner');
  const searchSpinner = document.getElementById('searchSpinner');
  const searchProgress = document.getElementById('searchProgress');
  const progressFill = document.getElementById('progressFill');
  const jobStats = document.getElementById('jobStats');
  const jobsFound = document.getElementById('jobsFound');
  const pagesScraped = document.getElementById('pagesScraped');
  const searchLogs = document.getElementById('searchLogs');

  let currentSearchTabId = null;
  let searchInProgress = false;

  // Load saved configuration
  chrome.storage.local.get(['openaiApiKey', 'openaiModel', 'userData', 'jobSearchSettings'], function(result) {
    if (result.openaiApiKey) {
      apiKeyInput.value = result.openaiApiKey;
    }
    modelInput.value = result.openaiModel || AutoApplyLib.DEFAULT_MODEL;
    if (result.userData) {
      userDataInput.value = result.userData;
    }
    if (result.jobSearchSettings) {
      const settings = result.jobSearchSettings;
      if (settings.jobTitle) jobTitleInput.value = settings.jobTitle;
      if (settings.location) locationInput.value = settings.location;
      if (settings.experienceLevel) experienceLevelSelect.value = settings.experienceLevel;
      if (settings.maxPages) maxPagesInput.value = settings.maxPages;
    }
    updateAllStatus();
    updateIndicators();
  });

  function updateIndicators() {
    if (apiKeyInput.value.trim() && apiKeyInput.value.trim().startsWith('sk-')) {
      apiIndicator.className = 'status-indicator status-complete';
    } else {
      apiIndicator.className = 'status-indicator status-incomplete';
    }
    
    if (userDataInput.value.trim().length > 50) {
      dataIndicator.className = 'status-indicator status-complete';
    } else {
      dataIndicator.className = 'status-indicator status-incomplete';
    }
  }

  function updateAllStatus() {
    const hasApiKey = apiKeyInput.value.trim() && apiKeyInput.value.trim().startsWith('sk-');
    const hasUserData = userDataInput.value.trim().length > 50;
    
    // Config status
    if (hasApiKey && hasUserData) {
      configStatus.textContent = '✅ Configuration complete! Ready to use all features.';
      configStatus.className = 'status success';
      fillBtn.disabled = false;
      searchBtn.disabled = false;
    } else {
      let missing = [];
      if (!hasApiKey) missing.push('API key');
      if (!hasUserData) missing.push('user data');
      
      configStatus.textContent = `⚠️ Please configure: ${missing.join(' and ')}`;
      configStatus.className = 'status warning';
      fillBtn.disabled = true;
      searchBtn.disabled = true;
    }
    
    // Apply status
    if (hasApiKey && hasUserData) {
      applyStatus.textContent = '🎯 Ready to fill forms with AI intelligence!';
      applyStatus.className = 'status success';
    } else {
      applyStatus.textContent = '⚙️ Configure your settings in the Config tab first.';
      applyStatus.className = 'status info';
    }
  }

  function setLoading(button, isLoading, message, statusElement) {
    if (isLoading) {
      button.disabled = true;
      const spinner = button.querySelector('.spinner');
      if (spinner) spinner.style.display = 'inline-block';
      
      if (statusElement && message) {
        statusElement.textContent = message;
        statusElement.className = 'status info';
        statusElement.style.display = 'block';
      }
    } else {
      button.disabled = false;
      const spinner = button.querySelector('.spinner');
      if (spinner) spinner.style.display = 'none';
    }
  }

  function addLog(message) {
    const timestamp = new Date().toLocaleTimeString();
    const logEntry = document.createElement('div');
    logEntry.className = 'log-entry';
    // textContent only: messages can contain page text and error strings.
    const stamp = document.createElement('span');
    stamp.className = 'log-timestamp';
    stamp.textContent = `[${timestamp}]`;
    logEntry.appendChild(stamp);
    logEntry.appendChild(document.createTextNode(` ${message}`));
    searchLogs.appendChild(logEntry);
    searchLogs.scrollTop = searchLogs.scrollHeight;
  }

  // Save configuration
  saveBtn.addEventListener('click', function() {
    const apiKey = apiKeyInput.value.trim();
    const userData = userDataInput.value.trim();
    
    if (!apiKey || !apiKey.startsWith('sk-')) {
      configStatus.textContent = '❌ Please enter a valid OpenAI API key (starts with sk-)';
      configStatus.className = 'status error';
      return;
    }
    
    if (!userData || userData.length < 50) {
      configStatus.textContent = '❌ Please provide more detailed user information (at least 50 characters)';
      configStatus.className = 'status error';
      return;
    }

    const model = modelInput.value.trim() || AutoApplyLib.DEFAULT_MODEL;
    if (AutoApplyLib.normalizeModel(model) !== model) {
      configStatus.textContent = '❌ Please enter a valid model name, for example gpt-4.1-mini';
      configStatus.className = 'status error';
      return;
    }
    
    setLoading(saveBtn, true, '💾 Saving configuration...', configStatus);
    
    const jobSearchSettings = {
      jobTitle: jobTitleInput.value.trim(),
      location: locationInput.value.trim(),
      experienceLevel: experienceLevelSelect.value,
      maxPages: parseInt(maxPagesInput.value) || 5
    };
    
    chrome.storage.local.set({
      openaiApiKey: apiKey,
      openaiModel: model,
      userData: userData,
      jobSearchSettings: jobSearchSettings
    }, function() {
      setTimeout(() => {
        setLoading(saveBtn, false);
        configStatus.textContent = '✅ Configuration saved successfully!';
        configStatus.className = 'status success';
        updateAllStatus();
        updateIndicators();
      }, 800);
    });
  });

  // Enhanced job search functionality
  searchBtn.addEventListener('click', function() {
    const apiKey = apiKeyInput.value.trim();
    const userData = userDataInput.value.trim();
    const jobTitle = jobTitleInput.value.trim() || 'Software Engineer';
    const location = locationInput.value.trim();
    const experienceLevel = experienceLevelSelect.value;
    const maxPages = parseInt(maxPagesInput.value) || 5;
    
    if (!apiKey || !userData) {
      searchStatus.textContent = '❌ Please configure your API key and user data first';
      searchStatus.className = 'status error';
      searchStatus.style.display = 'block';
      return;
    }

    if (searchInProgress) {
      // Stop search
      stopJobSearch();
      return;
    }
    
    // Start search
    startJobSearch(jobTitle, location, experienceLevel, maxPages);
  });

  // Shows the running state. The button stays enabled so it can be used as Stop.
  function showSearchRunning() {
    searchInProgress = true;
    searchBtn.querySelector('.button-text').textContent = '⏹️ Stop Search';
    searchBtn.classList.remove('success-btn');
    searchBtn.classList.add('danger-btn');
    searchSpinner.style.display = 'inline-block';
    searchBtn.disabled = false;
    searchProgress.style.display = 'block';
    jobStats.style.display = 'grid';
    searchLogs.style.display = 'block';
  }

  function showSearchIdle() {
    searchInProgress = false;
    searchBtn.querySelector('.button-text').textContent = '🔍 Search LinkedIn Jobs';
    searchBtn.classList.remove('danger-btn');
    searchBtn.classList.add('success-btn');
    setLoading(searchBtn, false);
    currentSearchTabId = null;
  }

  // The background owns the search: it opens LinkedIn and starts scraping once the tab has
  // loaded, so closing this popup does not stop or break it.
  function startJobSearch(jobTitle, location, experienceLevel, maxPages) {
    showSearchRunning();
    searchStatus.textContent = '🔍 Opening LinkedIn and starting search...';
    searchStatus.className = 'status info';
    searchStatus.style.display = 'block';

    // Reset counters
    jobsFound.textContent = '0';
    pagesScraped.textContent = '0';
    searchLogs.textContent = '';
    progressFill.style.width = '0%';

    addLog('🚀 Starting job search...');
    addLog(`🎯 Searching for: ${jobTitle} ${location ? `in ${location}` : ''}`);

    chrome.runtime.sendMessage({
      action: "startJobSearch",
      maxPages: maxPages,
      params: {
        jobTitle: jobTitle,
        location: location,
        experienceLevel: experienceLevel
      }
    }, function(response) {
      if (chrome.runtime.lastError || !response || !response.success) {
        const reason = chrome.runtime.lastError ? chrome.runtime.lastError.message : (response && response.error) || 'unknown error';
        addLog(`❌ Failed to open LinkedIn: ${reason}`);
        showSearchIdle();
        return;
      }
      currentSearchTabId = response.tabId;
      addLog('🌐 LinkedIn opened; scraping starts when the page has loaded. You can close this popup.');
    });
  }

  function stopJobSearch() {
    chrome.runtime.sendMessage({ action: "stopJobSearch" }, function() {
      if (chrome.runtime.lastError) {
        addLog(`❌ ${chrome.runtime.lastError.message}`);
      }
    });
    showSearchIdle();
    addLog('⏹️ Search stopped by user');
  }

  // A search may still be running from an earlier time the popup was open.
  chrome.runtime.sendMessage({ action: "getJobSearchState" }, function(response) {
    if (chrome.runtime.lastError) return;
    if (response && response.success && response.search) {
      currentSearchTabId = response.search.tabId;
      showSearchRunning();
      addLog(`🔄 Search for "${response.search.jobTitle}" is running in another tab`);
    }
  });

  function showApplyStatus(message, kind) {
    applyStatus.textContent = message;
    applyStatus.className = `status ${kind}`;
  }

  // Injects the content script into the active tab (allowed by activeTab, which opening the
  // popup grants) and sends it one message. The message never carries the API key or the
  // profile: the content script asks the background for each AI answer.
  async function sendToActiveTab(message) {
    let tabs;
    try {
      tabs = await chrome.tabs.query({ active: true, currentWindow: true });
    } catch (error) {
      tabs = null;
    }
    if (!tabs || !tabs[0]) throw new Error('❌ Unable to access current tab');
    try {
      await chrome.scripting.executeScript({
        target: { tabId: tabs[0].id },
        files: AutoApplyLib.CONTENT_SCRIPT_FILES
      });
      return await chrome.tabs.sendMessage(tabs[0].id, message);
    } catch (error) {
      // chrome:// pages, the Web Store and PDF viewers cannot be scripted.
      throw new Error('❌ Cannot access this page. Try refreshing and reopening the extension.');
    }
  }

  // Fill current form
  fillBtn.addEventListener('click', async function() {
    setLoading(fillBtn, true, '🤖 AI is analyzing and filling the form...', applyStatus);
    try {
      const response = await sendToActiveTab({ action: "fillFormWithAI" });
      if (response && response.success) {
        if (response.fieldsCount > 0) {
          showApplyStatus(`🎉 Success! AI filled ${response.fieldsCount} fields intelligently`, 'success');
        } else if (response.errors && response.errors.length > 0) {
          showApplyStatus(`❌ ${response.errors[0]}`, 'error');
        } else {
          showApplyStatus('📄 No fillable form fields found on this page', 'info');
        }
      } else {
        showApplyStatus(`❌ Error: ${(response && response.error) || 'Unknown error occurred'}`, 'error');
      }
    } catch (error) {
      showApplyStatus(error.message, 'error');
    } finally {
      setLoading(fillBtn, false);
    }
  });

  // Clear form
  clearBtn.addEventListener('click', async function() {
    setLoading(clearBtn, true, '🗑️ Clearing all form fields...', applyStatus);
    try {
      const response = await sendToActiveTab({ action: "clearForm" });
      if (response && response.success) {
        if (response.fieldsCount > 0) {
          showApplyStatus(`✅ Cleared ${response.fieldsCount} fields`, 'success');
        } else {
          showApplyStatus('📄 No form fields found to clear', 'info');
        }
      } else {
        showApplyStatus('❌ Failed to clear form', 'error');
      }
    } catch (error) {
      showApplyStatus(error.message, 'error');
    } finally {
      setLoading(clearBtn, false);
    }
  });

  // Analyze form
  analyzeBtn.addEventListener('click', async function() {
    setLoading(analyzeBtn, true, '📊 Analyzing current page structure...', applyStatus);
    try {
      const response = await sendToActiveTab({ action: "analyzePage" });
      if (response && response.success) {
        showApplyStatus(`📊 Found ${response.inputFields} input fields, ${response.forms} forms, ${response.buttons} buttons`, 'info');
      } else {
        showApplyStatus('❌ Could not analyze page', 'error');
      }
    } catch (error) {
      showApplyStatus(error.message, 'error');
    } finally {
      setLoading(analyzeBtn, false);
    }
  });

  // Listen for messages from background script and content script
  chrome.runtime.onMessage.addListener(function(request, sender, sendResponse) {
    if (request.action === "jobSearchProgress") {
      const progress = (request.currentPage / request.totalPages) * 100;
      progressFill.style.width = `${progress}%`;
      pagesScraped.textContent = request.currentPage;
      jobsFound.textContent = request.jobsFound;
      
      addLog(`📄 Scraped page ${request.currentPage}/${request.totalPages} - Found ${request.jobsFound} jobs total`);
    } else if (request.action === "jobSearchComplete") {
      showSearchIdle();
      progressFill.style.width = '100%';
      searchStatus.style.display = 'block';

      if (request.success) {
        const saved = request.totalJobs > 0 ? ' and saved to downloads' : '';
        const verb = request.stopped ? 'Search stopped.' : 'Search complete!';
        searchStatus.textContent = `🎉 ${verb} Found ${request.totalJobs} jobs${saved}`;
        searchStatus.className = 'status success';
        addLog(`✅ ${verb} ${request.totalJobs} jobs${saved}`);
      } else {
        searchStatus.textContent = `❌ Search failed: ${request.error}`;
        searchStatus.className = 'status error';
        addLog(`❌ Search failed: ${request.error}`);
      }
    } else if (request.action === "linkedinLoaded") {
      addLog('✅ LinkedIn page loaded, starting job extraction...');
    }
  });

  // Update indicators and status when inputs change
  apiKeyInput.addEventListener('input', function() {
    updateIndicators();
    updateAllStatus();
  });
  
  userDataInput.addEventListener('input', function() {
    updateIndicators();
    updateAllStatus();
  });

  // Feature cards click handlers
  document.getElementById('fillCurrentForm').addEventListener('click', () => {
    fillBtn.click();
  });

  document.getElementById('fillElementBtn').addEventListener('click', () => {
    applyStatus.textContent = '👆 Right-click a form field and select "AI Fill This Field" from the context menu';
    applyStatus.className = 'status info';
  });
});