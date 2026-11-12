'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');

const {
  normalizeFieldText,
  determineFieldType,
  buildFieldContext,
  buildLinkedInSearchUrl,
  formatJobsForFile
} = require('../lib/fields.js');

test('normalizeFieldText splits camelCase, separators and case', () => {
  assert.equal(normalizeFieldText('firstName'), 'first name');
  assert.equal(normalizeFieldText('applicant[last_name]'), 'applicant last name');
  assert.equal(normalizeFieldText('  Phone-Number  '), 'phone number');
  assert.equal(normalizeFieldText(undefined), '');
  assert.equal(normalizeFieldText(null), '');
});

test('determineFieldType: substrings no longer misfire', () => {
  // "statement" used to match "state", "hotel" used to match "tel".
  assert.equal(determineFieldType('personal statement'), 'coverLetter');
  assert.notEqual(determineFieldType('hotel'), 'phone');
  assert.notEqual(determineFieldType('intelligence'), 'phone');
  // "pay" inside "paypal" or "display" is not a salary field.
  assert.notEqual(determineFieldType('display name'), 'salary');
  // "cell" inside "excellent" is not a phone field.
  assert.notEqual(determineFieldType('excellent'), 'phone');
});

test('determineFieldType: common labels and ids', () => {
  const cases = {
    'Email': 'email',
    'E-mail address': 'email',
    'emailAddress': 'email',
    'Phone number': 'phone',
    'mobile': 'phone',
    'Tel': 'phone',
    'First name': 'firstName',
    'first_name': 'firstName',
    'firstName': 'firstName',
    'Surname': 'lastName',
    'lastName': 'lastName',
    'Full name': 'fullName',
    'Name': 'fullName',
    'Username': 'generic',
    'user_name': 'generic',
    'City': 'city',
    'State': 'state',
    'Province': 'state',
    'Postal code': 'zipCode',
    'zipCode': 'zipCode',
    'Postal address': 'address',
    'Street address': 'address',
    'Country': 'country',
    'Company name': 'company',
    'Current employer': 'company',
    'Job title': 'position',
    'Desired salary for this role': 'salary',
    'Start date': 'date',
    'Why do you want to work at our company?': 'motivation',
    'University': 'education',
    'Key skills': 'skills',
    'Relevant experience': 'experience',
    'Cover letter': 'coverLetter',
    'LinkedIn profile URL': 'linkedin',
    'linkedinUrl': 'linkedin',
    'Website address': 'website',
    'Portfolio': 'website',
    'Are you authorized to work in the UK?': 'workAuthorization',
    'Do you require visa sponsorship?': 'workAuthorization',
    'References': 'reference',
    'Favourite colour': 'generic',
    '': 'generic'
  };
  for (const [text, expected] of Object.entries(cases)) {
    assert.equal(determineFieldType(text), expected, `"${text}"`);
  }
});

test('determineFieldType: a clear label wins over noisy nearby text and class names', () => {
  const hints = {
    label: 'Personal statement',
    name: 'statement',
    id: 'q7',
    className: 'form-control state-valid tel-input',
    nearbyText: 'Email us if you have problems. Phone: 0123'
  };
  assert.equal(determineFieldType(hints), 'coverLetter');

  // With no label, the name is used before nearby text.
  assert.equal(determineFieldType({ name: 'applicant_city', nearbyText: 'email phone' }), 'city');
  // Nearby text is only a last resort.
  assert.equal(determineFieldType({ id: 'q1', nearbyText: 'Your email' }), 'email');
  assert.equal(determineFieldType({}), 'generic');
  assert.equal(determineFieldType(null), 'generic');
});

test('determineFieldType: autocomplete attribute takes priority', () => {
  assert.equal(determineFieldType({ autocomplete: 'given-name', label: 'Name' }), 'firstName');
  assert.equal(determineFieldType({ autocomplete: 'shipping postal-code' }), 'zipCode');
  assert.equal(determineFieldType({ autocomplete: 'off', label: 'Email' }), 'email');
  // Unknown or inherited keys are ignored.
  assert.equal(determineFieldType({ autocomplete: 'toString', label: 'City' }), 'city');
});

test('buildFieldContext joins the useful hints and leaves out class names', () => {
  const context = buildFieldContext({
    label: ' First   name ',
    placeholder: 'Jane',
    className: 'form-control',
    nearbyText: 'Applicant details'
  });
  assert.equal(context, 'First name | Jane | Applicant details');
  assert.ok(!context.includes('form-control'));
  assert.equal(buildFieldContext({ label: 'x'.repeat(500) }).length, 200);
  assert.equal(buildFieldContext({ label: 'abcdef' }, 3), 'abc');
});

test('buildLinkedInSearchUrl encodes the search parameters', () => {
  const url = new URL(buildLinkedInSearchUrl({ jobTitle: 'C++ Developer & Lead', location: 'London, UK' }));
  assert.equal(url.origin + url.pathname, 'https://www.linkedin.com/jobs/search/');
  assert.equal(url.searchParams.get('keywords'), 'C++ Developer & Lead');
  assert.equal(url.searchParams.get('location'), 'London, UK');
  assert.equal(url.searchParams.get('f_JT'), 'F');
  assert.equal(url.searchParams.get('sortBy'), 'DD');
  assert.equal(url.searchParams.has('f_E'), false);
});

test('buildLinkedInSearchUrl maps experience levels to LinkedIn f_E codes', () => {
  const code = (level) => new URL(buildLinkedInSearchUrl({ jobTitle: 'x', experienceLevel: level })).searchParams.get('f_E');
  assert.equal(code('internship'), '1');
  assert.equal(code('entry'), '2');
  assert.equal(code('associate'), '3');
  assert.equal(code('mid'), '4');
  assert.equal(code('senior'), '4');
  assert.equal(code('director'), '5');
  assert.equal(code('executive'), '6');
  assert.equal(code('3'), '3');
  assert.equal(code('wizard'), null);
  assert.equal(code(''), null);
});

test('buildLinkedInSearchUrl leaves out an empty location', () => {
  const url = new URL(buildLinkedInSearchUrl({ jobTitle: 'Engineer', location: '' }));
  assert.equal(url.searchParams.has('location'), false);
});

test('formatJobsForFile lists every job with a header', () => {
  const now = new Date('2026-01-02T10:00:00Z');
  const jobs = [
    { id: '1', title: 'Dev', company: 'Acme', location: 'London', posted: '1 day ago',
      link: 'https://www.linkedin.com/jobs/view/1', description: 'Build things', scraped: now.toISOString() },
    { id: '2', title: 'QA', company: 'Beta', location: 'Leeds', posted: 'today',
      link: 'https://www.linkedin.com/jobs/view/2', description: '', scraped: now.toISOString() }
  ];
  const text = formatJobsForFile(jobs, 'Dev', 'London', now);
  assert.match(text, /^# LinkedIn Job Search Results\nSearch Query: Dev in London\n/);
  assert.match(text, /Total Jobs Found: 2\n/);
  assert.match(text, /JOB #1\n[=]+\nTitle: Dev\nCompany: Acme\nLocation: London\n/);
  assert.match(text, /JOB #2\n[=]+\nTitle: QA\n/);
  assert.match(text, /Description:\nNo description available\n/);
  assert.match(text, /Link: https:\/\/www\.linkedin\.com\/jobs\/view\/1\n/);
});

test('formatJobsForFile handles no location and no jobs', () => {
  const text = formatJobsForFile([], 'Dev', '', new Date('2026-01-02T10:00:00Z'));
  assert.match(text, /Search Query: Dev\n/);
  assert.match(text, /Total Jobs Found: 0\n/);
  assert.ok(!text.includes('JOB #'));
});

test('lib/fields.js loads as a classic script and exposes AutoApplyLib', () => {
  // This is how the service worker (importScripts) and the content script load it.
  const fs = require('node:fs');
  const path = require('node:path');
  const vm = require('node:vm');
  const source = fs.readFileSync(path.join(__dirname, '..', 'lib', 'fields.js'), 'utf8');
  const context = vm.createContext({ URLSearchParams });
  vm.runInContext(source, context);
  assert.equal(typeof context.AutoApplyLib.determineFieldType, 'function');
  assert.equal(context.AutoApplyLib.determineFieldType('Email'), 'email');
});
