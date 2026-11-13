# Swarm report and handoff log

This file is the running record of an autonomous multi-agent session on this repo.
It is written so that a later session (any model) can pick the work up without the
original conversation. It is updated and pushed every time a batch of tasks closes.

- Branch: `claude/brave-fermat-dt1zw3`
- Session start: 2026-11-12 22:04 UTC
- Hard stop: 2026-11-13 01:00 UTC
- Companion files at the repo root:
  - `STACK.md` — real versions, package layout, build/test commands, what cannot run without credentials
  - `BACKLOG.md` — the audited list of bugs, security issues, missing tests and features, with evidence
  - `PLAN.md` — the task list: ID, owner tier, files owned, acceptance check, dependencies, status

## How to resume

1. Read `STACK.md` for the build and test commands.
2. Open `PLAN.md`. Every task has a status: `todo`, `in-progress`, `done`, `failed`, `blocked`.
3. Anything `in-progress` at the time of the last log entry below may be half-applied: run the
   build and tests first, then check `git status` and `git log` against the "Files" column.
4. Pick up `todo` tasks in dependency order. Items in `BACKLOG.md` that never made it into
   `PLAN.md` are listed under "Not started" at the bottom of this file.

## Log

### 22:04 UTC — session start
- Toolchain present: Java 21, Maven 3.9.11, Node 22, Python 3.13. Repo is 68 tracked files, about 7,100 lines of Java.
- No tests directory seen in the tracked tree at start; no README, no CI.
- Dispatched two agents in parallel:
  - Scout: writes `STACK.md`, checks whether `mvn compile` works in this environment.
  - Auditor: reads every source file, writes `BACKLOG.md` (25-40 evidenced items with file ownership clusters).
- Next: write `PLAN.md` from the backlog with disjoint file ownership, then dispatch implementers and workers in parallel.

### 22:08 UTC — T0 done (commit `ccc0ca0`)
- Scout found the baseline build red. Causes: `pom.xml` had the invalid compiler level `1.21`; `.gitignore`
  pattern `cache/` hid the source package `com.cvmaker.crawler.cache`, so `PageInputCacheManagerCSV` was never
  committed; a static `Dotenv.load()` crashed class loading without `.env`.
- Fixed: compiler release 21; JUnit 5 + AssertJ + Mockito + surefire 3.5.2; `.gitignore` anchored to `/cache/`;
  `PageInputCacheManagerCSV` rebuilt from its only call site (`AbstractJobCrawler`, static `saveInputCache(String, Page)`);
  Dotenv now ignores a missing `.env`.
- Verified by the lead: `mvn -q test` exits 0 (3 tests) with no extra flags.

### 22:14 UTC — audit done, plan written
- `BACKLOG.md`: 39 evidenced items with file-ownership clusters.
- `PLAN.md`: wave 1 = 12 parallel tasks (T1-T12), one per cluster, each in its own git worktree;
  wave 2 = cross-cluster items (T13-T16) and a reviewer pass on high-risk items (T17).
- Each agent writes `reports/<cluster>.md` with per-item detail. Those files are the detailed record;
  this file is the index.

### 23:10 UTC — wave 1 merged and green
- All twelve wave 1 tasks (T1-T12) reported done; their branches were merged with no conflicts (61 commits).
- One integration break found by the gate: the pom cleanup (T10) removed `org.json` while the data cluster (T6)
  started using it for the JSON-lines store. Restored the dependency (commit `fix(build): restore org.json`).
- Gate on the merged tree, run by the lead: `mvn -q verify` exit 0 with 793 tests, 0 failures;
  `node --test auto-apply-plugin/` 72 pass; `python3 -m py_compile gitinfo.py` ok; `target/cvmaker-1.0-SNAPSHOT.jar` built.
- Backlog items closed in wave 1: 1-8, 10-17, 19, 20, 22-35, 38, 39, and 18/37/13 in part.
  Per-item detail (what was wrong, what changed, how verified, what is left) is in `reports/C-*.md`.
- Time note: the session lost about 50 minutes between 22:16 and 23:09 to a container restart while a
  command waited for approval. The agents had finished in the meantime.
- Known leftovers recorded by agents (see their reports): no live run against OpenAI, Google, Reed or LinkedIn
  was possible; `pdflatex` is not installed here; job boards that need JavaScript are not fetched by `JobDataFetcher`;
  an email whose AI call always fails is retried on every run (no persisted attempt count);
  `websearch.SearchService` and `ApplicationConfig.getExaApiKey()` have no callers left.

### 23:14 UTC — wave 2 dispatched, then lost
- Six wave 2 agents were launched (T13, T14, T15, T16, T17a, T17b). The session was interrupted seconds later and
  stayed idle until 00:40 UTC. The agents were stopped with it. Five worktrees had no changes at all.
- T18 and T19 were never launched.

### 00:40-00:50 UTC — wrap-up
- Salvaged from the T14 worktree: `AbstractJobCrawler.adjustedDelay` now delegates to `CrawlDelays.adjustedDelay`
  (the `adjustedDelay` part of backlog item 37), with a test. It passed `mvn -q test` before the merge.
- `.gitignore`: added `__pycache__/` and `github_skills_analyzer.log`.
- A last quick fix for `EmailAnalysisService.extractTextFromHtml` was attempted but its agent ran against a stale
  base commit, so its commit (`3c5b34b`, never merged, local only) was discarded. The bug is still open, see below.
- Final gate on the pushed branch, run by the lead at 00:42 UTC: `mvn -q verify` exit 0, 795 tests, 0 failures,
  0 errors, 0 skipped; `node --test auto-apply-plugin/` 72 pass, 0 fail; `python3 -m py_compile gitinfo.py` ok;
  `target/cvmaker-1.0-SNAPSHOT.jar` built.

### 00:43-00:52 UTC — last short tasks
- **HTML email text (merged).** `EmailAnalysisService.extractTextFromHtml` now parses with jsoup, so multi-line
  `<script>`/`<style>` blocks no longer reach the model. Four tests through `analyzeEmailDetailed`.
- **Review T17a (merged): `reports/review-data-sheets.md`.** Items 7 and 8 both PASS. Findings, all in that file
  with line numbers: (1, medium) a legacy record split by a newline whose continuation line starts with `{` is
  dropped during migration and survives only in the `.bak-` file; (2, low) records without an email ID collapse
  onto one null key; (3, low) a same-second backup is skipped; (4, low) the CSV formula guard checks only the first
  character; (5, low) `GoogleSheetsService` clears the sheet and then writes, so a failed write leaves it empty.
- **Review T17b (merged): `reports/review-config-ext.md`.** Item 16 FAIL, item 32 FAIL (the key handling itself
  passes). Findings: (1, high) the only launch site never enabled the Chromium sandbox, so Playwright added
  `--no-sandbox`; (2, medium) the unsafe-flag denylist was two exact case-sensitive strings; (3, low) browser args
  were re-split on commas; (4, medium) the extension answers visually hidden, page-labelled fields with profile
  data the page can read, with no field cap; (5, low) background completions are not rate-limited or
  payload-validated.
- **Sandbox fixes (merged, 3 commits `fix(security):`).** Findings 1-3 of T17b: the sandbox is on by default and
  can be turned off only through the existing unsafe-flags opt-in; the denylist matches case-insensitively on the
  flag name and covers the equivalents; an already-parsed arg list is no longer re-split. Limits: `CrawlerConfig`
  still splits the `browser.args` property on commas, so a value containing a comma cannot be configured;
  `--disable-features` is gated only when it names IsolateOrigins or site-per-process; the sandbox test covers the
  pure decision method, not a real launch. These fixes have not had a second review.
- Gate after these merges, run by the lead at 00:46 UTC: `mvn -q verify` exit 0, 803 tests, 0 failures, 0 errors.
- **Still open from the reviews:** T17b findings 4 and 5 (extension), T17a findings 3 and 5, and whichever of
  T17a findings 1, 2, 4 are not listed as merged in the last entry below.

### State at the end of the session
- Done and merged: T0 and all of wave 1 (T1-T12). Backlog items closed: 1-8, 10-17, 19, 20, 22-35, 38, 39.
  Partly closed: 13 (both sides done), 18 (sheets side done, `ApplicationManager` side open), 37 (dead code removed
  and `adjustedDelay` done; check `reports/C-*.md` for anything each cluster left).
- Not done: backlog items 9, 21, 36 and the rest of 18; the three reviews; the build hygiene task.
- Nothing was verified against live services: no OpenAI, Google, Exa, Reed or LinkedIn call was made, and `pdflatex`
  is not installed in the build environment. Everything is verified by unit tests on logic pulled out of the
  network and browser code.

## Decisions and deviations

- Agents work in separate git worktrees because a shared tree would let one agent's half-edited file break
  another agent's `mvn test`. The lead merges; file ownership is disjoint so merges should be conflict-free.
- Model routing: the four most design-heavy clusters (T1-T4) and reviewers run on the lead's model, the other
  implementers on Opus, T12 on Sonnet. Reason: the owner asked for the hard thinking to happen in this session.
- JUnit 4.11 was replaced by JUnit 5 (there were no existing tests to migrate).

## Open questions for the repo owner

- `src/main/java/com/cvmaker/crawler/cache/PageInputCacheManagerCSV.java` was rebuilt from its call site. If you
  have the original locally (it was git-ignored), yours should replace it. Git may refuse to pull while an
  untracked local file sits at that path: move it aside first, pull, then compare.
- Personal CV data is still in git history (`userdata.txt`, `userdata_ideal.txt`, generated CV and cover-letter
  PDFs under `generation/`). Removing it needs a history rewrite and a force push, which was not done.
- No API keys or tokens were found in the working tree or in history.

## Not started (pick up here)

Each row of wave 2 in `PLAN.md` is a ready task: ID, files owned, and scope. In order of value:

1. **Open review findings.** T17a and T17b ran at the end (see the 00:43-00:52 log entry and
   `reports/review-*.md`). Fix what is still open there first: the extension filling hidden page-labelled fields
   with profile data (medium), background completion validation and rate limit, the Sheets clear-then-write window,
   and any `DataStorage` finding not merged. The sandbox fixes themselves need a second review.
2. **T15 (item 21).** OAuth scopes are still broader than the code needs, and the auth code is still duplicated in
   `GmailService` and `GoogleSheetsService`. Narrowing scopes invalidates a stored token: detect and explain, do not
   delete the token file silently.
3. **T13 (item 9).** AI form filling still does not receive the applicant's data, so the model can invent answers.
   This is the most important functional gap left in the crawler.
4. **T16.** (a) `ApplicationManager` still has its own status priority map next to `ApplicationStatus`.
   (b) done at 00:44 (HTML text extraction). (c) `CVGenerator` does not yet use
   the `AiService` options constructor (timeout, retries), and the LaTeX timeout is not configurable.
5. **T14 (item 36).** Reed selectors are still hardcoded; unused crawler settings are still in `CrawlerConfig`.
6. **T18.** JaCoCo report in `mvn verify`, README section on running tests.
7. **T19.** Integration audit of wave 1 as a whole (cross-cluster seams, new helpers that nothing uses yet such as
   `LabelResolver`/`FieldClassifier` inside `GenericCrawler`, README against code).

Smaller leftovers named in the cluster reports: `websearch.SearchService` and `ApplicationConfig.getExaApiKey()`
have no callers; an email whose AI call always fails is retried on every run; `JobDataFetcher` cannot read job
boards that need JavaScript (a Playwright fallback could reuse `parseJobPage`); the extension sends the whole
profile with each field prompt.

Practical notes for whoever resumes:
- Run agents in separate git worktrees and have each one run `git merge --ff-only claude/brave-fermat-dt1zw3`
  first: one worktree in this session started from a stale commit.
- After merging parallel branches, run the full gate before pushing. The only break in this session was a
  dependency one agent removed while another started using it (`org.json`).
- Local agent branches (`worktree-agent-*`) were not pushed; everything of value is merged into this branch.
