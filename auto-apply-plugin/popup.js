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
  chrome.storage.local.get(['openaiApiKey', 'userData', 'jobSearchSettings'], function(result) {
    if (result.openaiApiKey) {
      apiKeyInput.value = result.openaiApiKey;
    }
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
    logEntry.innerHTML = `<span class="log-timestamp">[${timestamp}]</span> ${message}`;
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
    
    setLoading(saveBtn, true, '💾 Saving configuration...', configStatus);
    
    const jobSearchSettings = {
      jobTitle: jobTitleInput.value.trim(),
      location: locationInput.value.trim(),
      experienceLevel: experienceLevelSelect.value,
      maxPages: parseInt(maxPagesInput.value) || 5
    };
    
    chrome.storage.local.set({
      openaiApiKey: apiKey,
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

  function startJobSearch(jobTitle, location, experienceLevel, maxPages) {
    searchInProgress = true;
    searchBtn.querySelector('.button-text').textContent = '⏹️ Stop Search';
    searchBtn.classList.remove('success-btn');
    searchBtn.classList.add('danger-btn');
    
    setLoading(searchBtn, true, '🔍 Opening LinkedIn and starting search...', searchStatus);
    searchProgress.style.display = 'block';
    jobStats.style.display = 'grid';
    searchLogs.style.display = 'block';
    
    // Reset counters
    jobsFound.textContent = '0';
    pagesScraped.textContent = '0';
    searchLogs.innerHTML = '';
    progressFill.style.width = '0%';
    
    addLog('🚀 Starting job search...');
    addLog(`🎯 Searching for: ${jobTitle} ${location ? `in ${location}` : ''}`);
    
    // Open LinkedIn search
    chrome.runtime.sendMessage({
      action: "openLinkedInSearch",
      params: {
        jobTitle: jobTitle,
        location: location,
        experienceLevel: experienceLevel
      }
    }, function(response) {
      if (response && response.success) {
        currentSearchTabId = response.tabId;
        addLog('🌐 LinkedIn opened, waiting for page load...');
        
        // Start scraping after a delay
        setTimeout(() => {
          chrome.runtime.sendMessage({
            action: "startJobScraping",
            maxPages: maxPages,
            jobTitle: jobTitle,
            location: location
          });
        }, 5000);
      } else {
        addLog('❌ Failed to open LinkedIn');
        stopJobSearch();
      }
    });
  }

  function stopJobSearch() {
    searchInProgress = false;
    searchBtn.querySelector('.button-text').textContent = '🔍 Search LinkedIn Jobs';
    searchBtn.classList.remove('danger-btn');
    searchBtn.classList.add('success-btn');
    setLoading(searchBtn, false);
    
    if (currentSearchTabId) {
      // Optionally close the search tab
      // chrome.tabs.remove(currentSearchTabId);
      currentSearchTabId = null;
    }
    
    addLog('⏹️ Search stopped by user');
  }

  // Fill current form
  fillBtn.addEventListener('click', function() {
    const apiKey = apiKeyInput.value.trim();
    const userData = userDataInput.value.trim();
    
    setLoading(fillBtn, true, '🤖 AI is analyzing and filling the form...', applyStatus);
    
    chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
      if (!tabs[0]) {
        setLoading(fillBtn, false);
        applyStatus.textContent = '❌ Unable to access current tab';
        applyStatus.className = 'status error';
        return;
      }
      
      chrome.tabs.sendMessage(tabs[0].id, {
        action: "fillFormWithAI",
        apiKey: apiKey,
        userData: userData
      }, function(response) {
        setLoading(fillBtn, false);
        
        if (chrome.runtime.lastError) {
          applyStatus.textContent = '❌ Cannot access this page. Try refreshing and reopening the extension.';
          applyStatus.className = 'status error';
          return;
        }
        
        if (response && response.success) {
          if (response.fieldsCount > 0) {
            applyStatus.textContent = `🎉 Success! AI filled ${response.fieldsCount} fields intelligently`;
            applyStatus.className = 'status success';
          } else {
            applyStatus.textContent = '📄 No fillable form fields found on this page';
            applyStatus.className = 'status info';
          }
        } else {
          const errorMsg = response?.error || 'Unknown error occurred';
          applyStatus.textContent = `❌ Error: ${errorMsg}`;
          applyStatus.className = 'status error';
        }
      });
    });
  });

  // Clear form
  clearBtn.addEventListener('click', function() {
    setLoading(clearBtn, true, '🗑️ Clearing all form fields...', applyStatus);
    
    chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
      chrome.tabs.sendMessage(tabs[0].id, {action: "clearForm"}, function(response) {
        setLoading(clearBtn, false);
        
        if (response && response.success) {
          if (response.fieldsCount > 0) {
            applyStatus.textContent = `✅ Cleared ${response.fieldsCount} fields`;
            applyStatus.className = 'status success';
          } else {
            applyStatus.textContent = '📄 No form fields found to clear';
            applyStatus.className = 'status info';
          }
        } else {
          applyStatus.textContent = '❌ Failed to clear form';
          applyStatus.className = 'status error';
        }
      });
    });
  });

  // Analyze form
  analyzeBtn.addEventListener('click', function() {
    setLoading(analyzeBtn, true, '📊 Analyzing current page structure...', applyStatus);
    
    chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {
      chrome.tabs.sendMessage(tabs[0].id, {action: "analyzePage"}, function(response) {
        setLoading(analyzeBtn, false);
        
        if (response && response.success) {
          applyStatus.textContent = `📊 Found ${response.inputFields} input fields, ${response.forms} forms, ${response.buttons} buttons`;
          applyStatus.className = 'status info';
        } else {
          applyStatus.textContent = '❌ Could not analyze page';
          applyStatus.className = 'status error';
        }
      });
    });
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
      searchInProgress = false;
      searchBtn.querySelector('.button-text').textContent = '🔍 Search LinkedIn Jobs';
      searchBtn.classList.remove('danger-btn');
      searchBtn.classList.add('success-btn');
      setLoading(searchBtn, false);
      progressFill.style.width = '100%';
      
      if (request.success) {
        searchStatus.textContent = `🎉 Search complete! Found ${request.totalJobs} jobs and saved to downloads`;
        searchStatus.className = 'status success';
        addLog(`✅ Search completed successfully! ${request.totalJobs} jobs saved to file`);
      } else {
        searchStatus.textContent = `❌ Search failed: ${request.error}`;
        searchStatus.className = 'status error';
        addLog(`❌ Search failed: ${request.error}`);
      }
      
      currentSearchTabId = null;
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
    applyStatus.textContent = '👆 Right-click on any form element and select "AI Fill Fields" from the context menu';
    applyStatus.className = 'status info';
  });
});