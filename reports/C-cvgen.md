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
