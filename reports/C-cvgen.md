# C-cvgen report (task T7)

Cluster files: `J/CVGenerator.java`, `J/JobDataFetcher.java`, `J/TemplateLoader.java`,
tests in `src/test/java/com/cvmaker/`, fixtures in `src/test/resources/cvgen/`.
`J/` = `src/main/java/com/cvmaker/`.

## Item 11: LaTeX compile step leaks its reader, can hang, and has filename bugs

### What was wrong
- The compiler output `BufferedReader` was never closed, and `proc.waitFor()` had no timeout, so a
  hung `pdflatex` (for example waiting for terminal input) hung the whole run.
- `texFileName.replace(".tex", ".pdf")` replaced the text anywhere in the name.
- The compiler log was printed only when `debug.save.ai.responses` was set (wrong flag).
- A PDF left from an earlier run counted as success even when this run failed.
- `cleanupIntermediateFiles` swallowed errors, and extensions from `latex.cleanup.extensions` were
  not trimmed (`".log, .aux"` gave `"cv .aux"`).
- `latex.progress.report.interval=0` would divide by zero.
- The CV and cover-letter methods were copy-paste duplicates.

### What changed (`J/CVGenerator.java`)
- New process seam, all nested in `CVGenerator` (no new files, so nothing outside the owned set):
  - `CommandRunner` (package-private functional interface): `run(command, workDir, timeout, lineListener)`
    returns `CommandResult(exitCode, timedOut, outputTail)`.
  - `ProcessCommandRunner`: closes stdin, drains merged stdout/stderr on a daemon thread (keeps only
    the last 40 lines), `waitFor(timeout)`, then kills the process and its descendants with
    `destroyForcibly`, and closes the stream if a grandchild still holds the pipe.
  - `LatexCompiler`: builds the command (compiler, trimmed non-blank args, `.tex` name), deletes a
    stale `<base>.pdf` first, runs, and throws `LatexCompilationException` (public, carries the log
    tail) on timeout, on a missing PDF, or on a non-zero exit with no PDF. On any failure, and on a
    non-zero exit that still produced a PDF, the last log lines are always printed.
  - Timeout is `CVGenerator.DEFAULT_LATEX_TIMEOUT` (120 s). There is no config key for it because
    `ConfigManager` belongs to C-config; a later task can add `latex.timeout.seconds` and pass it in
    `newLatexCompiler()`.
- `pdfFileNameFor` swaps only a trailing `.tex` (case-insensitive) or appends `.pdf`.
- `sanitizeFileName(name, fallback)`: keeps only the last path segment, replaces control and
  `<>:"|?*` characters with `_`, strips leading dots (so `..` cannot escape), falls back when empty.
  Applied to the configured PDF and `.tex` names and to the job folder name.
- `cleanupIntermediateFiles` trims extensions, uses `deleteIfExists`, prints a warning per failure
  and returns the failure count. It still runs only after a successful compile, so a failed run
  leaves the `.tex` and `.log` for debugging (as before).
- `generateCV`/`generateCoverLetter` merged into `generateDocument(DocumentKind, outputDir)`.
- Unchanged on purpose: the constructor and how it builds `AiService` (T16 wires item 12 options);
  public method signatures. The "Fetched job data" line now prints title and company instead of the
  object hash.

### Verification
`src/test/java/com/cvmaker/CVGeneratorTest.java` (21 tests, no LaTeX needed):
- File names: suffix-only `.pdf` swap (the old `replace` failed `my.tex.notes.tex`), sanitising of
  traversal, Windows paths, illegal characters, blanks.
- Cleanup: trimmed extensions, blank entries, a non-deletable entry is counted, not thrown.
- `ProcessCommandRunner` with `sh -c` (Linux/macOS only): a `sleep 30` is killed after 500 ms with
  `timedOut=true`; about 300 KB of output plus a stderr line is drained with only the tail kept;
  exit code and working directory pass through.
- `LatexCompiler` with a fake compiler (`sh -c '<script>' fakelatex cv.tex`): rename to the requested
  name, failure with log tail, stale PDF not accepted, non-zero exit with PDF accepted, zero exit
  without PDF rejected, hanging compiler stopped. With a lambda runner: timed-out result fails even
  if a partial PDF exists; progress interval 0 is safe; command building trims args.

### Left / not done
- No real `pdflatex` run here (not installed); the real path differs only in the command name.
- Timeout is not configurable yet (see above).

## Item 27: Job-URL mode generates a CV for a fake job

### What was wrong
- `fetchFromUrl` was a stub returning "Software Engineer" at "Tech Company" with the description
  "Job description from URL: <url>", so `input.job.url` produced a CV for a made-up job.
- `isFilePath` mixed `||` and `&&` without parentheses, NPE'd on null, and sent any non-existent
  name without a slash (for example `job.txt`) to the URL stub.
- The `URI uri` variable was unused; `JobData.generateJobName` gave `null_null` for null fields.

### What changed (`J/JobDataFetcher.java`)
- Routing: `fetchJobData` rejects null/blank, treats only absolute `http(s)` URLs with a host as URLs
  (`isHttpUrl`), and everything else as a file path ("File not found" if missing).
- Network seam: public `PageFetcher` interface and a new public `JobDataFetcher(PageFetcher)`
  constructor (additive; the no-arg constructor still exists and uses `HttpPageFetcher`).
  `HttpPageFetcher` uses `java.net.http` with 15 s connect / 30 s request timeouts, follows
  redirects, sends a browser-like User-Agent, requires a 2xx, and restores the interrupt flag.
- Parsing (`parseJobPage(html, url)`, jsoup + Jackson): JSON-LD `JobPosting` first (top-level object,
  array or `@graph`; `@type` as string, array or schema.org URL; malformed blocks skipped; title,
  `hiringOrganization` object or string, `jobLocation` address, entity-escaped HTML description).
  Then microdata (`itemprop=title`, `hiringOrganization name`), then `og:title` / `<title>` / `<h1>`
  split on `|`, `-`, en and em dashes and " at ", dropping a trailing segment that names the site
  (`og:site_name` or a URL host label). Description falls back to `[itemprop=description]`, common
  job-description ids/classes, `article`, `main`, then the body, after removing scripts, styles,
  nav, header, footer and forms. HTML becomes text with line breaks for paragraphs, `<br>` and
  list items ("- " prefix); capped at 20 000 characters.
- If no description of at least 80 characters is found (JS-only page, login wall), it throws an
  `IOException` that tells the user to save the text and use `input.job.description.file`, instead
  of generating a CV. Missing title or company become "Unknown Position" / "Unknown Company".
- The description passed to the AI starts with `Title:`, `Company:`, optional `Location:` and
  `Source:` lines, then the text.
- `JobData.generateJobName` (now package-private static, same output for ordinary names) is
  null-safe, collapses repeated underscores, trims leading/trailing `_`/`-`, cuts at 50 characters
  without a trailing separator, and returns `job` when nothing is left. `JobData.toString` added.
- File mode (`extractJobTitle`/`extractCompanyName` and their defaults) is unchanged.
- Jackson (`com.fasterxml.jackson.databind`) is used for JSON-LD because it is already imported
  elsewhere and item 28 plans to declare it; `org.json` was avoided since item 28 may remove it.
  `pom.xml` was not touched.

### Verification
`src/test/java/com/cvmaker/JobDataFetcherTest.java` (15 tests, offline) with fixtures in
`src/test/resources/cvgen/`:
- `jobposting-jsonld.html`: `@graph`, array `@type`, a malformed JSON-LD block, escaped HTML
  description, location; checks title, company, folder name, list formatting, no page chrome.
- `og-title-only.html`: "Data Engineer at Contoso Health | Indeed.com" title split, description
  taken from `#job-description` only (no script, style or sidebar text).
- `js-shell.html`: rejected with a clear error.
- Inline HTML: microdata, top-level JSON-LD array with a string organization, placeholders,
  truncation, `splitPageTitle`, `htmlToText`.
- Routing with a fake `PageFetcher`: URL goes to the fetcher (the old stub's fake job is gone),
  fetch errors propagate, null/blank rejected, `isHttpUrl` cases, a temp file is read from disk,
  and a missing `no-such-job-file.txt` is a "File not found" error, not a URL fetch.
- Folder-name sanitising: traversal, nulls, symbols-only, long names.

### Left / not done
- No live fetch was run (no network to job sites here). Many boards (LinkedIn, Indeed) need
  JavaScript or block plain HTTP clients; for those the user gets the error above. A Playwright
  fallback could reuse `parseJobPage` on the rendered HTML.
- `TemplateLoader.java` needed no change for these items.
