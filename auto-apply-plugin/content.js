// Content script. It is not declared in the manifest: the background or the popup injects it
// (after lib/fields.js) into the tab the user acted on, or into the LinkedIn search tab.
// It never sees the OpenAI API key or the user's profile. It describes each field to the
// background, which builds the prompt, calls OpenAI and returns only the text for that field.
//
// The body is wrapped so that injecting it twice into the same page is harmless: the second
// copy returns at once and the first copy keeps handling messages.
(function () {
if (globalThis.__autoApplyContentScriptLoaded) return;
globalThis.__autoApplyContentScriptLoaded = true;

// Store the last right-clicked element
let lastClickedElement = null;
let isSearching = false;
let stopRequested = false;
let searchData = {
  jobs: [],
  currentPage: 1,
  maxPages: 5,
  jobTitle: '',
  location: ''
};

// Track right-click events to store the clicked element
document.addEventListener('contextmenu', function (event) {
    lastClickedElement = event.target;

    // Add visual indicator to show which element was clicked
    removeHighlight();
    highlightElement(event.target);

    // Remove highlight after a short delay
    setTimeout(removeHighlight, 3000);
}, true);

function highlightElement(element) {
    element.style.outline = '2px solid #4CAF50';
    element.style.outlineOffset = '2px';
    element.setAttribute('data-ai-highlighted', 'true');
}

function removeHighlight() {
    const highlighted = document.querySelectorAll('[data-ai-highlighted]');
    highlighted.forEach(el => {
        el.style.outline = '';
        el.style.outlineOffset = '';
        el.removeAttribute('data-ai-highlighted');
    });
}

// Sends a message to the extension (background or popup). When nobody is listening, for example
// because the popup is closed, Chrome rejects the promise; that is expected, so it is swallowed.
function notifyExtension(message) {
    try {
        const pending = chrome.runtime.sendMessage(message);
        if (pending && typeof pending.catch === 'function') pending.catch(() => {});
    } catch (error) {
        // The extension was reloaded and this content script is orphaned.
    }
}

// Asks the background for an AI completion. Throws with the background's error message.
async function askBackground(message) {
    const response = await chrome.runtime.sendMessage(message);
    if (!response || response.success !== true) {
        throw new Error((response && response.error) || 'No answer from the extension');
    }
    return typeof response.text === 'string' ? response.text : '';
}

// The element to fill: the last right-clicked element if this script saw the click, otherwise
// the focused form field (right-clicking a field focuses it, and this script is injected
// only after the click).
function resolveTargetElement() {
    if (lastClickedElement && lastClickedElement.isConnected !== false) return lastClickedElement;
    const active = document.activeElement;
    if (active && active !== document.body && isInputField(active)) return active;
    return null;
}

function findInputFieldsInElement(element) {
    if (!element) return [];

    const inputs = [];

    // Check if the element itself is an input
    if (isInputField(element)) {
        inputs.push(element);
    }

    // Find all input fields within the element
    const selectors = [
        'input[type="text"]',
        'input[type="email"]',
        'input[type="tel"]',
        'input[type="url"]',
        'input[type="number"]',
        'input[type="date"]',
        'input[type="password"]',
        'textarea',
        'select',
        'input[role="combobox"]',
        '.rcmpaginatedselectinput'
    ];

    selectors.forEach(selector => {
        const fields = element.querySelectorAll(selector);
        fields.forEach(field => {
            if (!field.disabled && !field.readOnly && field.type !== 'hidden') {
                inputs.push(field);
            }
        });
    });

    // If no inputs found in the element, try parent elements
    if (inputs.length === 0 && element.parentElement) {
        let parent = element.parentElement;
        let attempts = 0;

        while (parent && attempts < 3) {
            const parentInputs = findInputFieldsInElement(parent);
            if (parentInputs.length > 0) {
                return parentInputs;
            }
            parent = parent.parentElement;
            attempts++;
        }
    }

    return inputs;
}

function isInputField(element) {
    const inputTypes = ['text', 'email', 'tel', 'url', 'number', 'date', 'password'];
    return (
        (element.tagName === 'INPUT' && inputTypes.includes(element.type)) ||
        element.tagName === 'TEXTAREA' ||
        element.tagName === 'SELECT' ||
        element.role === 'combobox' ||
        element.classList.contains('rcmpaginatedselectinput')
    );
}

// LinkedIn job scraping functionality
async function startLinkedInScraping(maxPages, jobTitle, location) {
    if (isSearching) return { success: false, error: 'A search is already running in this tab' };

    // Parameters come from a message and may be missing.
    const params = AutoApplyLib.normalizeSearchRequest({ maxPages, params: { jobTitle, location } });
    isSearching = true;
    stopRequested = false;
    searchData = {
        jobs: [],
        currentPage: 1,
        maxPages: params.maxPages,
        jobTitle: params.jobTitle,
        location: params.location
    };
    
    try {
        await scrapeLinkedInJobs();
    } catch (error) {
        console.error('LinkedIn scraping error:', error);
        notifyExtension({
            action: "jobSearchComplete",
            success: false,
            error: error.message
        });
    } finally {
        isSearching = false;
    }
    return { success: true };
}

function stopLinkedInScraping() {
    const wasSearching = isSearching;
    stopRequested = true;
    return { success: true, wasSearching };
}

async function scrapeLinkedInJobs() {
    while (searchData.currentPage <= searchData.maxPages && !stopRequested) {
        // Send progress update
        notifyExtension({
            action: "jobSearchProgress",
            currentPage: searchData.currentPage,
            totalPages: searchData.maxPages,
            jobsFound: searchData.jobs.length
        });
        
        // Wait for page to load completely
        await waitForPageLoad();
        
        // Scrape current page
        const pageJobs = await scrapeCurrentLinkedInPage();
        searchData.jobs = searchData.jobs.concat(pageJobs);
        
        console.log(`Page ${searchData.currentPage}: Found ${pageJobs.length} jobs`);
        
        if (stopRequested) break;

        // Check if there's a next page and navigate to it
        if (searchData.currentPage < searchData.maxPages) {
            const hasNext = await navigateToNextPage();
            if (!hasNext) {
                console.log('No more pages available');
                break;
            }
        }
        
        searchData.currentPage++;
        
        // Add delay between pages to avoid being blocked
        await new Promise(resolve => setTimeout(resolve, 2000));
    }
    
    // Create and download the file (after a stop, only if something was found)
    if (!stopRequested || searchData.jobs.length > 0) {
        const jobsText = AutoApplyLib.formatJobsForFile(searchData.jobs, searchData.jobTitle, searchData.location);
        await downloadJobsFile(jobsText, searchData.jobTitle);
    }

    // Send completion message
    notifyExtension({
        action: "jobSearchComplete",
        success: true,
        stopped: stopRequested,
        totalJobs: searchData.jobs.length
    });
}

async function waitForPageLoad() {
    return new Promise(resolve => {
        if (document.readyState === 'complete') {
            resolve();
        } else {
            window.addEventListener('load', resolve, { once: true });
        }
    });
}

async function scrapeCurrentLinkedInPage() {
    const jobs = [];
    
    // Wait a bit for dynamic content to load
    await new Promise(resolve => setTimeout(resolve, 2000));
    
    // Multiple selectors to catch different LinkedIn layouts
    const jobCardSelectors = [
        '.job-search-card',
        '.jobs-search__results-list li',
        '[data-entity-urn*="job"]',
        '.job-result-card',
        '.jobs-search-results__list-item'
    ];
    
    let jobCards = [];
    
    for (const selector of jobCardSelectors) {
        jobCards = document.querySelectorAll(selector);
        if (jobCards.length > 0) {
            console.log(`Found ${jobCards.length} job cards using selector: ${selector}`);
            break;
        }
    }
    
    for (const card of jobCards) {
        try {
            const job = extractJobInfo(card);
            if (job && job.title && job.company) {
                jobs.push(job);
            }
        } catch (error) {
            console.error('Error extracting job info:', error);
        }
    }
    
    return jobs;
}

function extractJobInfo(jobCard) {
    try {
        // Multiple selectors for different LinkedIn layouts
        const titleSelectors = [
            '.base-search-card__title a',
            '.job-search-card__title a',
            'h3 a',
            '.job-result-card__title',
            '.result-card__title'
        ];
        
        const companySelectors = [
            '.base-search-card__subtitle a',
            '.job-search-card__subtitle a',
            'h4 a',
            '.job-result-card__subtitle',
            '.result-card__subtitle'
        ];
        
        const locationSelectors = [
            '.job-search-card__location',
            '.job-result-card__location',
            '.result-card__location'
        ];
        
        const linkSelectors = [
            'a[href*="/jobs/view/"]',
            '.base-card__full-link',
            '.job-search-card__title a'
        ];
        
        const timeSelectors = [
            'time',
            '.job-search-card__listdate',
            '.job-result-card__listdate'
        ];
        
        // Extract information using multiple selectors
        const titleElement = findElementBySelectors(jobCard, titleSelectors);
        const companyElement = findElementBySelectors(jobCard, companySelectors);
        const locationElement = findElementBySelectors(jobCard, locationSelectors);
        const linkElement = findElementBySelectors(jobCard, linkSelectors);
        const timeElement = findElementBySelectors(jobCard, timeSelectors);
        
        const title = titleElement ? titleElement.textContent.trim() : '';
        const company = companyElement ? companyElement.textContent.trim() : '';
        const location = locationElement ? locationElement.textContent.trim() : '';
        const link = linkElement ? linkElement.href : '';
        const posted = timeElement ? timeElement.textContent.trim() : '';
        
        // Get job description if available
        const descriptionElement = jobCard.querySelector('.job-search-card__snippet, .job-result-card__snippet');
        const description = descriptionElement ? descriptionElement.textContent.trim() : '';
        
        // Extract job ID from URL
        const jobIdMatch = link.match(/\/jobs\/view\/(\d+)/);
        const jobId = jobIdMatch ? jobIdMatch[1] : '';
        
        if (!title || !company) {
            return null; // Skip if essential info is missing
        }
        
        return {
            id: jobId,
            title: title,
            company: company,
            location: location,
            link: link,
            posted: posted,
            description: description,
            scraped: new Date().toISOString()
        };
        
    } catch (error) {
        console.error('Error extracting job info from card:', error);
        return null;
    }
}

function findElementBySelectors(parent, selectors) {
    for (const selector of selectors) {
        const element = parent.querySelector(selector);
        if (element) return element;
    }
    return null;
}

async function navigateToNextPage() {
    // Look for next page button with multiple selectors
    const nextButtonSelectors = [
        'button[aria-label="View next page"]',
        'button[aria-label="Next"]',
        '.artdeco-pagination__button--next',
        '[data-test="pagination-next-btn"]'
    ];
    
    let nextButton = null;
    
    for (const selector of nextButtonSelectors) {
        nextButton = document.querySelector(selector);
        if (nextButton && !nextButton.disabled) {
            break;
        }
    }
    
    if (nextButton && !nextButton.disabled) {
        console.log('Clicking next page button');
        nextButton.click();
        
        // Wait for page navigation
        await new Promise(resolve => setTimeout(resolve, 3000));
        return true;
    }
    
    return false;
}

// The background builds the file name and a data URL and starts the download, so there is
// no blob URL to leak.
async function downloadJobsFile(content, jobTitle) {
    try {
        const response = await chrome.runtime.sendMessage({
            action: "downloadJobs",
            content: content,
            jobTitle: jobTitle
        });
        if (response && response.success === false) {
            console.error('Download failed:', response.error);
        }
    } catch (error) {
        console.error('Download failed:', error);
    }
}

// AI form filling functionality
async function fillElementWithAI() {
    let fieldsCount = 0;
    let errors = [];

    try {
        // The context menu gives no usable coordinates.
        const targetElement = resolveTargetElement();

        if (!targetElement || targetElement.isConnected === false) {
            throw new Error('Could not find target element. Right-click the form element again.');
        }

        highlightElement(targetElement);
        const inputFields = findInputFieldsInElement(targetElement);

        if (inputFields.length === 0) {
            throw new Error('No input fields found in the selected element');
        }

        console.log(`Found ${inputFields.length} input fields in selected element`);

        const pageTitle = document.title;
        const headings = Array.from(document.querySelectorAll('h1, h2, h3')).map(h => h.textContent).join(' ');
        const elementContext = targetElement.textContent.substring(0, 200);
        const jobContext = `Page: ${pageTitle} | Section: ${elementContext} | Headings: ${headings}`.substring(0, 400);

        for (const input of inputFields) {
            if (input.value.trim() !== '' && input.type !== 'select-one') {
                continue;
            }

            try {
                let result = null;

                if (input.tagName === 'SELECT') {
                    result = await fillSelectWithAI(input, jobContext);
                } else {
                    const fieldInfo = detectFieldType(input);
                    console.log(`Processing field: ${fieldInfo.type} - ${fieldInfo.context.substring(0, 50)}`);

                    const aiResponse = await generateAIResponse(fieldInfo, jobContext);

                    if (aiResponse && aiResponse.length > 0) {
                        input.focus();
                        input.style.backgroundColor= '#e8f5e8';
                        input.value = aiResponse;

                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        input.dispatchEvent(new Event('blur', { bubbles: true }));

                        setTimeout(() => {
                            input.style.backgroundColor = '';
                        }, 1000);

                        result = true;
                        console.log(`Filled field with: ${aiResponse.substring(0, 50)}`);
                    }
                }

                if (result) {
                    fieldsCount++;
                    await new Promise(resolve => setTimeout(resolve, 300));
                }

            } catch (error) {
                errors.push(`Field error: ${error.message}`);
                console.error('Error filling field:', error);
            }
        }

        setTimeout(removeHighlight, 2000);

        const message = fieldsCount > 0
            ? `✅ Successfully filled ${fieldsCount} fields in selected element`
            : '⚠️ No fields were filled';

        notifyExtension({
            action: "showNotification",
            message: message
        });

        return { success: true, fieldsCount, errors };

    } catch (error) {
        console.error('Element filling failed:', error);
        removeHighlight();

        notifyExtension({
            action: "showNotification",
            message: `❌ Error: ${error.message}`
        });

        return { success: false, error: error.message, fieldsCount };
    }
}

// Only native <select> elements are handled here. Custom comboboxes are text inputs and are
// filled as text by the callers.
async function fillSelectWithAI(element, jobContext) {
    if (element.tagName === 'SELECT') {
        return await fillNativeSelectWithAI(element, jobContext);
    }
    return false;
}

async function fillNativeSelectWithAI(select, jobContext) {
    if (select.options.length <= 1 || select.disabled) return false;

    try {
        const fieldInfo = detectFieldType(select);
        // The first option is usually a "Please select" placeholder.
        const options = Array.from(select.options).slice(1);
        const optionTexts = options.map(opt => opt.text);

        const aiResponse = await askBackground({
            action: "aiChooseOption",
            options: optionTexts,
            field: { context: fieldInfo.context },
            jobContext: jobContext
        });
        const index = AutoApplyLib.matchOption(optionTexts, aiResponse);
        if (index >= 0) {
            const matchingOption = options[index];
            select.style.backgroundColor = '#e8f5e8';
            select.value = matchingOption.value;
            select.dispatchEvent(new Event('change', { bubbles: true }));

            setTimeout(() => {
                select.style.backgroundColor = '';
            }, 1000);

            console.log(`Selected option: ${matchingOption.text}`);
            return true;
        }
        return false;
    } catch (error) {
        console.error('Error with native select:', error);
        return false;
    }
}

function detectFieldType(element) {
    let label = '';
    if (element.labels && element.labels[0]) {
        label = element.labels[0].textContent;
    } else {
        const parent = element.parentElement;
        const prevSibling = element.previousElementSibling;
        const nextSibling = element.nextElementSibling;

        if (parent) {
            const labelEl = parent.querySelector('label');
            if (labelEl) label = labelEl.textContent;
        }
        if (prevSibling && prevSibling.tagName === 'LABEL') {
            label = prevSibling.textContent;
        }
        if (nextSibling && nextSibling.tagName === 'LABEL') {
            label = nextSibling.textContent;
        }
    }

    const parent = element.parentElement;
    const hints = {
        autocomplete: element.getAttribute ? element.getAttribute('autocomplete') || '' : '',
        label: label,
        ariaLabel: element.getAttribute ? element.getAttribute('aria-label') || '' : '',
        placeholder: element.placeholder || '',
        name: element.name || '',
        id: element.id || '',
        nearbyText: parent ? parent.textContent.substring(0, 100) : ''
    };

    return {
        type: AutoApplyLib.determineFieldType(hints),
        context: AutoApplyLib.buildFieldContext(hints),
        placeholder: element.placeholder,
        label: label.trim(),
        element: element
    };
}

// The background builds the prompt from the field description and the stored profile.
async function generateAIResponse(fieldInfo, jobContext = '') {
    const { type, context, placeholder, label } = fieldInfo;
    return askBackground({
        action: "aiFillField",
        field: { type, context, placeholder: placeholder || '', label: label || '' },
        jobContext: jobContext
    });
}

async function fillFormWithAI() {
    let fieldsCount = 0;
    let errors = [];

    try {
        const pageTitle = document.title;
        const headings = Array.from(document.querySelectorAll('h1, h2, h3')).map(h => h.textContent).join(' ');
        const jobContext = `Page: ${pageTitle} | Headings: ${headings}`.substring(0, 300);

        const inputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input[type="url"], input[type="number"], input[type="date"], textarea');

        console.log(`Found ${inputs.length} input fields to process`);

        for (const input of inputs) {
            if (input.type === 'hidden' || input.disabled || input.readOnly || input.value.trim() !== '') {
                continue;
            }

            try {
                const fieldInfo = detectFieldType(input);
                console.log(`Processing field: ${fieldInfo.type} - ${fieldInfo.context.substring(0, 50)}`);

                const aiResponse = await generateAIResponse(fieldInfo, jobContext);

                if (aiResponse && aiResponse.length > 0) {
                    input.focus();
                    input.value = aiResponse;

                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                    input.dispatchEvent(new Event('blur', { bubbles: true }));

                    fieldsCount++;
                    console.log(`Filled field with: ${aiResponse.substring(0, 50)}`);

                    await new Promise(resolve => setTimeout(resolve, 300));
                }
            } catch (error) {
                errors.push(`Field error: ${error.message}`);
                console.error('Error filling field:', error);
            }
        }

        const selects = document.querySelectorAll('select');
        for (const select of selects) {
            if (select.options.length <= 1 || select.disabled) continue;

            try {
                const result = await fillSelectWithAI(select, jobContext);
                if (result) {
                    fieldsCount++;
                }
                await new Promise(resolve => setTimeout(resolve, 300));
            } catch (error) {
                errors.push(`Select error: ${error.message}`);
                console.error('Error with select:', error);
            }
        }

        return { success: true, fieldsCount, errors };

    } catch (error) {
        console.error('Form filling failed:', error);
        return { success: false, error: error.message, fieldsCount };
    }
}

function clearForm() {
    let fieldsCount = 0;

    try {
        const inputs = document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input[type="url"], input[type="number"], input[type="date"], textarea');
        inputs.forEach(input => {
            if (!input.disabled && !input.readOnly) {
                input.value = '';
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.dispatchEvent(new Event('change', { bubbles: true }));
                fieldsCount++;
            }
        });

        const checkboxes = document.querySelectorAll('input[type="checkbox"]:checked');
        checkboxes.forEach(checkbox => {
            checkbox.checked = false;
            checkbox.dispatchEvent(new Event('change', { bubbles: true }));
            fieldsCount++;
        });

        const radios = document.querySelectorAll('input[type="radio"]:checked');
        radios.forEach(radio => {
            radio.checked = false;
            radio.dispatchEvent(new Event('change', { bubbles: true }));
        });

        const selects = document.querySelectorAll('select');
        selects.forEach(select => {
            if (select.selectedIndex !== 0) {
                select.selectedIndex = 0;
                select.dispatchEvent(new Event('change', { bubbles: true }));
                fieldsCount++;
            }
        });

        return fieldsCount;
    } catch (error) {
        console.error('Error clearing form:', error);
        return 0;
    }
}

function analyzePage() {
    try {
        const inputFields = document.querySelectorAll('input, textarea, select').length;
        const forms = document.querySelectorAll('form').length;
        const buttons = document.querySelectorAll('button, input[type="submit"]').length;
        
        return {
            success: true,
            inputFields: inputFields,
            forms: forms,
            buttons: buttons
        };
    } catch (error) {
        return { success: false, error: error.message };
    }
}

// Listen for messages from popup and background script
chrome.runtime.onMessage.addListener(function (request, sender, sendResponse) {
    if (request.action === "fillFormWithAI") {
        fillFormWithAI()
            .then(result => sendResponse(result))
            .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
    } else if (request.action === "clearForm") {
        try {
            const fieldsCount = clearForm();
            sendResponse({ success: true, fieldsCount: fieldsCount });
        } catch (error) {
            sendResponse({ success: false, error: error.message });
        }
    } else if (request.action === "fillElementWithAI") {
        fillElementWithAI()
            .then(result => sendResponse(result))
            .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
    } else if (request.action === "startLinkedInScraping") {
        // Answer at once; scraping takes minutes and reports through jobSearchComplete.
        const alreadyRunning = isSearching;
        startLinkedInScraping(request.maxPages, request.jobTitle, request.location)
            .catch(error => console.error('LinkedIn scraping error:', error));
        sendResponse(alreadyRunning
            ? { success: false, error: 'A search is already running in this tab' }
            : { success: true, started: true });
    } else if (request.action === "stopLinkedInScraping") {
        sendResponse(stopLinkedInScraping());
    } else if (request.action === "analyzePage") {
        const result = analyzePage();
        sendResponse(result);
    }
});

})();
