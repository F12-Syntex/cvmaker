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
| T1 | C-email | 3, 4, 5, 6, dead code of 37 in `EmailAnalysisService` | implementer | `J/application/management/EmailAnalysisService.java`, `EmailCategory.java`, `ApplicationManager.java`, `J/crawler/generic/utils/AIResponseParser.java`, new `J/util/LlmJson.java` | tests cover category parsing, fallback rules, failed-AI path, LlmJson on fenced/trailing-text/nested JSON | | in-progress (dispatched 22:16) |
| T2 | C-sheets | 8, 19, 18 (sheets side, creates `ApplicationStatus`), 20, dead code of 37 in this file | implementer | `J/application/management/GoogleSheetsService.java`, new `ApplicationStatus.java`, new `J/application/management/sheets/*` | tests cover formula-injection escaping and status ranking | high (8) | in-progress (dispatched 22:16) |
| T3 | C-crawler-core | 1, 14, 13 (crawler side), 39, 37 (except `adjustedDelay`) | implementer | `J/crawler/AbstractJobCrawler.java`, `CrawlerManager.java`, `CrawlerFactory.java`, `JobCrawlerApp.java`, `PageVisualizer.java`, `J/crawler/generic/GenericCrawler.java`, `J/App.java`, `create-file-structure.js` | tests cover folder-name collision logic and factory | | in-progress (dispatched 22:16) |
| T4 | C-ai | 2, 12, 35, AiService side of 13 | implementer | `J/service/ai/*.java`, `J/websearch/*.java` | tests cover request building per model (no temperature for GPT-5 family, max tokens honoured), timeout/retry with a fake client, log redaction | | in-progress (dispatched 22:16) |
| T5 | C-generic | 10, 25, 26 (not 9) | implementer | `J/crawler/generic/FormAnalyzer.java`, `FormFiller.java`, `FieldHandler.java`, `J/crawler/FormField.java`, `J/crawler/generic/utils/FieldExtractor.java`, `J/crawler/form/FormCrawler.java`, new `J/crawler/form/FieldClassifier.java`, new `LabelResolver` | tests cover classifier and label/selector building | | in-progress (dispatched 22:16) |
| T6 | C-data | 23, 7, 17, 22 | implementer | `J/application/management/JobApplicationData.java`, `DataStorage.java`, `GmailService.java`, `ApplicationConfig.java` | tests cover storage round trip, CSV export escaping, config env fallback and validation | high (7) | in-progress (dispatched 22:16) |
| T7 | C-cvgen | 11, 27 | implementer | `J/CVGenerator.java`, `J/JobDataFetcher.java`, `J/TemplateLoader.java`, `src/test/resources/cvgen/*` | tests cover filename sanitising, process timeout handling, job-page parsing from saved HTML | | in-progress (dispatched 22:16) |
| T8 | C-config | 15, 16 | implementer | `J/configuration/ConfigManager.java`, `CrawlerConfig.java`, `configuration/*.properties` | tests cover typo/malformed values and defaults | high (16) | in-progress (dispatched 22:16) |
| T9 | C-reed | 24 | implementer | `J/crawler/reed/*.java`, new `J/crawler/CrawlDelays.java` | tests cover card indexing and delay computation | | in-progress (dispatched 22:16) |
| T10 | C-build + C-docs | 28, 29, 30 | implementer | `pom.xml`, `.github/workflows/ci.yml`, `README.md`, `.env.example` | `mvn -q package` builds a runnable jar; README commands match | | in-progress (dispatched 22:16) |
| T11 | C-ext | 34, 31, 33, 32 | implementer | `auto-apply-plugin/*` | `node --test auto-apply-plugin/` passes | high (32) | in-progress (dispatched 22:16) |
| T12 | C-misc | 38 | worker | `gitinfo.py` | `python3 -m py_compile gitinfo.py` | | in-progress (dispatched 22:16) |

## Wave 2 (after wave 1 is merged)

| ID | Task | Tier | Files owned | Depends on | Risk | Status |
|---|---|---|---|---|---|---|
| T13 | Item 9: pass applicant data into AI form filling, add `shutdown()`, wire `GenericCrawler` | implementer | C-generic files + `GenericCrawler.java` | T3, T5 | | todo |
| T14 | Item 36: Reed selectors configurable, act on or delete unused crawler settings; `adjustedDelay` part of 37 | implementer | C-reed files + `CrawlerConfig.java`, `reedcrawler.properties` | T8, T9 | | todo |
| T15 | Item 21: narrow OAuth scopes, single `GoogleAuthProvider` | implementer | `GmailService.java`, `GoogleSheetsService.java`, new `GoogleAuthProvider.java` | T2, T6 | high | todo |
| T16 | Item 18 (`ApplicationManager` side) and wiring item 12 options into `CVGenerator` | worker | `ApplicationManager.java`, `CVGenerator.java` | T1, T2, T4, T7 | | todo |
| T17 | Review of high-risk work: items 7, 8, 16, 32 (and 21 when done) | reviewer | read-only | T2, T6, T8, T11 | | todo |

## Wave 3 (time permitting)

Fix reviewer findings, then a second audit pass over the merged tree for regressions and remaining
gaps, test-coverage top-up for untested pure logic, and a final full `mvn -q verify` plus
`node --test` run. Planned in detail after wave 2.

## Model routing for this session

The repo owner asked for the hard design work to be done by the strongest model now, with the log
left for a later model. So the four clusters with the most design content (T1, T2, T3, T4) and the
reviewers run on the lead's model; the other implementers run on Opus; T12 runs on Sonnet.
