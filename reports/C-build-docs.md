# C-build + C-docs (T10): items 28, 29, 30

Branch `worktree-agent-a17c037339ab26d53`, based on `66038ba`. One commit per item, not pushed.

## Item 28: pom cleanup and runnable packaging (`365542a`)

**What was wrong.** Six dependencies were never imported (selenium-chrome-driver,
selenium-support, webdrivermanager, itext7-core, commons-exec, org.json). Jackson was imported
directly but only arrived transitively. Lombok was compile scope with no annotation-processor
path (breaks on JDK 23+, where implicit processing is off). `package` produced a plain jar with
no main class and no dependencies, and there was no exec configuration. `<url>` was example.com.

**What changed (`pom.xml` only).**
- Removed the six unused artifacts after grepping `src/` for every package they provide
  (no import and no fully-qualified use). jsoup is kept (item 27 may use it).
- Added `jackson-databind` `${jackson.version}` = 2.18.2, the exact version openai-java 2.3.0
  brings, so the resolved tree does not change.
- Lombok is now `provided` and listed in `maven-compiler-plugin` `annotationProcessorPaths`.
- `maven-dependency-plugin` 3.8.1 copies runtime dependencies to `target/lib/` at `package`;
  `maven-jar-plugin` writes `Main-Class: ${exec.mainClass}` (default `com.cvmaker.App`) and a
  `lib/` `Class-Path`. Chose this over a shaded jar: Playwright's driver bundle makes an uber-jar
  very large, and shading signed Google/BouncyCastle jars needs signature filtering.
- `exec-maven-plugin` 3.5.0 pinned; `mvn exec:java` runs `App`; profiles `crawler`, `tracker`,
  `search` select `JobCrawlerApp`, `ApplicationManager`, `ExaSearchClient`.
  `-Dexec.mainClass=...` still overrides.
- `<url>` is now the GitHub repository.
- No runtime dependency changed version; JUnit 5, AssertJ, Mockito untouched.

**Verified.** `mvn -q clean package` and `mvn -q -B verify` pass (3 tests). The manifest shows
`Main-Class: com.cvmaker.App`. `target/lib/` holds 61 jars (224 MB, mostly Playwright's
driver bundle), and no test or Lombok jars. Runs without credentials, from a scratch directory
holding a copy of `configuration/`, `templates/` and a dummy `data/userdata.txt`:
- `java -jar target/cvmaker-1.0-SNAPSHOT.jar` stops with the OpenAI SDK error
  `` `credential` is required, but was not set ``. From an empty directory it stops on
  `Configuration file not found`.
- `ApplicationManager` stops on the same OpenAI credential error.
- `ExaSearchClient` stops with an NPE for the missing Exa key.
- `JobCrawlerApp` loads both configs and then gets a PlaywrightException (no browser download).
None of them failed on a missing class. `mvn -q compile exec:java -Psearch` also runs.
`java -cp target/cvmaker-1.0-SNAPSHOT.jar com.microsoft.playwright.CLI --version` prints 1.52.0.

**Left / notes.**
- If another wave-1 agent adds an import of org.json, selenium, itext or commons-exec, the
  merge will not compile. Re-add the dependency or switch that code to Jackson. When I
  grepped, no source used them.
- `maven-jar-plugin` stays at the pinned 3.0.2 (works; bumping was not needed).

## Item 29: CI workflow (`b7d8c72`)

**What was wrong.** No CI.

**What changed.** New `.github/workflows/ci.yml`: on push and pull_request, `ubuntu-latest`,
`permissions: contents: read`, no secrets. Temurin 21 with the Maven cache, `mvn -B verify`
(with `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`). Then Node 20 and `node --test auto-apply-plugin/`,
guarded by a `find` for Node's default test-file patterns. While there are none, it prints a
skip message and passes.

**Why Node 20.** `node --test <directory>` searches the directory only on Node 20. On Node 21+
(this environment has 22.23.4) arguments are globs and a bare directory is run as a module, so
the command fails even when tests exist. I checked both versions with a scratch test file:
Node 22 gives fail 1, `npx node@20` gives pass 1. When moving to a newer Node, change the step to
`node --test 'auto-apply-plugin/**/*.test.js'` (or run `node --test` inside the folder).

**Verified.** YAML parses. The guard finds 0 files in `auto-apply-plugin/` today. The equivalent
`mvn -B verify` passes locally. The workflow itself has not been run on GitHub (not pushed).

**Left.** The backlog's optional `node --check` and `py_compile gitinfo.py` steps were left out
on purpose: the task limits CI to `mvn -B verify` plus `node --test`.

## Item 30: README and .env.example (`064febf`)

**What was wrong.** No README. Environment variables and requirements (pdflatex, Playwright
browsers, OAuth) existed only in code.

**What changed.**
- `README.md` documents what the code does today, from reading `App`, `CVGenerator`,
  `JobDataFetcher`, `TemplateLoader`, `ConfigManager`, `CrawlerConfig`, `JobCrawlerApp`,
  `CrawlerFactory`, `AbstractJobCrawler`, the Reed/form/generic crawlers, `ApplicationManager`,
  `GmailService`, `GoogleSheetsService`, `DataStorage`, `ApplicationConfig`, `ExaSearchClient`,
  `LLMModel`, the extension manifest, `gitinfo.py` and `create-file-structure.js`. It covers:
  - build and the run command for each of the four entry points (jar and `mvn exec` profile);
  - the `data/` input files;
  - every key of the three properties files with its code default, marking keys that are
    parsed but unused or not read at all;
  - every environment variable and where it is read from;
  - the OAuth first run (port 8888, `tokens/`);
  - loading the extension unpacked, the helper scripts, and privacy notes.
- `.env.example`: variable names with placeholders only.
  `OPENAI_API_KEY` and `GITHUB_KEY` are listed as commented `export` lines, because neither
  is read from `.env`.

**Verified.**
- I tested dotenv-java 3.2.0 precedence: a process-environment variable wins over `.env`, and
  the environment alone is enough.
- The OpenAI key is read only from the environment (`OpenAIOkHttpClient.fromEnv()`), which
  matches the error seen above.
- I ran every jar command in the README except `CLI install chromium`, which downloads a
  browser (`--version` was run instead).

**Left.** Some README statements describe current behaviour that wave-1/2 tasks are changing:
- the job-URL placeholder (item 27);
- `JobCrawlerApp` hard-coding FORM, and the generic crawler being unusable (items 1, 9);
- unused `ai.*` and crawler keys (items 12, 36);
- OAuth scopes (item 21);
- Chromium flags (item 16);
- the environment-variable table, if item 23 changes how `ApplicationConfig` reads values.
After those merges, update the matching README lines.
