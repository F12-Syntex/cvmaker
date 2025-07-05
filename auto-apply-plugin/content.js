// Store the last right-clicked element
let lastClickedElement = null;
let clickCoordinates = { x: 0, y: 0 };
let isSearching = false;
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
    clickCoordinates = { x: event.pageX, y: event.pageY };

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

function findElementAtCoordinates(x, y) {
    if (x && y) {
        const element = document.elementFromPoint(x, y);
        if (element) return element;
    }
    return lastClickedElement;
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
    if (isSearching) return;
    
    isSearching = true;
    searchData = {
        jobs: [],
        currentPage: 1,
        maxPages: maxPages,
        jobTitle: jobTitle,
        location: location
    };
    
    try {
        await scrapeLinkedInJobs();
    } catch (error) {
        console.error('LinkedIn scraping error:', error);
        chrome.runtime.sendMessage({
            action: "jobSearchComplete",
            success: false,
            error: error.message
        });
    } finally {
        isSearching = false;
    }
}

async function scrapeLinkedInJobs() {
    while (searchData.currentPage <= searchData.maxPages && isSearching) {
        // Send progress update
        chrome.runtime.sendMessage({
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
    
    // Create and download the file
    const jobsText = formatJobsForFile(searchData.jobs, searchData.jobTitle, searchData.location);
    downloadJobsFile(jobsText, searchData.jobTitle);
    
    // Send completion message
    chrome.runtime.sendMessage({
        action: "jobSearchComplete",
        success: true,
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

function formatJobsForFile(jobs, searchTitle, searchLocation) {
    const header = `# LinkedIn Job Search Results
Search Query: ${searchTitle}${searchLocation ? ` in ${searchLocation}` : ''}
Search Date: ${new Date().toLocaleDateString()}
Total Jobs Found: ${jobs.length}
Generated by: AI Auto Applier Extension

================================================================================

`;

    const jobsText = jobs.map((job, index) => {
        return `JOB #${index + 1}
================================================================================
Title: ${job.title}
Company: ${job.company}
Location: ${job.location}
Posted: ${job.posted}
Job ID: ${job.id}
Link: ${job.link}

Description:
${job.description || 'No description available'}

Scraped: ${new Date(job.scraped).toLocaleString()}

================================================================================

`;
    }).join('');

    return header + jobsText;
}

function downloadJobsFile(content, jobTitle) {
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const filename = `linkedin_jobs_${jobTitle.replace(/[^a-z0-9]/gi, '_')}_${new Date().toISOString().split('T')[0]}.txt`;
    
    // Use Chrome's downloads API through background script
    chrome.runtime.sendMessage({
        action: "downloadFile",
        url: url,
        filename: filename
    });
}

// AI form filling functionality
async function fillElementWithAI(apiKey, userData, clickX, clickY) {
    let fieldsCount = 0;
    let errors = [];

    try {
        const targetElement = findElementAtCoordinates(clickX, clickY) || lastClickedElement;

        if (!targetElement) {
            throw new Error('Could not find target element');
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
                    result = await fillSelectWithAI(apiKey, userData, input, jobContext);
                } else {
                    const fieldInfo = detectFieldType(input);
                    console.log(`Processing field: ${fieldInfo.type} - ${fieldInfo.context.substring(0, 50)}`);

                    const aiResponse = await generateAIResponse(apiKey, userData, fieldInfo, jobContext);

                    if (aiResponse && aiResponse.length > 0) {
                        input.focus();
                        input.style.backgroundColor = '#e8f5e8';
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

        chrome.runtime.sendMessage({
            action: "showNotification",
            message: message
        });

        return { success: true, fieldsCount, errors };

    } catch (error) {
        console.error('Element filling failed:', error);
        removeHighlight();

        chrome.runtime.sendMessage({
            action: "showNotification",
            message: `❌ Error: ${error.message}`
        });

        return { success: false, error: error.message, fieldsCount };
    }
}

async function fillSelectWithAI(apiKey, userData, element, jobContext) {
    if (element.tagName === 'SELECT') {
        return await fillNativeSelectWithAI(apiKey, userData, element, jobContext);
    }

    if (element.role === 'combobox' || element.classList.contains('rcmpaginatedselectinput')) {
        return await fillCustomDropdownWithAI(apiKey, userData, element, jobContext);
    }

    return false;
}

async function fillNativeSelectWithAI(apiKey, userData, select, jobContext) {
    if (select.options.length <= 1 || select.disabled) return false;

    try {
        const fieldInfo = detectFieldType(select);
        const options = Array.from(select.options).slice(1).map(opt => opt.text).join(', ');

        if (options.length === 0) return false;

        const prompt = `User Information: ${userData}\n\nSelect the best option from these choices: ${options}\n\nField context: ${fieldInfo.context}\nJob context: ${jobContext}\n\nRespond with only the exact option text:`;

        const aiResponse = await callOpenAI(apiKey, prompt);
        if (aiResponse) {
            const matchingOption = Array.from(select.options).find(option =>
                option.text.toLowerCase().trim() === aiResponse.toLowerCase().trim() ||
                option.text.toLowerCase().includes(aiResponse.toLowerCase()) ||
                aiResponse.toLowerCase().includes(option.text.toLowerCase())
            );

            if (matchingOption) {
                select.style.backgroundColor = '#e8f5e8';
                select.value = matchingOption.value;
                select.dispatchEvent(new Event('change', { bubbles: true }));

                setTimeout(() => {
                    select.style.backgroundColor = '';
                }, 1000);

                console.log(`Selected option: ${matchingOption.text}`);
                return true;
            }
        }
        return false;
    } catch (error) {
        console.error('Error with native select:', error);
        return false;
    }
}

async function callOpenAI(apiKey, prompt) {
    try {
        const response = await fetch('https://api.openai.com/v1/chat/completions', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${apiKey}`
            },
            body: JSON.stringify({
                model: 'gpt-3.5-turbo',
                messages: [
                    {
                        role: 'system',
                        content: 'You are a helpful assistant that fills out job application forms. Return only the requested value with no extra text, quotes, or explanations. Be concise and professional.'
                    },
                    {
                        role: 'user',
                        content: prompt
                    }
                ],
                max_tokens: 150,
                temperature: 0.3
            })
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(`OpenAI API error: ${response.status} - ${errorData.error?.message || 'Unknown error'}`);
        }

        const data = await response.json();
        return data.choices[0].message.content.trim();
    } catch (error) {
        console.error('OpenAI API call failed:', error);
        throw error;
    }
}

function detectFieldType(element) {
    const id = (element.id || '').toLowerCase();
    const name = (element.name || '').toLowerCase();
    const placeholder = (element.placeholder || '').toLowerCase();
    const className = (element.className || '').toLowerCase();

    let label = '';
    if (element.labels && element.labels[0]) {
        label = element.labels[0].textContent.toLowerCase();
    } else {
        const parent = element.parentElement;
        const prevSibling = element.previousElementSibling;
        const nextSibling = element.nextElementSibling;

        if (parent) {
            const labelEl = parent.querySelector('label');
            if (labelEl) label = labelEl.textContent.toLowerCase();
        }
        if (prevSibling && prevSibling.tagName === 'LABEL') {
            label = prevSibling.textContent.toLowerCase();
        }
        if (nextSibling && nextSibling.tagName === 'LABEL') {
            label = nextSibling.textContent.toLowerCase();
        }
    }

    const parent = element.parentElement;
    const nearbyText = parent ? parent.textContent.toLowerCase().substring(0, 100) : '';

    const allText = `${id} ${name} ${placeholder} ${label} ${className} ${nearbyText}`;

    return {
        type: determineFieldType(allText),
        context: allText.substring(0, 200),
        placeholder: element.placeholder,
        label: label,
        element: element
    };
}

function determineFieldType(text) {
    if (text.includes('email') || text.includes('e-mail')) return 'email';
    if (text.includes('phone') || text.includes('tel') || text.includes('mobile') || text.includes('cell')) return 'phone';
    if (text.includes('first') && text.includes('name')) return 'firstName';
    if (text.includes('last') && text.includes('name')) return 'lastName';
    if ((text.includes('full') || text.includes('complete')) && text.includes('name')) return 'fullName';
    if (text.includes('name') && !text.includes('company') && !text.includes('user')) return 'fullName';
    if (text.includes('address') || text.includes('street') || text.includes('location')) return 'address';
    if (text.includes('city')) return 'city';
    if (text.includes('state') || text.includes('province')) return 'state';
    if (text.includes('zip') || text.includes('postal')) return 'zipCode';
    if (text.includes('country')) return 'country';
    if (text.includes('company') || text.includes('employer') || text.includes('organization')) return 'company';
    if (text.includes('position') || text.includes('title') || text.includes('job') || text.includes('role')) return 'position';
    if (text.includes('salary') || text.includes('compensation') || text.includes('wage') || text.includes('pay')) return 'salary';
    if (text.includes('university') || text.includes('school') || text.includes('college') || text.includes('education') || text.includes('degree')) return 'education';
    if (text.includes('skill') || text.includes('expertise') || text.includes('proficient')) return 'skills';
    if (text.includes('experience') || text.includes('background')) return 'experience';
    if (text.includes('cover') || text.includes('letter') || text.includes('summary') || text.includes('objective') || text.includes('about')) return 'coverLetter';
    if (text.includes('linkedin')) return 'linkedin';
    if (text.includes('website') || text.includes('portfolio') || text.includes('url') || text.includes('link')) return 'website';
    if (text.includes('date') || text.includes('when') || text.includes('start') || text.includes('end')) return 'date';
    if (text.includes('why') || text.includes('reason') || text.includes('motivation') || text.includes('interest')) return 'motivation';
    if (text.includes('reference')) return 'reference';
    if (text.includes('citizen') || text.includes('eligible') || text.includes('authorized')) return 'workAuthorization';

    return 'generic';
}

async function generateAIResponse(apiKey, userData, fieldInfo, jobContext = '') {
    const { type, context, placeholder, label } = fieldInfo;

    let prompt = `User Information:\n${userData}\n\n`;

    if (jobContext) {
        prompt += `Page Context: ${jobContext}\n\n`;
    }

    prompt += `Field Information:\nType: ${type}\nContext: ${context}\nPlaceholder: ${placeholder}\nLabel: ${label}\n\nPlease provide `;

    switch (type) {
        case 'email':
            prompt += 'the user\'s email address';
            break;
        case 'phone':
            prompt += 'the user\'s phone number in standard format';
            break;
        case 'firstName':
            prompt += 'only the user\'s first name';
            break;
        case 'lastName':
            prompt += 'only the user\'s last name';
            break;
        case 'fullName':
            prompt += 'the user\'s full name';
            break;
        case 'address':
            prompt += 'the user\'s street address';
            break;
        case 'city':
            prompt += 'the user\'s city';
            break;
        case 'state':
            prompt += 'the user\'s state or province';
            break;
        case 'zipCode':
            prompt += 'the user\'s zip or postal code';
            break;
        case 'country':
            prompt += 'the user\'s country';
            break;
        case 'company':
            prompt += 'the user\'s current or most recent company name';
            break;
        case 'position':
            prompt += 'the user\'s current job title or desired position';
            break;
        case 'education':
            prompt += 'relevant education information (degree and school)';
            break;
        case 'skills':
            prompt += 'relevant skills for this field (comma-separated)';
            break;
        case 'experience':
            prompt += 'brief professional experience summary';
            break;
        case 'coverLetter':
            prompt += 'a brief professional response (2-3 sentences maximum)';
            break;
        case 'linkedin':
            prompt += 'the user\'s LinkedIn profile URL';
            break;
        case 'website':
            prompt += 'the user\'s website or portfolio URL';
            break;
        case 'salary':
            prompt += 'an appropriate salary expectation based on the role';
            break;
        case 'date':
            prompt += 'an appropriate date in YYYY-MM-DD format';
            break;
        case 'workAuthorization':
            prompt += '"Yes" if the user is authorized to work, otherwise "No"';
            break;
        case 'motivation':
            prompt += 'a brief explanation of interest or motivation (1-2 sentences)';
            break;
        default:
            prompt += `an appropriate response for this field based on the context: ${context}`;
    }

    try {
        const response = await callOpenAI(apiKey, prompt);
        return response.replace(/^["']|["']$/g, '').trim();
    } catch (error) {
        console.error('Failed to generate AI response:', error);
        throw error;
    }
}

async function fillFormWithAI(apiKey, userData) {
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

                const aiResponse = await generateAIResponse(apiKey, userData, fieldInfo, jobContext);

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
                const result = await fillSelectWithAI(apiKey, userData, select, jobContext);
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
        fillFormWithAI(request.apiKey, request.userData)
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
        fillElementWithAI(request.apiKey, request.userData, request.clickX, request.clickY)
            .then(result => sendResponse(result))
            .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
    } else if (request.action === "startLinkedInScraping") {
        startLinkedInScraping(request.maxPages, request.jobTitle, request.location)
            .then(result => sendResponse(result))
            .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
    } else if (request.action === "analyzePage") {
        const result = analyzePage();
        sendResponse(result);
    }
});