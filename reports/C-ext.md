# C-ext report (T11): auto-apply-plugin

Branch `worktree-agent-a82e7af0a5d8c539a`, four item commits in the order 34, 31, 33, 32.
Everything is under `auto-apply-plugin/`. The extension is still plain JavaScript loaded by a
Manifest V3 manifest. It has no bundler and no npm dependencies.

## How to verify

- `node --test auto-apply-plugin/` runs 72 tests and all of them pass. Node resolves the directory
  through `auto-apply-plugin/package.json` `"main": "test/run-all.js"`, which loads every
  `test/*.test.js`. Inside the folder, `npm test` (`node --test test/*.test.js`) does the same.
- `node --check` passes on every `.js` file, and `manifest.json` parses as JSON.
- `mvn -q test` passes. No Java was touched.
- For every item, I ran the new tests against the files from before that item's commit
  (`git archive` into a scratch copy): 12, 11 and 24 new tests failed for items 31, 33 and 32.
  For item 34 the old `determineFieldType` gave `personal statement -> state`,
  `hotel -> phone`, `desired salary for this role -> position`,
  `why ... our company? -> company` and `website address -> address`.
- `test/harness.js` is a small fake of `chrome.*` and the DOM. It loads `background.js`,
  `content.js` and `popup.js` in `node:vm` and drives them through their real listeners.
- A manual load in Chrome was not possible here. To smoke-test by hand: load the extension
  unpacked, save a key, use Fill on a form, right-click a field, and run a LinkedIn search
  (this needs a login).

## Item 34: pure helpers in a module, with node tests

- **Wrong:** `determineFieldType` matched substrings in a fixed order over one blob of
  id/name/label/class/nearby text. So "personal statement" was `state`, "hotel" was `phone`,
  and class names and nearby text added noise. None of the pure helpers had tests.
- **Changed:** new `lib/fields.js`. It is a classic script, not an ES module, because the
  content script cannot be an ES module without a bundler or web-accessible resources. It
  exports through `module.exports` under node and through `globalThis.AutoApplyLib` in the
  browser. The service worker loads it with `importScripts`, the injected content script and
  the popup load it before their own script, and the tests use `require`.
  - The classifier normalises camelCase and separators and uses word-boundary rules in a
    deliberate priority order (for example, salary and motivation come before position and
    company).
  - It classifies each hint source separately: autocomplete, label, aria-label, placeholder,
    name, id, then nearby text. Class names are no longer used.
  - `formatJobsForFile` (now with an injectable date) and `buildLinkedInSearchUrl` moved here.
- **Tests:** `test/fields.test.js`.

## Item 31: broken pieces

- **Notifications:** they used a missing `icon.png`. I added `icons/icon16|48|128.png`,
  generated as plain PNGs, plus manifest `icons` and `action.default_icon`. Notifications now
  use `icons/icon128.png`.
- **Context-menu coordinates:** the code read `pageX/pageY`, which do not exist on
  `OnClickData`. They were dropped, along with `findElementAtCoordinates`. The content script
  uses the element it saw right-clicked and refuses one that is no longer attached to the page.
- **Undefined function:** I removed the branch that called the undefined
  `fillCustomDropdownWithAI`. Comboboxes are filled as text inputs.
- **Popup:** the log uses `textContent`. The clear, analyze and fill handlers check
  `runtime.lastError` and a missing tab.
- **Unhandled rejections:** a `notifyExtension` helper in the background and content scripts
  swallows the "no receiver" rejection from `runtime.sendMessage` when the popup is closed.
  Failures from download, notification, `openPopup` and `tabs.sendMessage` are caught.
- **Extra fix (not in the backlog):** the popup sent experience levels as names such as
  `entry`, but LinkedIn's `f_E` filter takes codes 1 to 6. `linkedInExperienceCode` maps them.
- **Tests:** `test/manifest.test.js`, `test/background.test.js`, `test/content.test.js` and
  `test/popup.test.js`.

## Item 33: LinkedIn search flow

- **Wrong:**
  - Scraping started from a 5-second popup timer followed by a 3-second background timer, so it
    never started if the popup closed.
  - Stop never reached the content script.
  - The blob URL was never revoked, and a missing `jobTitle` crashed the download.
  - The Stop button was also disabled while a search ran, so it could not be clicked.
- **Changed:**
  - **Background owns the search:** `startJobSearch` opens the tab and stores the search in
    `storage.session`. `tabs.onUpdated` with `status === 'complete'` for that tab starts
    scraping exactly once (`shouldStartScraping`). If the tab had already loaded, scraping
    starts at once.
  - **Stop:** `stopJobSearch` goes from the popup to the background, and
    `stopLinkedInScraping` goes from the background to the content script. The loop checks a
    stop flag. A stopped search still downloads partial results if it found any.
  - **State cleanup:** the search state is cleared on completion, stop, a failed start and tab
    close. A reopened popup restores the running state through `getJobSearchState`.
  - **Downloads:** the content script sends the text, and the background builds a safe file
    name (`buildJobsFilename`) and a base64 data URL. No blob URL is created at all, which
    replaces the "revoke the blob URL" sketch: the service worker cannot create blob URLs.
  - **Missing parameters:** they are normalised (`normalizeSearchRequest`).
  - **Acknowledgement:** `startLinkedInScraping` is acknowledged at once, and completion arrives
    as `jobSearchComplete`.
- **Tests:** `test/search.test.js`.

## Item 32: security

- **Wrong:**
  - `content.js` ran on `<all_urls>`, and the OpenAI key and the full profile were sent to it
    in messages.
  - OpenAI was called from the content-script context.
  - `tabs` was redundant, and `*.linkedin.com` was wider than needed.
  - The model was hardcoded to `gpt-3.5-turbo`.
- **Changed:**
  - **API calls:** every OpenAI call is in `background.js`. Content scripts send `aiFillField`
    (field type, context, label, placeholder, page context) or `aiChooseOption` (option texts).
    The worker reads the key, model and profile, builds the prompt (`buildFieldPrompt` and
    `buildSelectPrompt`, which limit the length of page text and cap the profile), calls
    OpenAI and returns only `{success, text}`. It answers only messages from this extension
    that come from a tab. Errors pass through `redactSecrets`.
  - **Storage:** `storage.local.setAccessLevel('TRUSTED_CONTEXTS')`, so content scripts cannot
    read the key.
  - **Injection on demand:** there is no manifest content script. `lib/fields.js` and
    `content.js` are injected with `scripting.executeScript` in three cases:
    - the popup's active tab (activeTab);
    - the clicked frame for the context menu (activeTab plus `frameId`);
    - the LinkedIn search tab (host permission).

    `content.js` is wrapped in a guard, so a second injection is a no-op.
  - **Permissions:** `activeTab, scripting, storage, contextMenus, downloads, notifications`.
    Host permissions are now `https://api.openai.com/*` and `https://www.linkedin.com/*` only.
  - **Model:** a new popup field. It is validated by `normalizeModel` and defaults to
    `gpt-4.1-mini`. GPT-5 and o-series models get `max_completion_tokens` and
    `reasoning_effort: low` and no `temperature`.
- **Tests:** `test/security.test.js`, plus behaviour tests in `test/popup.test.js` (a fake
  popup DOM).

## Behaviour changes and things left open

- **Context menu:** it now shows on editable fields only, titled "AI Fill This Field". An
  injected script cannot see a right-click that happened before it was injected, so it fills
  the focused field. Right-clicking a field focuses it. It falls back to the last right-click
  it saw. Right-clicking a container to fill all of its fields no longer works; use the popup's
  Fill button for the whole page.
- **Prompt injection:** a page can still use its labels to try to make the model copy profile
  data into a field on that page. This is inherent to filling forms with the profile.
  Mitigations are length limits only.
- **Full page navigation:** if LinkedIn pagination does a full page load, the injected script
  is lost and the search stays marked as started until Stop or tab close. Scraping was never
  re-injected after a navigation before this change either.
- **Profile in every call:** the profile is still sent with each field prompt (capped at
  8000 characters). Batching all fields into one call would cut this further.
- **Live check not done:** there was no live OpenAI or LinkedIn check (no credentials here).
  The default model name should be confirmed against the account.
- **Extension reload:** after reloading the extension, refresh open pages before using it, as
  before.
