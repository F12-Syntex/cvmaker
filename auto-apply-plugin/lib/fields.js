// Pure helpers shared by the service worker, the content script, the popup and the node tests.
//
// This file is a classic script, not an ES module, so that it can be loaded in every context the
// extension has without a bundler:
//   - service worker: importScripts('lib/fields.js')
//   - content script: injected before content.js
//   - popup: <script src="lib/fields.js"> before popup.js
//   - node tests: require('../lib/fields.js')
// It must stay free of DOM and chrome.* calls so the tests can run it under plain node.
(function (root) {
  'use strict';

  // Turns ids, names and labels such as "firstName", "first_name" or "Phone-Number" into
  // lower-case words separated by single spaces, so the rules below can use word boundaries.
  function normalizeFieldText(value) {
    if (value === undefined || value === null) return '';
    return String(value)
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, ' ')
      .trim();
  }

  // Ordered rules: the first match wins, so more specific rules come first. Every pattern runs on
  // normalized text and uses word boundaries, so "hotel" is not "tel" and "statement" is not "state".
  const FIELD_RULES = [
    ['email', /\be ?mail\b|\bemail ?address\b/],
    ['linkedin', /\blinked ?in\b/],
    ['website', /\b(web ?site|portfolio|url|home ?page|git ?hub|link)\b/],
    ['phone', /\b(phone|telephone|tel|mobile|cell|phone ?number|mobile ?number)\b/],
    ['firstName', /\b(first ?name|given ?name|forename)\b/],
    ['lastName', /\b(last ?name|surname|family ?name)\b/],
    ['fullName', /\b(full ?name|complete ?name)\b/],
    ['workAuthorization', /\b(citizen|citizenship|eligible|eligibility|authori[sz]ed|authori[sz]ation|visa|sponsorship|right to work)\b/],
    ['salary', /\b(salary|compensation|wage|wages|pay|remuneration)\b/],
    ['motivation', /\b(why|reason|reasons|motivation|motivated|interest|interested)\b/],
    ['date', /\b(date|when|start|end|availability|available)\b/],
    ['zipCode', /\b(zip|zip ?code|postal ?code|post ?code)\b/],
    ['city', /\b(city|town)\b/],
    ['state', /\b(state|province|county|region)\b/],
    ['country', /\bcountry\b/],
    ['address', /\b(address|street|address ?line|location)\b/],
    ['education', /\b(university|school|college|education|degree|qualification|qualifications)\b/],
    ['skills', /\b(skill|skills|expertise|proficient|proficiency)\b/],
    ['experience', /\b(experience|background)\b/],
    ['coverLetter', /\b(cover|letter|summary|objective|about|statement)\b/],
    ['company', /\b(company|employer|organi[sz]ation)\b/],
    ['position', /\b(position|title|job|role)\b/],
    ['reference', /\b(reference|references|referee)\b/]
  ];

  // HTML autocomplete tokens are the most reliable signal a form gives, so they are checked first.
  const AUTOCOMPLETE_TYPES = {
    'email': 'email',
    'tel': 'phone',
    'tel-national': 'phone',
    'given-name': 'firstName',
    'family-name': 'lastName',
    'name': 'fullName',
    'street-address': 'address',
    'address-line1': 'address',
    'address-line2': 'address',
    'address-level2': 'city',
    'address-level1': 'state',
    'postal-code': 'zipCode',
    'country': 'country',
    'country-name': 'country',
    'organization': 'company',
    'organization-title': 'position',
    'url': 'website',
    'bday': 'date'
  };

  // Field sources from most to least reliable. Class names are left out on purpose: they are
  // styling noise ("form-control state-valid") and caused wrong matches.
  const FIELD_SOURCES = ['label', 'ariaLabel', 'placeholder', 'name', 'id', 'nearbyText'];

  function classifyNormalizedText(text) {
    if (!text) return 'generic';
    for (const [type, pattern] of FIELD_RULES) {
      if (pattern.test(text)) return type;
    }
    // A bare "name" means the applicant's name, unless it is a user, file or account name.
    if (/\bname\b/.test(text) && !/\b(user|file|account)\b/.test(text)) return 'fullName';
    return 'generic';
  }

  // Accepts either a single string (all hints joined) or an object of hints:
  // { autocomplete, label, ariaLabel, placeholder, name, id, nearbyText }.
  // With an object, each source is classified on its own, most reliable first, so noisy nearby
  // text cannot override a clear label.
  function determineFieldType(hints) {
    if (hints === undefined || hints === null) return 'generic';
    if (typeof hints !== 'object') return classifyNormalizedText(normalizeFieldText(hints));

    const autocomplete = String(hints.autocomplete || '').toLowerCase().trim().split(/\s+/).pop();
    if (autocomplete && Object.prototype.hasOwnProperty.call(AUTOCOMPLETE_TYPES, autocomplete)) {
      return AUTOCOMPLETE_TYPES[autocomplete];
    }
    for (const source of FIELD_SOURCES) {
      const type = classifyNormalizedText(normalizeFieldText(hints[source]));
      if (type !== 'generic') return type;
    }
    return 'generic';
  }

  // Short, readable description of a field for the AI prompt (class names left out).
  function buildFieldContext(hints, maxLength) {
    const limit = maxLength || 200;
    if (!hints || typeof hints !== 'object') return String(hints || '').substring(0, limit);
    return FIELD_SOURCES
      .map(source => (hints[source] === undefined || hints[source] === null ? '' : String(hints[source]).trim()))
      .filter(Boolean)
      .join(' | ')
      .replace(/\s+/g, ' ')
      .substring(0, limit);
  }

  // LinkedIn's f_E filter takes numeric codes; the popup offers names.
  const LINKEDIN_EXPERIENCE_CODES = {
    internship: '1',
    entry: '2',
    associate: '3',
    mid: '4',
    senior: '4', // LinkedIn has a single "Mid-Senior level"
    director: '5',
    executive: '6'
  };

  function linkedInExperienceCode(level) {
    const key = String(level || '').toLowerCase().trim();
    if (/^[1-6]$/.test(key)) return key;
    return Object.prototype.hasOwnProperty.call(LINKEDIN_EXPERIENCE_CODES, key) ? LINKEDIN_EXPERIENCE_CODES[key] : '';
  }

  function buildLinkedInSearchUrl(params) {
    const baseUrl = 'https://www.linkedin.com/jobs/search/';
    const searchParams = new URLSearchParams();

    searchParams.append('keywords', params.jobTitle);
    if (params.location) searchParams.append('location', params.location);
    const experienceCode = linkedInExperienceCode(params.experienceLevel);
    if (experienceCode) searchParams.append('f_E', experienceCode);
    searchParams.append('f_JT', 'F'); // Full-time
    searchParams.append('sortBy', 'DD'); // Date posted

    return `${baseUrl}?${searchParams.toString()}`;
  }

  // `now` is injectable so the tests do not depend on the clock.
  function formatJobsForFile(jobs, searchTitle, searchLocation, now) {
    const date = now || new Date();
    const header = `# LinkedIn Job Search Results
Search Query: ${searchTitle}${searchLocation ? ` in ${searchLocation}` : ''}
Search Date: ${date.toLocaleDateString()}
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

  const MAX_SEARCH_PAGES = 25;
  const DEFAULT_SEARCH_PAGES = 5;

  // Cleans the popup's search request; every field may be missing or of the wrong type.
  function normalizeSearchRequest(request) {
    const r = request || {};
    const params = r.params || r;
    const text = (value, max) => (typeof value === 'string' ? value.trim().substring(0, max) : '');
    const pages = parseInt(r.maxPages, 10);
    return {
      jobTitle: text(params.jobTitle, 200) || 'Software Engineer',
      location: text(params.location, 200),
      experienceLevel: text(params.experienceLevel, 20),
      maxPages: Number.isFinite(pages) ? Math.min(Math.max(pages, 1), MAX_SEARCH_PAGES) : DEFAULT_SEARCH_PAGES
    };
  }

  // A search waits for its LinkedIn tab to finish loading; then scraping starts exactly once.
  function shouldStartScraping(search, tabId, changeInfo) {
    return Boolean(
      search &&
      !search.started &&
      search.tabId === tabId &&
      changeInfo &&
      changeInfo.status === 'complete'
    );
  }

  function buildJobsFilename(jobTitle, date) {
    const day = (date || new Date()).toISOString().split('T')[0];
    const safeTitle = String(jobTitle === undefined || jobTitle === null ? '' : jobTitle)
      .replace(/[^a-z0-9]+/gi, '_')
      .replace(/^_+|_+$/g, '')
      .substring(0, 60) || 'search';
    return `linkedin_jobs_${safeTitle}_${day}.txt`;
  }

  // Data URL for a text download. The service worker cannot create blob URLs, and a data URL
  // leaves nothing to revoke.
  function textToDataUrl(text) {
    const bytes = new TextEncoder().encode(String(text));
    let binary = '';
    const chunkSize = 0x8000;
    for (let i = 0; i < bytes.length; i += chunkSize) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
    }
    return `data:text/plain;charset=utf-8;base64,${btoa(binary)}`;
  }

  // --- AI requests (built and sent only by the service worker) ---

  // Files injected on demand into a tab; there is no manifest content script.
  const CONTENT_SCRIPT_FILES = ['lib/fields.js', 'content.js'];

  const DEFAULT_MODEL = 'gpt-4.1-mini';
  const MAX_PROFILE_CHARS = 8000;
  const MAX_CONTEXT_CHARS = 400;
  const MAX_OPTIONS = 200;

  function limitText(value, max) {
    if (value === undefined || value === null) return '';
    return String(value).replace(/\s+/g, ' ').trim().substring(0, max);
  }

  // Model names are stored by the popup; anything odd falls back to the default.
  function normalizeModel(model) {
    const name = typeof model === 'string' ? model.trim() : '';
    return /^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/.test(name) ? name : DEFAULT_MODEL;
  }

  // GPT-5 and o-series models reject `temperature` and spend tokens on reasoning.
  function isReasoningModel(model) {
    return /^(gpt-5|o\d)/i.test(model);
  }

  function buildChatCompletionRequest(model, prompt) {
    const name = normalizeModel(model);
    const body = {
      model: name,
      messages: [
        {
          role: 'system',
          content: 'You are a helpful assistant that fills out job application forms. Return only the requested value with no extra text, quotes, or explanations. Be concise and professional.'
        },
        { role: 'user', content: String(prompt) }
      ]
    };
    if (isReasoningModel(name)) {
      body.max_completion_tokens = 2000;
      body.reasoning_effort = 'low';
    } else {
      body.max_completion_tokens = 300;
      body.temperature = 0.3;
    }
    return body;
  }

  function parseChatCompletion(data) {
    const choice = data && Array.isArray(data.choices) ? data.choices[0] : null;
    const content = choice && choice.message ? choice.message.content : null;
    if (typeof content !== 'string') throw new Error('OpenAI returned no text');
    return content.trim();
  }

  // Removes wrapping quotes the model sometimes adds.
  function cleanAIResponse(text) {
    return String(text || '').replace(/^["']|["']$/g, '').trim();
  }

  const FIELD_REQUESTS = {
    email: 'the user\'s email address',
    phone: 'the user\'s phone number in standard format',
    firstName: 'only the user\'s first name',
    lastName: 'only the user\'s last name',
    fullName: 'the user\'s full name',
    address: 'the user\'s street address',
    city: 'the user\'s city',
    state: 'the user\'s state or province',
    zipCode: 'the user\'s zip or postal code',
    country: 'the user\'s country',
    company: 'the user\'s current or most recent company name',
    position: 'the user\'s current job title or desired position',
    education: 'relevant education information (degree and school)',
    skills: 'relevant skills for this field (comma-separated)',
    experience: 'brief professional experience summary',
    coverLetter: 'a brief professional response (2-3 sentences maximum)',
    linkedin: 'the user\'s LinkedIn profile URL',
    website: 'the user\'s website or portfolio URL',
    salary: 'an appropriate salary expectation based on the role',
    date: 'an appropriate date in YYYY-MM-DD format',
    workAuthorization: '"Yes" if the user is authorized to work, otherwise "No"',
    motivation: 'a brief explanation of interest or motivation (1-2 sentences)'
  };

  // `field` and `jobContext` come from the web page, so they are length-limited here.
  function buildFieldPrompt(userData, field, jobContext) {
    const f = field || {};
    const type = Object.prototype.hasOwnProperty.call(FIELD_REQUESTS, f.type) ? f.type : 'generic';
    const context = limitText(f.context, MAX_CONTEXT_CHARS);
    let prompt = `User Information:\n${limitText(userData, MAX_PROFILE_CHARS)}\n\n`;
    const page = limitText(jobContext, MAX_CONTEXT_CHARS);
    if (page) prompt += `Page Context: ${page}\n\n`;
    prompt += `Field Information:\nType: ${type}\nContext: ${context}\n` +
      `Placeholder: ${limitText(f.placeholder, 200)}\nLabel: ${limitText(f.label, 200)}\n\nPlease provide `;
    prompt += type === 'generic'
      ? `an appropriate response for this field based on the context: ${context}`
      : FIELD_REQUESTS[type];
    return prompt;
  }

  function sanitizeOptions(options) {
    if (!Array.isArray(options)) return [];
    return options.slice(0, MAX_OPTIONS).map(option => limitText(option, 200)).filter(Boolean);
  }

  function buildSelectPrompt(userData, options, fieldContext, jobContext) {
    return `User Information: ${limitText(userData, MAX_PROFILE_CHARS)}\n\n` +
      `Select the best option from these choices: ${sanitizeOptions(options).join(', ')}\n\n` +
      `Field context: ${limitText(fieldContext, MAX_CONTEXT_CHARS)}\n` +
      `Job context: ${limitText(jobContext, MAX_CONTEXT_CHARS)}\n\n` +
      'Respond with only the exact option text:';
  }

  // Index of the option that best matches the AI answer, or -1. Exact matches win over
  // partial ones, and empty option texts never match.
  function matchOption(optionTexts, answer) {
    const wanted = String(answer || '').toLowerCase().trim();
    if (!wanted) return -1;
    const texts = (optionTexts || []).map(text => String(text || '').toLowerCase().trim());
    const exact = texts.indexOf(wanted);
    if (exact >= 0) return exact;
    return texts.findIndex(text => text && (text.includes(wanted) || wanted.includes(text)));
  }

  // Masks anything that looks like an OpenAI key (OpenAI echoes part of a bad key in errors).
  function redactSecrets(text) {
    return String(text === undefined || text === null ? '' : text)
      .replace(/sk-[A-Za-z0-9_*.-]{4,}/g, 'sk-[redacted]');
  }

  const api = {
    CONTENT_SCRIPT_FILES,
    redactSecrets,
    DEFAULT_MODEL,
    limitText,
    normalizeModel,
    isReasoningModel,
    buildChatCompletionRequest,
    parseChatCompletion,
    cleanAIResponse,
    buildFieldPrompt,
    sanitizeOptions,
    buildSelectPrompt,
    matchOption,
    normalizeSearchRequest,
    shouldStartScraping,
    buildJobsFilename,
    textToDataUrl,
    normalizeFieldText,
    determineFieldType,
    buildFieldContext,
    linkedInExperienceCode,
    buildLinkedInSearchUrl,
    formatJobsForFile
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  } else {
    root.AutoApplyLib = api;
  }
})(typeof globalThis !== 'undefined' ? globalThis : this);
