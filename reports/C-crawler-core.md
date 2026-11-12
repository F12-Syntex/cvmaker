# C-crawler-core report (task T3)

Branch `worktree-agent-ae4f64b6742e7de80`, based on `66038ba`. Items done in order: 1, 14, 13 (crawler
side), 39, 37 (my files, without `adjustedDelay`). One commit per item, plus one commit for this report.
`J/` = `src/main/java/com/cvmaker/`, `T/` = `src/test/java/com/cvmaker/`.

Check: `mvn -q test` passes (80 tests in 10 classes; 77 of them are new).

| Item | Commit | Status |
|---|---|---|
| 1 | `eafc124` | done |
| 14 | `abd6a91` | done |
| 13 (crawler side) | `05abd63` | done |
| 39 | `6ee925d` | done |
| 37 (my files) | `facbe9e` | done, except `adjustedDelay` (waits for T14) |

## Item 1: GenericCrawler was wired to a null Page and closed stdin

**Wrong:** the constructor built `FormAnalyzer` and `FormFiller` with `page`, which is null until
`setupBrowser()`. The Enter prompt used try-with-resources on a `Scanner(System.in)`, which closed stdin,
so `FormFiller`'s later `System.in.read()` failed.

**Changed:**
- `J/crawler/generic/GenericCrawler.java`: analyzer and filler are created in `setupBrowser()` after
  `super.setupBrowser()`, through `createAnalyzer(Page)` / `createFiller(Page)` (protected, overridable).
  The constructors of `FormAnalyzer`, `FormFiller` and `FieldHandler` are untouched.
  `processJobsAndApply()` before `setupBrowser()` prints a message and returns.
  The prompt goes through `waitForEnter()` (protected).
- New `J/crawler/ConsolePrompt.java`: `waitForEnter(InputStream)` reads byte by byte up to `\n`, never
  closes and never buffers ahead. Returns false at end of stream.
- `J/crawler/AbstractJobCrawler.java`: new protected `createPlaywright()` seam, so tests can run
  `setupBrowser()` against mocks.

**Verified by:** `T/crawler/generic/GenericCrawlerTest` (4), `T/crawler/ConsolePromptTest` (4).
Putting the wiring back into the constructor makes all 4 `GenericCrawlerTest` tests fail.
`T/crawler/CrawlerTestSupport` is the shared fixture: a crawler config in a temp dir and a mocked
Playwright object graph.

## Item 14: Playwright setup and close were not exception-safe

**Wrong:** `setupBrowser()` printed launch failures and continued with a null page (NPEs later, leaked
Playwright driver). `close()` stopped at the first exception. `CrawlerManager` closed each crawler twice,
the second time from the main thread.

**Changed:**
- `AbstractJobCrawler.setupBrowser()`: on failure it closes what it started and rethrows the original
  `RuntimeException`. A repeated call closes the previous browser first.
- `AbstractJobCrawler.close()`: page, context and Playwright are each closed in their own try, the
  fields are set to null first, and the method is idempotent. `setupBrowser()` and `close()` are
  `synchronized`.
- `AbstractJobCrawler.openForLogin()`: throws `IllegalStateException` when there is no page, and reads
  the Enter line with `ConsolePrompt` (the old single `System.in.read()` left the `\n` of a Windows
  `\r\n` behind, which answered the next prompt by itself).
- `J/crawler/CrawlerManager.java`: `closeOnce()` closes each crawler exactly once (identity set).
  `shutdown()` cancels, then waits up to 10 s for crawler threads so that they close their own browser,
  then closes whatever is left. One failing `close()` no longer stops the others. A crawler passed to
  `startCrawler()` without `addCrawler()` is now also tracked.

**Verified by:** `T/crawler/AbstractJobCrawlerTest` (setup and close tests), `T/crawler/CrawlerManagerTest`
(5). Against the old main code 9 of these tests fail.

**Left:** if a crawler thread is blocked in `System.in.read()` (not interruptible), `shutdown()` still
closes its browser from the calling thread after the 10 s wait, as before.

## Item 13 (crawler side): generator leak and state left changed on error

**Wrong:** `generateCVForJob` built a new `CVGenerator` (new AI client, new non-daemon executor) per job.
When `generate()` threw, `shutdown()` was skipped and `config.outputDirectory` stayed on the failed job's
folder, so later jobs were nested inside it. `App` called `shutdown()` outside `finally`.

**Changed:**
- `AbstractJobCrawler`: one lazily created `CVGenerator` per crawler (`createCvGenerator()` is
  protected and overridable), shut down in `close()`. The output directory is restored in `finally`.
- `J/App.java`: new package-private `runGeneration(CVGenerator)` with `shutdown()` in `finally`.

**Verified by:** `T/crawler/AbstractJobCrawlerCvGenerationTest` (failed generation restores the directory,
next job is not nested, one generator for two jobs, `close()` shuts it down once), `T/AppTest` (3).

**Not done here:** the `AiService` side (daemon executor, client close) belongs to T4.

## Item 39: per-job CV folders collided

**Wrong:** the folder name was the first 5 words of the title with everything before the first `.`
dropped. Same title meant same folder, so the second CV overwrote the first. A null title threw.

**Changed:**
- New `J/crawler/JobFolderNamer.java` (pure): `Job-<title>_<company>_<hash8>`. The hash is the first 8
  hex digits of SHA-256 over the job ID, else the URL, else the whitespace-normalised description.
  Title: at most 5 words, company: at most 4, each at most 40 characters, accents folded, a leading
  list number (`3. `) dropped, only `A-Z a-z 0-9 _ -` kept. Empty title gives `untitled`.
- `AbstractJobCrawler.generateCVForJob` uses it and accepts a null job, title or company.

**Verified by:** `T/crawler/JobFolderNamerTest` (24, including path-traversal input) and two tests in
`AbstractJobCrawlerCvGenerationTest` that fail against the old code.

**Behaviour to know:**
- Folder names changed, so folders from earlier runs are not reused.
- The same job (same ID, URL or description) maps to the same folder on purpose: a re-run replaces its
  own CV.
- Reed's `JobSearchService.extractJobInfo` sets neither job ID nor URL, so today the description hash is
  what separates jobs. Setting `jobId`/`url` there (T9/T14 files) would make names stable when a
  description is edited.

**Left:** `JobDataFetcher.JobData.generateJobName` (T7's file) could call `JobFolderNamer.slug`.

## Item 37 (my files): dead code and the visualizer

**Changed:**
- `J/crawler/PageVisualizer.java` was never instantiated, and could not have worked:
  - Its init script was a bare `() => {...}`. Playwright Java runs an init script as plain source, so the
    function was evaluated and never called. It is now an invoked function, and it is also applied to
    the already open document.
  - The `highlightElement(Locator)` script had no closing brace (a syntax error that the catch hid).
  - New `PageVisualizer.disabled()` and `isEnabled()`. A disabled visualizer never touches the page.
- `AbstractJobCrawler`: protected field `visualizer`, never null (no-op before setup, after close and
  when `crawler.visualization.enabled=false`). Used in `openForLogin`, `generateCVForJob` and
  `GenericCrawler.processJobsAndApply`.
- `J/crawler/JobCrawlerApp.java`: unused `customConfig` removed.
- `J/crawler/CrawlerFactory.java`: switch expression over the enum (no unreachable `default`; a new
  `CrawlerType` without a case is a compile error). A null type fails with "crawler type".
- `create-file-structure.js` deleted (empty file list, referenced nowhere).

**Verified by:** `T/crawler/PageVisualizerTest` (8), `T/crawler/CrawlerFactoryTest` (6), four visualizer
tests in `AbstractJobCrawlerTest`. I also ran two one-off probes against the installed headless Chromium
(not part of the test suite): a bare `() => {...}` init script leaves its variable undefined while the
statement form defines it, and the new `PageVisualizer` shows the overlay and highlights elements.

**Not done:**
- `AbstractJobCrawler.adjustedDelay` is unchanged; it waits for `CrawlDelays` (T9) and is planned in T14.
- The Reed classes (T9) do not call `visualizer` yet. `ReedCrawler` can use the inherited field;
  `JobSearchService` and `JobApplicationService` would need it passed in.

## Findings outside my items (not changed)

1. **The anti-detection init script in `AbstractJobCrawler.setupBrowser` never runs.** It has the same
   bare `() => {...}` form as the visualizer script had. I left it alone: making it run changes how the
   crawler presents itself to job sites, which is the owner's decision and belongs with item 16.
2. `FormFiller.fillForm` and `FormCrawler.processJobsAndApply` (T5 files) still use a single
   `System.in.read()`. They should call `ConsolePrompt.waitForEnter(System.in)`.
3. For T13 (item 9): `GenericCrawler.createAnalyzer/createFiller` are the place to pass
   `config.getUserDataContent()`. `GenericCrawler` should override `close()` to shut both services down
   and then call `super.close()`.
4. `AbstractJobCrawler` uses Lombok `@Data`, so `equals`/`hashCode` cover mutable fields. Do not put
   crawlers in hash-based collections; `CrawlerManager` uses an identity set for that reason.
5. Mockito prints a self-attach warning on JDK 21. Adding Mockito as a `-javaagent` in surefire
   (`pom.xml`, C-build) removes it.

## Deviations from PLAN.md

- Two new main files are not in T3's file list: `J/crawler/JobFolderNamer.java` (named in backlog item
  39) and `J/crawler/ConsolePrompt.java` (item 1's non-closing reader, shared with `openForLogin`).
- The `openForLogin` stdin change is in the item 14 commit, because it is in `AbstractJobCrawler`.
- Commit messages end with a `Claude-Session:` line after the `Co-Authored-By:` line, as the session's
  attribution rule requires.
