# PLAN.md

Lead plan for the swarm session of 2026-11-12 (22:04 UTC to 01:00 UTC). Item numbers refer to
`BACKLOG.md`, which holds the evidence and fix sketch for each item. `J/` = `src/main/java/com/cvmaker/`.
Status values: `todo`, `in-progress`, `done`, `failed`, `blocked`.

## Rules for every task

1. Each agent works in its own git worktree and commits there, one commit per backlog item
   (conventional commit message). It does not push. The lead merges.
2. Edit only the files your task owns. Tests for your files go under `src/test/java/` in the
   mirror package, and you own those too. Test resources go under `src/test/resources/<cluster>/`.
3. Do not edit `pom.xml` unless you are C-build. If you need a dependency, say so under QUESTIONS.
4. Public signatures that other clusters call stay source-compatible: add overloads, do not change
   or remove. If a breaking change is needed, do not make it; record it under DEVIATIONS.
5. Every bug fix gets a unit test that fails before the fix and passes after, where the logic can be
   reached offline. Pull pure logic out of network/Playwright code so it can be tested.
6. Nothing in this environment has credentials: no OpenAI, Google, Exa or live-site calls in tests.
7. Acceptance for every task: `mvn -q test` passes in your worktree, plus the per-task check below.
8. Write `reports/<cluster>.md`: per item, what was wrong, what changed (files and behaviour), how it
   is verified, and what is left or deliberately not done. A later engineer resumes from this file.
9. Do not edit `J/crawler/cache/PageInputCacheManagerCSV.java`, `BACKLOG.md`, `PLAN.md`,
   `STACK.md` or `SWARM_REPORT.md`.

## Wave 0

| ID | Task | Tier | Files | Check | Status |
|---|---|---|---|---|---|
| T0 | Make the build green, JUnit 5, rebuild missing cache class | implementer | `pom.xml`, `.gitignore`, `J/crawler/cache/**`, `ApplicationConfig` Dotenv line | `mvn -q test` | done (`ccc0ca0`) |

## Wave 1 (parallel, disjoint files)

| ID | Cluster | Items in order | Tier | Files owned | Extra check | Risk | Status |
|---|---|---|---|---|---|---|---|
| T1 | C-email | 3, 4, 5, 6, dead code of 37 in `EmailAnalysisService` | implementer | `J/application/management/EmailAnalysisService.java`, `EmailCategory.java`, `ApplicationManager.java`, `J/crawler/generic/utils/AIResponseParser.java`, new `J/util/LlmJson.java` | tests cover category parsing, fallback rules, failed-AI path, LlmJson on fenced/trailing-text/nested JSON | | done (merged 23:09) |
| T2 | C-sheets | 8, 19, 18 (sheets side, creates `ApplicationStatus`), 20, dead code of 37 in this file | implementer | `J/application/management/GoogleSheetsService.java`, new `ApplicationStatus.java`, new `J/application/management/sheets/*` | tests cover formula-injection escaping and status ranking | high (8) | done (merged 23:09) |
| T3 | C-crawler-core | 1, 14, 13 (crawler side), 39, 37 (except `adjustedDelay`) | implementer | `J/crawler/AbstractJobCrawler.java`, `CrawlerManager.java`, `CrawlerFactory.java`, `JobCrawlerApp.java`, `PageVisualizer.java`, `J/crawler/generic/GenericCrawler.java`, `J/App.java`, `create-file-structure.js` | tests cover folder-name collision logic and factory | | done (merged 23:09) |
| T4 | C-ai | 2, 12, 35, AiService side of 13 | implementer | `J/service/ai/*.java`, `J/websearch/*.java` | tests cover request building per model (no temperature for GPT-5 family, max tokens honoured), timeout/retry with a fake client, log redaction | | done (merged 23:09) |
| T5 | C-generic | 10, 25, 26 (not 9) | implementer | `J/crawler/generic/FormAnalyzer.java`, `FormFiller.java`, `FieldHandler.java`, `J/crawler/FormField.java`, `J/crawler/generic/utils/FieldExtractor.java`, `J/crawler/form/FormCrawler.java`, new `J/crawler/form/FieldClassifier.java`, new `LabelResolver` | tests cover classifier and label/selector building | | done (merged 23:09) |
| T6 | C-data | 23, 7, 17, 22 | implementer | `J/application/management/JobApplicationData.java`, `DataStorage.java`, `GmailService.java`, `ApplicationConfig.java` | tests cover storage round trip, CSV export escaping, config env fallback and validation | high (7) | done (merged 23:09) |
| T7 | C-cvgen | 11, 27 | implementer | `J/CVGenerator.java`, `J/JobDataFetcher.java`, `J/TemplateLoader.java`, `src/test/resources/cvgen/*` | tests cover filename sanitising, process timeout handling, job-page parsing from saved HTML | | done (merged 23:09) |
| T8 | C-config | 15, 16 | implementer | `J/configuration/ConfigManager.java`, `CrawlerConfig.java`, `configuration/*.properties` | tests cover typo/malformed values and defaults | high (16) | done (merged 23:09) |
| T9 | C-reed | 24 | implementer | `J/crawler/reed/*.java`, new `J/crawler/CrawlDelays.java` | tests cover card indexing and delay computation | | done (merged 23:09) |
| T10 | C-build + C-docs | 28, 29, 30 | implementer | `pom.xml`, `.github/workflows/ci.yml`, `README.md`, `.env.example` | `mvn -q package` builds a runnable jar; README commands match | | done (merged 23:09) |
| T11 | C-ext | 34, 31, 33, 32 | implementer | `auto-apply-plugin/*` | `node --test auto-apply-plugin/` passes | high (32) | done (merged 23:09) |
| T12 | C-misc | 38 | worker | `gitinfo.py` | `python3 -m py_compile gitinfo.py` | | done (merged 23:09) |

## Wave 2 (dispatched 23:14, parallel, disjoint files, each in its own worktree)

| ID | Task | Tier | Files owned | Risk | Status |
|---|---|---|---|---|---|
| T13 | Item 9: pass applicant data into AI form filling, add `shutdown()`, make `GenericCrawler` use it and the new `LabelResolver`/`FieldClassifier` where they apply | implementer | `J/crawler/generic/**`, `J/crawler/FormField.java`, `J/crawler/form/**`, `J/crawler/LabelResolver.java` | | in-progress |
| T14 | Item 36: Reed selectors configurable; act on or delete unused crawler settings; `adjustedDelay` part of 37; use `CrawlDelays` in `AbstractJobCrawler` | implementer | `J/crawler/reed/**`, `J/crawler/CrawlDelays.java`, `J/crawler/AbstractJobCrawler.java`, `J/configuration/CrawlerConfig.java`, `configuration/reedcrawler.properties` | | in-progress |
| T15 | Item 21: narrow OAuth scopes, one `GoogleAuthProvider`; token store location documented | implementer | `J/application/management/GmailService.java`, `GoogleSheetsService.java`, new `GoogleAuthProvider.java` | high | in-progress |
| T16 | Item 18 `ApplicationManager` side (use `ApplicationStatus`); wire `AiService` options (item 12) and a configurable LaTeX timeout into `CVGenerator`; fix `extractTextFromHtml` (script/style patterns lack DOTALL) | implementer | `J/application/management/ApplicationManager.java`, `EmailAnalysisService.java`, `J/CVGenerator.java`, `J/JobDataFetcher.java` | | in-progress |
| T17a | Review items 7 (data store) and 8 (formula injection) | reviewer | read-only, writes `reports/review-data-sheets.md` | | in-progress |
| T17b | Review items 16 (Chromium flags) and 32 (extension API key) | reviewer | read-only, writes `reports/review-config-ext.md` | | in-progress |
| T18 | Build hygiene: `.gitignore` entries (`__pycache__/`, `github_skills_analyzer.log`), JaCoCo report in `mvn verify` (no thresholds), README test section | worker | `.gitignore`, `pom.xml`, `README.md` | | in-progress |
| T19 | Integration audit of the merged wave 1 (`ccc0ca0..HEAD`): cross-cluster seams, unused new helpers, README vs code, behaviour regressions | reviewer | read-only, writes `reports/integration-audit.md` | | in-progress |

## Wave 3 (planned after wave 2 reports)

Fix findings from T17a, T17b and T19; final `mvn -q verify`, `node --test auto-apply-plugin/`,
`python3 -m py_compile gitinfo.py`; final update of `SWARM_REPORT.md`.

## Model routing for this session

The repo owner asked for the hard design work to be done by the strongest model now, with the log
left for a later model. So the four clusters with the most design content (T1, T2, T3, T4) and the
reviewers run on the lead's model; the other implementers run on Opus; T12 runs on Sonnet.
