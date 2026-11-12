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

  function buildLinkedInSearchUrl(params) {
    const baseUrl = 'https://www.linkedin.com/jobs/search/';
    const searchParams = new URLSearchParams();

    searchParams.append('keywords', params.jobTitle);
    if (params.location) searchParams.append('location', params.location);
    if (params.experienceLevel) searchParams.append('f_E', params.experienceLevel);
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

  const api = {
    normalizeFieldText,
    determineFieldType,
    buildFieldContext,
    buildLinkedInSearchUrl,
    formatJobsForFile
  };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = api;
  } else {
    root.AutoApplyLib = api;
  }
})(typeof globalThis !== 'undefined' ? globalThis : this);
