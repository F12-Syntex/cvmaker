# BACKLOG.md (audit report)

Audited on 2026-11-12, at commit `ccc0ca0`. I read every Java file under `src/main/java/com/cvmaker`, the 3 properties files, `pom.xml`, `.gitignore`, all 5 files in `auto-apply-plugin/`, `gitinfo.py` and `create-file-structure.js`. I did not edit any source file.

All paths are relative to the repo root. `J/` stands for `src/main/java/com/cvmaker/`.

**Already fixed in `ccc0ca0`, so not listed below:**
- The invalid compiler level `1.21`.
- The missing `crawler.cache.PageInputCacheManagerCSV` class, which `.gitignore` `cache/` had hidden.
- JUnit 5, AssertJ and Mockito, with surefire 3.5.2.
- `Dotenv.load()` crashing when `.env` is missing.

The remaining work on those points is in items 23 and 28.

**For the repo owner:**
- **No API keys, tokens or OAuth secrets** are in the working tree or the git history. I searched for `sk-`, `AIza`, `ghp_`, `GOCSPX-` and `client_secret`.
- **Personal CV data is still in git history.** These files were deleted but remain in older commits: `userdata.txt`, `userdata_ideal.txt` (it has contact-detail lines), `generation/Lead_UAT_Analyst_Unknown_Company/cv.pdf`, `generation/Lead_UAT_Analyst_Unknown_Company/cover_letter.pdf` and `generated_cv.*`. Removing them needs a history rewrite, which is the owner's decision.
- **`J/crawler/cache/PageInputCacheManagerCSV.java` was rebuilt from its call site.** If the owner has the original locally, it should replace the rebuilt file.

---

## Items (ordered by value)

### 1. GenericCrawler is wired to a null Page and closes stdin
- type: bug
- files: `J/crawler/generic/GenericCrawler.java`
- evidence: At `GenericCrawler.java:23-24`, the constructor builds `FormAnalyzer`/`FormFiller` with `page`. At that point `page` is still null, because it is only set in `setupBrowser()` (`J/crawler/AbstractJobCrawler.java:86`). So `analyzeForm()` NPEs, and the NPE is swallowed at `FormAnalyzer.java:50`. Also, `try (Scanner scanner = new Scanner(System.in))` at `:38` closes `System.in`, so the later `System.in.read()` in `FormFiller.java:58` throws.
- fix sketch: Override `setupBrowser()` and create the analyzer and filler after `super.setupBrowser()`, as `ReedCrawler.java:25-32` already does. Read the line with a non-closing reader. Add a Mockito test that `processJobsAndApply` gets a non-null page.
- size: S
- needs-network-or-credentials: no

### 2. GPT-5 model is called with a temperature it does not accept, and maxTokens is ignored
- type: bug
- files: `J/service/ai/OpenAIClient.java`, `J/service/ai/LLMModel.java`
- evidence: `OpenAIClient.java:22` always sends `temperature`. `LLMModel.GPT_5_1_MINI` (`LLMModel.java:9`, which is really `gpt-5-mini`) is used with temperature 0.7 and 0.5 (`FormAnalyzer.java:27`, `FormFiller.java:29`), but the gpt-5 family only accepts the default temperature, so these calls return HTTP 400. `LLMRequest.maxTokens` is never sent (`OpenAIClient.java:19-23`). `completion.usage().get()` at `:27` throws when usage is absent.
- fix sketch: Add a `supportsTemperature` flag to `LLMModel` and skip the parameter when it is false. Map `maxTokens` to `maxCompletionTokens`. Handle a missing usage block with `usage().orElse`. Unit-test the parameter building with a small seam that does not touch the network.
- size: S
- needs-network-or-credentials: no for the unit test; a live call needs OPENAI_API_KEY

### 3. Email category is never parsed (AI fields thrown away)
- type: bug
- files: `J/application/management/EmailAnalysisService.java`, `J/application/management/EmailCategory.java`
- evidence: The prompt at `EmailAnalysisService.java:175` asks for one of the display names from `EmailCategory.getAllCategories()`, such as "Interview Invitation". But `:248` parses the answer with `EmailCategory.valueOf(category)`, so it always throws `IllegalArgumentException`. When the category is null, it throws NPE instead. The NPE is not caught at `:249`, so it reaches the outer catch (`:274-276`), which replaces all AI-extracted fields with rule-based fallback data.
- fix sketch: Add `EmailCategory.fromLabel(String)`. It should be null-safe, ignore case, and accept both the enum name and the display name. Use it at `:248`. Add tests.
- size: S
- needs-network-or-credentials: no

### 4. The rule-based email fallback rejects almost every email
- type: bug
- files: `J/application/management/EmailAnalysisService.java`
- evidence: In `isJobRelatedRuleBased` (`:335-375`), `nonJobKeywords` includes "all", "update", "general", "connect", "network" and "invitation", matched with `contains`. "all" alone matches "call", "small" and "finally", so the fallback returns false for nearly every email. Those emails are then marked processed for good (item 5). `categorizeEmailRuleBased` (`:377-400`) has the same substring problem ("test" matches "latest").
- fix sketch: Move both methods into a pure `EmailRuleClassifier` that matches on word boundaries and uses a trimmed keyword list. Add table-driven tests.
- size: S
- needs-network-or-credentials: no

### 5. Emails are marked processed even when AI analysis failed
- type: robustness
- files: `J/application/management/ApplicationManager.java`, `J/application/management/EmailAnalysisService.java`
- evidence: `updateApplicationDatabase` (`ApplicationManager.java:113-121`) writes every email ID to `processed_emails.txt`. That includes emails where the AI call failed (network or quota error) and `analyzeEmail` quietly returned fallback data (`EmailAnalysisService.java:41-44`). Those emails are never retried.
- fix sketch: Make `analyzeEmail` report that it degraded, either with a result flag or a checked exception. Then skip `saveProcessedEmailId` for transient failures. Test this with a fake `AiService` that throws.
- size: S
- needs-network-or-credentials: no

### 6. JSON parsing of LLM output is hand-rolled with regex and substring
- type: bug
- files: `J/application/management/EmailAnalysisService.java`, `J/crawler/generic/utils/AIResponseParser.java`, new `J/util/LlmJson.java`
- evidence:
  - `extractJsonValue` (`EmailAnalysisService.java:310-318`) breaks on escaped quotes and nested objects. It also turns JSON `null` into the string "null", which then appears as a company named "null" in the sheet.
  - Only a leading "```json" fence is stripped (`:219-224`).
  - `AIResponseParser.java:26-29` takes a substring from `indexOf("{")`, which throws when there is no brace, and flattens nested objects with `Map.toString`.
- fix sketch: Add one shared helper, `LlmJson.extractObject(String) -> Optional<JsonNode>`. It should strip code fences, find the outermost balanced object and parse it with Jackson. Use it in both classes. Add tests built from real-looking LLM outputs.
- size: M
- needs-network-or-credentials: no

### 7. Local application database corrupts records, and CSV export is unsafe
- type: bug / security
- files: `J/application/management/JobApplicationData.java`, `J/application/management/DataStorage.java`
- evidence:
  - `serialize()` (`JobApplicationData.java:154-186`) escapes `||` only in `extractedInfo`. A newline in any field breaks the line-based loader (`DataStorage.java:79-87`).
  - `split("\\|\\|")` at `:189` has no `-1` limit, so trailing empty fields are dropped. A field that ends in `|` produces `|||`, which shifts the columns.
  - The CSV export (`DataStorage.java:44-58`) does not neutralise cells starting with `=`, `+`, `-` or `@` (formula injection, and email subjects are attacker-controlled). It also uses a locale-dependent `%.2f`, and `FileWriter`/`Scanner` use the platform charset.
- fix sketch: Store one JSON object per line using Jackson, with UTF-8. Keep a reader for the old `||` format so existing files still load. In the CSV export, prefix risky cells with `'` and use `Locale.ROOT`. Add round-trip tests with `@TempDir`.
- size: M
- needs-network-or-credentials: no

### 8. Google Sheets formula injection through HYPERLINK
- type: security
- files: `J/application/management/GoogleSheetsService.java`
- evidence: `GoogleSheetsService.java:280`, `:294`, `:582` and `:596` build `"=HYPERLINK(\"" + url + "\", ...)"` from `applicationUrl`, which comes from AI/email content, and `emailLink`. The URL is not escaped or validated, so a `"` in it injects arbitrary formula text.
- fix sketch: Accept only `http`/`https` URLs, double any `"`, and leave the cell empty otherwise. Put the formula building in a pure helper and test it.
- size: S
- needs-network-or-credentials: no

### 9. AI form filling never sees the applicant's data, so the LLM invents it
- type: bug
- files: `J/crawler/generic/FormAnalyzer.java`, `J/crawler/generic/FormFiller.java`
- evidence: `createAnalysisPrompt` (`FormAnalyzer.java:130-151`) and `getAIValueForField` (`FormFiller.java:75-102`) contain only page and field text. They never include the user's CV data (`ConfigManager.getUserDataContent`, which `AbstractJobCrawler.config` already holds), so names, emails and answers are invented.
- also:
  - Each visible field triggers its own LLM call, even when `formContext` already has the value.
  - `cleanAISuggestion` uses ```` ```.*?``` ```` without DOTALL (`:107`).
  - Neither class shuts down its `AiService`.
- fix sketch: Pass `userData` into both prompts. Look up `formContext` by identifier, name or label first, and call the AI only for fields still missing. Add `shutdown()`. Unit-test the prompt builders.
- size: M
- needs-network-or-credentials: no for the prompt tests

### 10. FieldHandler cannot fill select/textarea, and silently picks the first option
- type: bug
- files: `J/crawler/FormField.java`, `J/crawler/generic/FieldHandler.java`
- evidence:
  - `FormField.getType()` (`FormField.java:95-97`) returns "text" for `<select>`/`<textarea>`, which have no `type` attribute. So the `select`/`textarea` branches (`FieldHandler.java:32-38`) never run, and selects get `fill()`, which throws.
  - The select and radio fallbacks pick the first option (`:75-78`, `:98`), which can answer eligibility or legal questions wrongly without anyone noticing.
  - `verify` (`:120-127`) uses `inputValue`, which is always "on" for checkboxes.
- fix sketch: Store `tagName` in `FormField` and dispatch on tag, then type. Remove the first-option fallback: skip the field and report it as unfilled. Check checkboxes with `isChecked()`. Add Mockito tests with a mocked `Locator`.
- size: S
- needs-network-or-credentials: no

### 11. LaTeX compile step leaks its reader, can hang, and has filename bugs
- type: bug
- files: `J/CVGenerator.java`
- evidence:
  - The `BufferedReader` at `:249` is never closed, and `proc.waitFor()` at `:266` has no timeout.
  - `texFileName.replace(".tex", ".pdf")` at `:269` replaces the text anywhere in the name.
  - The compiler log is printed only when `isSaveAiResponses()` is set (`:274`), which is the wrong flag.
  - `cleanupIntermediateFiles` (`:218-229`) swallows errors.
  - The CV and cover-letter methods (`:92-163`) are copy-paste duplicates.
- fix sketch: Use try-with-resources and `waitFor(timeout)`, then `destroyForcibly`. Swap only the suffix. Always print the last N log lines on failure. Merge the two generate methods into one `generateDocument(kind)`. Test with a fake compiler, using the configurable `latex.compiler` set to a shell script under `@TempDir`.
- size: S
- needs-network-or-credentials: no

### 12. AiService ignores the configured timeout and retries, and its prompt builders are duplicated and untested
- type: robustness / test
- files: `J/service/ai/AiService.java`
- evidence:
  - `ConfigManager` loads `ai.timeout_seconds`, `ai.max_retries` and `ai.request_delay_ms` (`ConfigManager.java:201-205`), but nothing reads them.
  - `waitForCompletionWithProgress` (`AiService.java:100-124`) polls with no deadline. The `future.get(60s)` call comes only after the result is already done.
  - The `AiService(LLMProvider)` constructor (`:32-37`) ignores its argument.
  - `:23` always prints the Ollama URL.
  - The two prompt builders (`:153-192`) differ only by a label. They append `ai_prompt` with no separator, and a null prompt becomes the text "null".
  - `extractLatexFromResponse` (`:126-151`) NPEs on null.
- fix sketch: Add an options object (timeout, retries, backoff) and use `future.get(timeout)`. Retry on failure. Merge the builders into one null-safe builder and make it package-private. Add tests for the fence and `\documentclass` stripping, and retries with a fake `LLMClient`. After this, the `CVGenerator.java:25` call site can pass the options (C-cvgen).
- size: M
- needs-network-or-credentials: no

### 13. AiService executors and clients leak; crawler state is left changed on error
- type: robustness
- files: `J/crawler/AbstractJobCrawler.java`, `J/App.java`
- evidence:
  - Every `AiService` starts a non-daemon single-thread executor (`AiService.java:17`).
  - `generateCVForJob` builds a new `CVGenerator`, and so a new OpenAI client, for every job (`AbstractJobCrawler.java:176`).
  - When generation throws, it skips `generator.shutdown()` and leaves `config.outputDirectory` pointing at the job folder (`:179-187`).
  - `App.java:25-29` calls `shutdown()` outside a `finally` block.
- fix sketch: Keep one `CVGenerator` per crawler and shut it down in `close()`. Use try/finally to restore the output directory. Move `App`'s shutdown into `finally`.
- size: S
- needs-network-or-credentials: no

### 14. Playwright setup and close are not exception-safe
- type: robustness
- files: `J/crawler/AbstractJobCrawler.java`, `J/crawler/CrawlerManager.java`
- evidence:
  - `setupBrowser()` (`AbstractJobCrawler.java:60-101`) catches and prints launch failures. That leaves `page` null, which causes an NPE later in `openForLogin`/`processJobsAndApply`, and it leaks the `Playwright` instance.
  - `close()` (`:118-129`) stops at the first exception.
  - `close()` is also called twice: in the task's `finally` (`CrawlerManager.java:79`) and again in `shutdown()` (`:108`).
- fix sketch: Rethrow from `setupBrowser`. In `close()`, close each resource in its own try, set the fields to null, and make the method idempotent. Test with mocked `Playwright`/`BrowserContext`.
- size: S
- needs-network-or-credentials: no (with mocks)

### 15. Config parsing crashes on typos and has wrong defaults
- type: robustness / test
- files: `J/configuration/ConfigManager.java`, `J/configuration/CrawlerConfig.java`
- evidence:
  - Every number is read with a bare `Integer.parseInt` (`CrawlerConfig.java:156-196`, `ConfigManager.java:202-216`), so one typo stops startup with a `NumberFormatException`.
  - List values are split on "," without trimming (`ConfigManager.java:211`, `:214`).
  - Temperature is clamped to 0–1, but OpenAI accepts 0–2 (`:187`).
  - The default `cvConfigFile` is "config.properties" (`CrawlerConfig.java:104`), which does not exist; the real path is `configuration/config.properties`.
  - `INDEED_CONFIG_FILE` is unused (`:16`).
  - `jobDescriptionContent` stays null when there is neither a file nor a URL (`ConfigManager.java:224-228`).
  - `@Data`/`@ToString` (`ConfigManager.java:17-18`) would print the user's full CV and prompts if the config is ever logged.
- fix sketch: Add shared typed getters (`intProp`, `boolProp`, `listProp`) that fall back to the default with a warning. Fix the defaults. Exclude the content fields from `toString`. Add tests with `@TempDir` properties files.
- size: M
- needs-network-or-credentials: no

### 16. Unsafe Chromium flags are on by default for a logged-in persistent profile
- type: security
- files: `J/configuration/CrawlerConfig.java`, `configuration/reedcrawler.properties`
- evidence: The default browser args (`CrawlerConfig.java:122`, `reedcrawler.properties:24`) include `--disable-web-security` and `--no-sandbox`. They apply to a persistent profile (`playwright-session`) that holds the user's logins, and the generic and form crawlers visit arbitrary sites with it.
- fix sketch: Remove both flags from the defaults and the properties file. Document an explicit opt-in.
- size: S
- needs-network-or-credentials: no

### 17. emailTimestamp records processing time instead of email time, so the "latest status" is effectively random
- type: bug
- files: `J/application/management/JobApplicationData.java`, `J/application/management/GmailService.java`
- evidence: The `JobApplicationData` constructor sets `emailTimestamp = now()` (`JobApplicationData.java:51`). `GoogleSheetsService.isNewerApplication` (`:393-443`) uses it to choose which status to show per company. Emails are processed in `HashSet` order (`GmailService.java:93`), so the "latest" status depends on processing order.
- fix sketch: Set `emailTimestamp` from Gmail's `internalDate`, or by parsing the RFC 2822 `Date` header, which the constructor already receives. Add a parser test.
- size: S
- needs-network-or-credentials: no

### 18. Two status rankings that contradict each other
- type: bug / refactor
- files: new `J/application/management/ApplicationStatus.java`, `J/application/management/GoogleSheetsService.java`, `J/application/management/ApplicationManager.java` (lines 179-187 only)
- evidence:
  - `ApplicationManager.getStatusPriorityMap` (`:179-187`) ranks "Rejected" highest. `GoogleSheetsService.isMoreAdvancedStatus` (`:472-515`) ranks it -1 and treats any unknown status as more advanced.
  - The keys are exact strings ("Offer"), while the AI returns "Offered" or "Interview Scheduled".
  - The conditional formatting matches only exact uppercase values (`:959`, `:986`, `:1013`).
- fix sketch: Add one `ApplicationStatus` enum with `normalize(String)` and a single rank, and use it in both places. Add tests.
- size: M
- needs-network-or-credentials: no

### 19. Pull the pure logic out of the 1141-line GoogleSheetsService and test it
- type: refactor / test
- files: `J/application/management/GoogleSheetsService.java`, new `J/application/management/sheets/SheetRowMapper.java`, new `J/application/management/sheets/DateParsing.java`
- evidence:
  - There are three overlapping date parsers: `formatDateToHumanReadable` (`:717`), `parseAnyDateFormat` (`:446`), and `parseEmailDate` (`:799`), which is dead code.
  - The `MMM`/`MMMM` patterns at `:759-762` have no `Locale.ENGLISH`.
  - The hyperlink request builders are copy-pasted (`:248-313` and `:554-604`).
  - `getColumnLetter` (`:1127-1141`) says in its comment that it is 0-based, but it is 1-based.
  - `createRowData` and `combineNotes` have no tests.
- fix sketch: Move the date parsing, row mapping, notes and column-letter code into pure classes and delete the dead parser. Add unit tests: dates in RFC 2822, ISO and dd/MM forms; a column-letter table (1→A, 26→Z, 27→AA); and the notes order.
- size: M
- needs-network-or-credentials: no

### 20. Spreadsheet init creates a new spreadsheet on any error and never saves its ID
- type: robustness
- files: `J/application/management/GoogleSheetsService.java`
- evidence:
  - `initializeSpreadsheet` (`:160-174`) catches every `IOException`, including 403s and network errors, and creates a new spreadsheet.
  - The new ID is only printed, so every run with an empty `GOOGLE_SHEETS_ID` creates another spreadsheet.
  - Every request hardcodes `setSheetId(0)` (`:274`, `:699`, `:918`, and others).
- fix sketch: Create a spreadsheet only when the ID is empty or the response is a 404. Save the new ID, for example to `system_state.txt` or `.env` with a printed instruction. Look up the real `sheetId` of the first sheet.
- size: S
- needs-network-or-credentials: yes (Google OAuth)

### 21. OAuth scopes are too broad, and the auth code is duplicated
- type: security
- files: `J/application/management/GmailService.java`, `J/application/management/GoogleSheetsService.java`, new `J/application/management/GoogleAuthProvider.java`
- evidence: `GmailService.java:33-38` requests full `DRIVE` and `GMAIL_LABELS`, which nothing uses. The same OAuth flow is copied into `GoogleSheetsService.java:133-158`. Both copies hardcode port 8888 and share the token key "user" while asking for different scopes, so whichever service authorizes first decides the stored scopes.
- fix sketch: Add one `GoogleAuthProvider` with exactly `gmail.readonly` and `spreadsheets`, and a configurable port. Both services should use it.
- size: M
- needs-network-or-credentials: yes (Google OAuth to verify)

### 22. Gmail fetch reads only one page, decodes with the platform charset, and has null bugs
- type: robustness
- files: `J/application/management/GmailService.java`
- evidence:
  - Each query reads only the first page of results (`maxResults` 500, no `nextPageToken`) (`:100-104`).
  - `new String(data)` at `:167` and `:182` uses the platform charset.
  - `mimeType.startsWith` at `:176` NPEs when the MIME type is null.
  - The text/plain and text/html parts are both appended, so the body is duplicated.
  - `getPayload().getHeaders()` at `:139` is used without a null check.
  - The date range is hardcoded to empty (`:71`).
- fix sketch: Paginate. Decode as UTF-8 and prefer text/plain. Make the code null-safe. Move body decoding into a pure static method and test it with hand-built `MessagePart` objects.
- size: S
- needs-network-or-credentials: no for the decoder test

### 23. ApplicationConfig: no environment-variable fallback, no validation, dead getters
- type: robustness
- files: `J/application/management/ApplicationConfig.java`
- evidence:
  - After `ccc0ca0` a missing `.env` no longer crashes, but every getter (`:15-37`) silently returns null. The null client ID then surfaces as an obscure OAuth failure.
  - `getGoogleSheetsId` and `getGoogleSheetsSecret` (`:23-29`) are unused.
  - The data files are hardcoded relative paths (`:8-11`).
- fix sketch: Fall back to `System.getenv`. Add `require(String key)` with a clear error message. Delete the unused getters. Add an optional data-directory setting.
- size: S
- needs-network-or-credentials: no

### 24. Reed search loop indexes the wrong cards and barely waits
- type: bug
- files: `J/crawler/reed/JobSearchService.java`, `J/crawler/reed/JobApplicationService.java`, `J/crawler/reed/ReedCrawler.java`, new `J/crawler/CrawlDelays.java`
- evidence:
  - `page.waitForTimeout(config.getCrawlingSpeed())` (`JobSearchService.java:67`, `:79`) waits 1–10 ms instead of `searchResultsDelay`.
  - `findNextEasyApplyJob` (`:101-105`) uses the crawler-wide `jobsChecked` as the index into each selector list in turn. Several selectors overlap (`.job-card` and `[class*='job-card_jobCard']`), and `[data-qa*='job']` matches far too much.
  - After clicking a card and applying, the code never returns to the results page (`ReedCrawler.java:49-70`).
  - `adjustedDelay` is copy-pasted (`JobApplicationService.java:192-195` and `AbstractJobCrawler.java:205-210`).
- fix sketch: Resolve the first matching selector once per results page and keep a local index. Go back to (or re-query) the results after each application. Move `adjustedDelay` into a pure `CrawlDelays` class with tests. C-crawler-core later makes `AbstractJobCrawler` delegate to it.
- size: M
- needs-network-or-credentials: yes for an end-to-end run (live site); the delay math can be tested offline

### 25. Label lookup is copied three times and builds selectors that break
- type: refactor / bug
- files: `J/crawler/FormField.java`, `J/crawler/generic/utils/FieldExtractor.java`, `J/crawler/form/FormCrawler.java`, `J/crawler/generic/FieldHandler.java`
- evidence:
  - The selector `label[for='" + id + "']` is built by string concatenation in three places (`FormField.java:58`, `FieldExtractor.java:90`, `FormCrawler.java:469`), and `FormCrawler.java:254` uses `"#" + id`. Both break on ids containing quotes, `$` or `:`, which ASP.NET produces; `niceName` at `FormCrawler.java:405` already handles those ids.
  - `FieldHandler.java:87` concatenates the radio `name` the same way.
  - The "nearest preceding label" heuristic is also copied.
- fix sketch: Add one `LabelResolver` that builds attribute selectors with proper CSS string escaping, and test the escaping as a pure function. Replace all copies with it.
- size: S
- needs-network-or-credentials: no

### 26. FormCrawler classification uses substring matching that misfires
- type: test / refactor
- files: `J/crawler/form/FormCrawler.java`, new `J/crawler/form/FieldClassifier.java`
- evidence:
  - `isApplicant` (`:372-379`) matches "name", "tel" and "git" as substrings, so every `*Name` key, "hotel" and "digit" all match.
  - The `humanize` step `([a-f0-9]{6,})` (`:489`) deletes real words such as "facade" and "accede".
  - None of this is tested.
- fix sketch: Move the logic into a pure `FieldClassifier.classify(tag, type, key, label)` that matches on word boundaries. Add table-driven tests.
- size: S
- needs-network-or-credentials: no

### 27. Job-URL mode generates a CV for a fake job
- type: bug / feature
- files: `J/JobDataFetcher.java`
- evidence:
  - `fetchFromUrl` (`:71-83`) is a stub. It returns "Software Engineer" at "Tech Company" with the description "Job description from URL: <url>", so `input.job.url` produces a CV for a made-up job.
  - `isFilePath` (`:47-52`) mixes `||` and `&&` without parentheses and NPEs on null.
  - The `URI uri` variable is unused.
- fix sketch: Fetch the page with `java.net.http` and extract text with jsoup, which is already in the pom. Get the title and company from `og:title`, `<title>` and JSON-LD `JobPosting`. Test the parser on saved HTML fixtures under `src/test/resources`.
- size: M
- needs-network-or-credentials: no for the parser tests; yes for a live fetch

### 28. Clean up pom dependencies and add runnable packaging
- type: build
- files: `pom.xml`
- evidence:
  - These dependencies are never imported: `selenium-chrome-driver`, `selenium-support`, `webdrivermanager`, `itext7-core`, `commons-exec`, `jsoup` (until item 27) and `org.json`.
  - Jackson is imported directly in `LocalLLMClient`, `AIResponseParser` and `ExaSearchClient`, but it only arrives transitively through openai-java.
  - Lombok is compile scope with no `annotationProcessorPaths`. On JDK 23 and later, implicit annotation processing is off by default.
  - There is no exec or shade configuration for the four `main` classes: `App`, `JobCrawlerApp`, `ApplicationManager` and `ExaSearchClient`.
  - `<url>` is still `example.com`.
- fix sketch:
  - Remove the unused artifacts. Coordinate with item 27 before removing jsoup.
  - Declare `jackson-databind` explicitly.
  - Make Lombok `provided` and add it to `annotationProcessorPaths`.
  - Add `exec-maven-plugin` with a profile per main class, or a shaded jar.
- size: S
- needs-network-or-credentials: Maven Central only

### 29. Add a CI workflow
- type: build
- files: new `.github/workflows/ci.yml`
- evidence: There is no CI configuration anywhere in the repo.
- fix sketch: Run on push and PR with Temurin 21 and the Maven cache: `mvn -B verify`. Also run `node --check auto-apply-plugin/*.js` and, after item 34, `node --test`. Run `python -m py_compile gitinfo.py`.
- size: S
- needs-network-or-credentials: no (GitHub-hosted)

### 30. Add a README and .env.example
- type: docs
- files: new `README.md`, new `.env.example`
- evidence: There is no README. The required environment variables appear only in code: `OPENAI_API_KEY` (`OpenAIClient.java:13` `fromEnv`), `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_SHEETS_ID`, `EXA_API_KEY` (`ApplicationConfig.java`) and `GITHUB_KEY` (`gitinfo.py:99`). pdflatex and the Playwright browsers are also undocumented requirements.
- fix sketch: Write sections for what it does, setup (JDK 21, pdflatex, `mvn exec`), the `data/` input files, a properties reference, the three entry points, OAuth first run, loading the extension unpacked, and privacy notes. Create `.env.example` with placeholder values only.
- size: S
- needs-network-or-credentials: no

### 31. Extension: broken pieces in background, content and popup scripts
- type: bug
- files: `auto-apply-plugin/background.js`, `auto-apply-plugin/content.js`, `auto-apply-plugin/popup.js`
- evidence:
  - Notifications use `iconUrl: 'icon.png'` (`background.js:44`), but no `icon.png` exists, so every notification fails.
  - `contextMenus` `OnClickData` has no `pageX`/`pageY`, so `background.js:28-29` always sends 0. Even with real values, `elementFromPoint` (`content.js:41-44`) expects viewport coordinates, not page coordinates.
  - `fillCustomDropdownWithAI` is called at `content.js:501` but never defined.
  - `addLog` uses `innerHTML` with error text (`popup.js:137`).
  - The clear and analyze handlers never check `chrome.runtime.lastError` (`popup.js:323-359`).
  - `chrome.runtime.sendMessage` with no listener (`background.js:100`) produces unhandled rejections.
- fix sketch: Add an icon (or drop `iconUrl` and use the manifest icons). Use `lastClickedElement` only. Remove the dead combobox branch or implement it. Use `textContent`. Check `lastError` and catch the promise rejections.
- size: S
- needs-network-or-credentials: no (`node --check` plus a manual load)

### 32. Extension: the API key is sent to content scripts on every site
- type: security
- files: `auto-apply-plugin/manifest.json`, `auto-apply-plugin/background.js`, `auto-apply-plugin/content.js`, `auto-apply-plugin/popup.js`
- evidence:
  - `content.js` is injected into `<all_urls>` (`manifest.json:24-28`).
  - The OpenAI key is passed to it in messages (`background.js:24-30`, `popup.js:289-292`).
  - `content.js` calls OpenAI from the content-script context (`content.js:546-582`), where the extension's `host_permissions` do not apply.
  - `tabs` is redundant alongside `activeTab`.
  - The model is hardcoded to the deprecated `gpt-3.5-turbo` (`:555`).
  - The full `userData` is sent with every field prompt.
- fix sketch: Move every OpenAI call into the service worker and have content scripts request completions by message, so the key stays out of them. Inject `content.js` on demand with `scripting.executeScript` instead of `<all_urls>`. Make the model configurable.
- size: M
- needs-network-or-credentials: yes for a live OpenAI check

### 33. Extension: LinkedIn search depends on popup timers and Stop does nothing
- type: bug
- files: `auto-apply-plugin/popup.js`, `auto-apply-plugin/background.js`, `auto-apply-plugin/content.js`
- evidence:
  - Scraping starts from a 5-second `setTimeout` in the popup (`popup.js:243-250`) followed by another 3 s in the background (`background.js:70`). If the popup closes first, which it does whenever it loses focus, scraping never starts.
  - `stopJobSearch` (`popup.js:258-272`) never notifies the content script, so `isSearching` stays true (`content.js:140`).
  - The blob URL is never revoked (`content.js:387`).
  - `jobTitle.replace` crashes when `jobTitle` is undefined (`:388`).
- fix sketch: Start scraping from the background on `tabs.onUpdated` with `status === 'complete'` for the stored `searchTabId`. Add a `stopLinkedInScraping` message. Revoke the blob URL after the download starts. Guard against missing parameters.
- size: M
- needs-network-or-credentials: no for the logic; a manual LinkedIn check needs a login

### 34. Extension: move the pure helpers into a module and test them with node
- type: test
- files: `auto-apply-plugin/content.js`, `auto-apply-plugin/background.js`, new `auto-apply-plugin/lib/fields.js`, new `auto-apply-plugin/test/fields.test.js`
- evidence: `determineFieldType` (`content.js:624-651`) misfires because checks run in order and match substrings: "personal statement" matches `state`, "hotel" matches `tel`, and the class names and nearby text add noise. `formatJobsForFile` (`:351-383`) and `buildLinkedInSearchUrl` (`background.js:83-94`) are pure functions with no tests.
- fix sketch: Move these functions into an ES module, imported from the service worker and loaded first for the content script. Fix the matching order and use word boundaries. Add `node --test` tests (Node 22 is available).
- size: S
- needs-network-or-credentials: no

### 35. Full API bodies are logged, and the Exa client ignores its options
- type: security / bug
- files: `J/service/ai/LocalLLMClient.java`, `J/websearch/ExaSearchClient.java`, `J/websearch/SearchService.java`
- evidence:
  - Full request and response bodies are printed (`LocalLLMClient.java:112-113`, `ExaSearchClient.java:49`, `:80-81`, `:139-140`). They contain CV and email content.
  - `search()` ignores `numResults`, `includeDomains` and `excludeDomains` (`ExaSearchClient.java:43-46`).
  - It reads `published_date` (`:159`), but the Exa API returns `publishedDate`.
  - A null API key causes an NPE when the header is added (`:53`).
  - `testConnection` hardcodes `localhost:1234` (`LocalLLMClient.java:43`), and its log message is missing a "/" (`:55`).
- fix sketch: Log only the status and body length, with full bodies behind a debug flag. Send the request options. Fix the field name. Validate the key. Test request and response JSON mapping with a stubbed `OkHttpClient` interceptor.
- size: S
- needs-network-or-credentials: no

### 36. Make the Reed selectors configurable and act on (or delete) the unused crawler settings
- type: feature
- files: `J/configuration/CrawlerConfig.java`, `configuration/reedcrawler.properties`, `J/crawler/reed/JobSearchService.java`, `J/crawler/reed/JobApplicationService.java`, `J/crawler/reed/JobDescriptionExtractor.java`
- evidence:
  - `reedcrawler.properties` has commented-out `selectors.*` keys (`:48-61`) that nothing reads; the selectors are hardcoded arrays (`JobApplicationService.java:19-88`, `JobSearchService.java:22-47`).
  - These keys are never read: `crawler.verbose.logging`, `browser.stealth.mode`, `crawler.rate.limit.*`, `crawler.retry.*` and `crawler.screenshot.*` (`:63-81`).
  - `site.base.url` (`:44`) points at a site that is not Reed.
  - `JobDescriptionExtractor` falls back to the whole page body (`:62-66`).
- fix sketch: Add `CrawlerConfig.getSelectors(key, defaults)` and use it in the reed services. Implement the random min/max rate-limit delay and screenshot-on-error, or delete those keys. Add a config test.
- size: M
- needs-network-or-credentials: no for the config tests
- order: after C-config (items 15–16) and item 24

### 37. Remove dead code and wire the visualizer
- type: refactor
- files: `J/crawler/PageVisualizer.java`, `J/crawler/JobCrawlerApp.java`, `J/crawler/CrawlerFactory.java`, `create-file-structure.js`, `J/crawler/AbstractJobCrawler.java`
- evidence:
  - `PageVisualizer` is never instantiated, even though `crawler.visualization.enabled` exists.
  - `customConfig` in `JobCrawlerApp.java:20-22` is unused.
  - The `default` branch at `CrawlerFactory.java:42-43` is unreachable.
  - `create-file-structure.js` has an empty file list (`:5-6`).
  - `AbstractJobCrawler.adjustedDelay` (`:205-210`) should delegate to `CrawlDelays` after item 24.
  - Other dead code belongs to its own cluster: `EmailAnalysisService.enrichWithWebSearch` and `extractEmailFromText` (`:47-114`) to C-email, and `GoogleSheetsService.updateJobApplicationData` (`:316`) and `parseEmailDate` (`:799`) to C-sheets.
- fix sketch: Create a `PageVisualizer` in `AbstractJobCrawler.setupBrowser` when visualization is enabled and expose it to subclasses. Delete the unused code, and delete the script or give it a real purpose.
- size: S
- needs-network-or-credentials: no

### 38. gitinfo.py uses the OpenAI SDK API removed in v1, and its retry loop can spin forever
- type: bug
- files: `gitinfo.py`
- evidence:
  - `openai.ChatCompletion.create` (`gitinfo.py:301`) was removed in `openai>=1.0`, so the call raises `AttributeError`.
  - In `make_request_with_retry` (`:58-95`), the rate-limit branch `continue`s without counting a retry, so the loop is unbounded.
  - The log file `github_skills_analyzer.log` (`:19`) is not gitignored; `.gitignore` has `github_downloader.log`.
- fix sketch: Switch to `OpenAI().chat.completions.create`, read the model from an environment variable, cap the rate-limit waits, and fix the `.gitignore` entry. Check with `python -m py_compile`.
- size: S
- needs-network-or-credentials: no for the compile check

### 39. Per-job CV folders collide and overwrite each other
- type: bug
- files: `J/crawler/AbstractJobCrawler.java`
- evidence: `generateCVForJob` (`:156-168`) builds the folder name from the first 5 words of the title, then drops everything before the first `.`. Two jobs with the same title share a folder, and the second CV overwrites the first. The company and job ID are not used. A null title NPEs.
- fix sketch: Move the naming into a pure `JobFolderNamer` that uses title, company and a short hash of the URL or job ID, and add tests. `JobDataFetcher.JobData.generateJobName` (`J/JobDataFetcher.java:24-30`) can share it later.
- size: S
- needs-network-or-credentials: no

---

## File ownership clusters

Each cluster owns its files exclusively; one agent should work through a cluster's items in order. Clusters can run in parallel unless the last column says otherwise.

| Cluster | Files owned | Items (in order) | Cross-cluster notes |
|---|---|---|---|
| C-build | `pom.xml`, `.github/workflows/ci.yml` | 28, 29 | Do 28 before C-cvgen item 27 removes or uses jsoup: keep jsoup if 27 is planned. |
| C-docs | `README.md`, `.env.example` | 30 | Best after 28, so the run commands are final. |
| C-ai | `J/service/ai/AiService.java`, `OpenAIClient.java`, `LocalLLMClient.java`, `LLMModel.java`, `LLMRequest.java`, `LLMResponse.java`, `LLMClientFactory.java`, `Endpoint.java`, `J/websearch/*.java` | 2, 12, 35 | Item 12 adds an options constructor; C-cvgen updates the `CVGenerator.java:25` call site afterwards. |
| C-email | `J/application/management/EmailAnalysisService.java`, `EmailCategory.java`, `ApplicationManager.java`, `J/crawler/generic/utils/AIResponseParser.java`, new `J/util/LlmJson.java` | 3, 4, 5, 6, then dead code from 37 in `EmailAnalysisService` | The `ApplicationManager:179-187` part of item 18 should be done here after C-sheets creates `ApplicationStatus`, or handed over. |
| C-data | `J/application/management/JobApplicationData.java`, `DataStorage.java`, `GmailService.java`, `ApplicationConfig.java` | 23, 7, 17, 22 | Item 21 touches `GmailService` too: run it last (see Late). |
| C-sheets | `J/application/management/GoogleSheetsService.java`, new `ApplicationStatus.java`, new `J/application/management/sheets/*` | 8, 19, 18, 20, then dead code from 37 in this file | Items 19 and 18 both rewrite the status and date code: keep one agent. The file is large, so make narrow edits. |
| C-config | `J/configuration/ConfigManager.java`, `J/configuration/CrawlerConfig.java`, `configuration/*.properties` | 15, 16 | Item 36 needs these files: run it after this cluster. |
| C-cvgen | `J/CVGenerator.java`, `J/JobDataFetcher.java`, `J/TemplateLoader.java`, `src/test/resources/jobpages/*` | 11, 27 | Then wire item 12's options into `CVGenerator.java:25`. |
| C-crawler-core | `J/crawler/AbstractJobCrawler.java`, `CrawlerManager.java`, `CrawlerFactory.java`, `JobCrawlerApp.java`, `PageVisualizer.java`, `J/crawler/generic/GenericCrawler.java`, `J/App.java`, `create-file-structure.js` | 1, 14, 13, 39, 37 | Do item 1 before C-generic item 9 changes the `FormAnalyzer`/`FormFiller` constructors. The `adjustedDelay` part of 37 waits for item 24. |
| C-generic | `J/crawler/generic/FormAnalyzer.java`, `FormFiller.java`, `FieldHandler.java`, `J/crawler/FormField.java`, `J/crawler/generic/utils/FieldExtractor.java`, `J/crawler/form/FormCrawler.java`, new `J/crawler/form/FieldClassifier.java`, new `LabelResolver` | 10, 25, 26, 9 | Item 9 adds `shutdown()` and the `userData` argument; C-crawler-core makes `GenericCrawler` use them. Do not edit `AIResponseParser` (owned by C-email). |
| C-reed | `J/crawler/reed/*.java`, new `J/crawler/CrawlDelays.java` | 24, then 36 | Item 36 also edits `CrawlerConfig` and `reedcrawler.properties`: after C-config. |
| C-ext | `auto-apply-plugin/*` | 34, 31, 33, 32 | Do 34 first so the later fixes have tests. All four items edit `content.js`, so one agent. |
| C-misc | `gitinfo.py`, `.gitignore` | 38 | None. |
| Late (cross) | `GmailService.java` + `GoogleSheetsService.java` + new `GoogleAuthProvider.java` | 21 | Start only after C-data and C-sheets have finished. |

Do not edit `J/crawler/cache/PageInputCacheManagerCSV.java`; it was just rebuilt in `ccc0ca0`. If item 7 wants its RFC 4180 helper (`toCsvLine`), copy it rather than changing that file.

### Suggested first wave (all offline, disjoint files)
- 1 (C-crawler-core)
- 2 (C-ai)
- 3 → 4 → 5 (C-email)
- 23 → 7 (C-data)
- 8 (C-sheets)
- 10 (C-generic)
- 11 (C-cvgen)
- 15 (C-config)
- 28 (C-build)
- 34 (C-ext)
- 38 (C-misc)
