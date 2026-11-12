# STACK.md (scout report)

## Versions (from pom.xml, verified)
- Java 21 (JDK 21.0.12 here), Maven 3.9.11. Single module `com.cvmaker:cvmaker:1.0-SNAPSHOT`, jar packaging, UTF-8.
- pom sets `maven.compiler.source/target` = `1.21` (INVALID, see Pitfalls). Compiler plugin 3.8.0, surefire 2.22.1, jar plugin 3.0.2 (all pinned in pluginManagement).
- Key deps: openai-java 2.3.0, Playwright 1.52.0, Selenium chrome-driver/support 4.33.0, webdrivermanager 6.1.0,
  jsoup 1.20.1, okhttp 4.12.0, org.json 20250517, itext7-core 7.2.5 (pom type), commons-exec 1.3,
  Lombok 1.18.38, dotenv-java 3.2.0, Google: api-client 2.8.0, sheets v4-rev20220620-2.0.0, gmail v1-rev20220404-2.0.0,
  oauth-client-jetty 1.39.0, http-client-gson 1.47.0.
- JUnit 4.11 (test scope) is declared in pom.xml, but there are NO tests.
- No Spring, no logging framework: code uses System.out / printStackTrace.

## Layout (src/main/java/com/cvmaker, about 7100 lines, 47 files)
- `App.java` - main entry: CV generation from configuration/config.properties.
- `CVGenerator`, `TemplateLoader`, `JobDataFetcher` - AI-driven LaTeX CV/cover letter generation, shells out to a LaTeX compiler (ProcessBuilder) to make PDF.
- `configuration/` - `ConfigManager` (config.properties), `CrawlerConfig` (configuration/<site>crawler.properties).
- `service/ai/` - LLM abstraction: `LLMClient`, `OpenAIClient` (uses `OpenAIOkHttpClient.fromEnv()`), `LocalLLMClient`, `LLMClientFactory`, `LLMModel`, `AiService`, request/response/usage types.
- `websearch/` - Exa web search client (`ExaSearchClient`, `SearchService`, request/result).
- `crawler/` - Playwright job crawlers: `AbstractJobCrawler` (persistent Chromium context), `CrawlerFactory`, `CrawlerManager`, `JobCrawlerApp` (main), `JobInfo`, `FormField`, `PageVisualizer`.
- `crawler/reed/` - Reed site crawler (search, description extraction, application).
- `crawler/generic/` (+ `utils/`) - AI-driven generic form analysis and filling.
- `crawler/form/` - `FormCrawler`.
- `application/management/` - application tracking: Google Sheets (`GoogleSheetsService`, 1141 lines), Gmail (`GmailService`, `EmailAnalysisService`), `ApplicationManager` (has main), `DataStorage`, `ApplicationConfig` (reads .env via dotenv).
- Other dirs: `templates/` (classic, modern, professional, professional2 LaTeX templates), `configuration/` (3 .properties), `auto-apply-plugin/` (Chrome MV3 extension: manifest/background/content/popup JS, calls api.openai.com and LinkedIn; not built by Maven), `create-file-structure.js`, `gitinfo.py` (helpers).

## Conventions
- Package root `com.cvmaker`; classes PascalCase, Javadoc on public methods, 4-space indent.
- Config is `.properties` loaded relative to the working directory (`configuration/...`), so run from the repo root.
- Secrets come from `.env` (dotenv) or environment variables, never from properties.
- Playwright sessions persist in `playwright-session/` / `reed-session/` (gitignored).
- Lombok is available; match existing style per file before using it.

## Commands (exact)
- Compile (WORKING): `cd /home/user/cvmaker && mvn -q compile -Dmaven.compiler.source=21 -Dmaven.compiler.target=21`
- Test: `mvn test -Dmaven.compiler.source=21 -Dmaven.compiler.target=21` (no tests exist; nothing to run)
- Package: `mvn -q package -DskipTests -Dmaven.compiler.source=21 -Dmaven.compiler.target=21` (produces target/cvmaker-1.0-SNAPSHOT.jar; plain jar, no main class manifest, no shade plugin, so deps are not bundled)
- Run CV generator: `mvn -q exec:java -Dexec.mainClass=com.cvmaker.App` (exec plugin not pinned; may need download) or java -cp with deps.
- Alternative to the -D flags: change pom properties to `21` (scout did not edit pom).

## Build results (this environment)
- `mvn -q -o compile`: FAILS offline. First error: `maven-resources-plugin:3.0.2 ... has not been downloaded` (local repo is empty). Needs network.
- `mvn -q compile` (online, via the preconfigured proxy; no extra Maven fix from /root/.ccr/README.md was needed, JAVA_TOOL_OPTIONS already sets trustStore and proxy): dependencies and plugins resolve, but FAILS with
  `invalid target release: 1.21` (maven-compiler-plugin 3.8.0, pom values `1.21`).
- With `-Dmaven.compiler.source=21 -Dmaven.compiler.target=21`: dependency resolution OK, then compile FAILS:
  `AbstractJobCrawler.java:[12,33] package com.cvmaker.crawler.cache does not exist`
  (only 1 error, the first and last error line apart from Maven boilerplate).
- Root cause: `AbstractJobCrawler` imports `com.cvmaker.crawler.cache.PageInputCacheManagerCSV`, but that package does not exist in the repo. `.gitignore` contains `cache/`, which matches `src/main/java/com/cvmaker/crawler/cache/` so the source was never committed (commit 9f38198 added the usage). Fix options: create `PageInputCacheManagerCSV` in `src/main/java/com/cvmaker/crawler/cache/`, remove the import/usage, or change .gitignore to `/cache/` and add the class. Until fixed, `package` also fails.
- Therefore the baseline is RED. Workers should not treat this error as caused by their change; only check that no NEW errors appear.

## Tests
- `src/test` does not exist. JUnit 4.11 is in pom (test scope). Surefire 2.22.1 with JUnit 4 works with `src/test/java/**/*Test.java`. New tests must avoid network, Playwright, and OpenAI.

## External credentials / network (cannot run here)
- OpenAI: `OpenAIClient` uses `OpenAIOkHttpClient.fromEnv()` (needs OPENAI_API_KEY env; outbound api.openai.com). Affects CVGenerator, AiService, generic form filling, EmailAnalysisService. Models in `LLMModel` (gpt-4.1 family, gpt-5-mini). `LocalLLMClient` targets a local LLM endpoint (not available).
- Google: `ApplicationConfig` reads from `.env` GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_SHEETS_CLIENT_ID, GOOGLE_SHEETS_CLIENT_SECRET, GOOGLE_SHEETS_ID; OAuth browser flow (jetty) stores `tokens/StoredCredential`. `Dotenv.load()` is static and throws if no `.env` file exists, so any class touching ApplicationConfig fails at init without `.env`. Affects GoogleSheetsService, GmailService, ApplicationManager.
- Exa search: EXA_API_KEY (`ExaSearchClient`).
- Playwright: `AbstractJobCrawler` calls `Playwright.create()` + `chromium().launchPersistentContext` (non-headless by default: browser.headless=false, slow.mo 1000). Needs browsers; Chromium exists at /opt/pw-browsers/chromium (do not download; PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD / executable path would be needed). Crawling targets live sites (reed.co.uk, etc.) and needs network and logins.
- LaTeX compiler (`config.getLatexCompiler()`, e.g. pdflatex) must be installed for PDF output; not verified here.
- Selenium/WebDriverManager is a dependency but is not referenced in src (unused).

## Secrets check
- configuration/*.properties: no API keys, tokens or passwords found (keyword grep hit only `site.search.keywords` / selector keywords, harmless). They contain personal local paths/user-agent style settings only.
- `.env`, `tokens/StoredCredential` are gitignored and not tracked. Nothing secret committed as far as scanned.

## Pitfalls
- pom `1.21` target is invalid for javac; use `21` (see commands).
- Missing `crawler.cache` package (gitignore `cache/` swallowed it); baseline compile is broken.
- `.gitignore` also ignores `*.txt`, `output`, `generation`, `temp`, `helper.js`, so new files with those names/extensions will silently not be committed.
- Offline Maven repo is empty: always run online (proxy) or builds fail resolving plugins.
- Working directory must be repo root for relative `configuration/` paths.
- Static `Dotenv.load()` makes unit tests of Google/Exa-related classes fail unless `.env` exists.
- `GoogleSheetsService` is a 1141-line file; edit with narrow changes.
- Mixed `System.exit` in `App`; keep tests off `main` methods.
- Browser extension `auto-apply-plugin/` is plain JS with no build or test tooling.

## After T0 (build is green)
- pom: `maven.compiler.release=21`, maven-compiler-plugin 3.13.0, maven-surefire-plugin 3.5.2. No -D flags needed any more.
- Compile: `cd /home/user/cvmaker && mvn -q compile`
- Test: `mvn -q test` (single class: `mvn -q test -Dtest=PageInputCacheManagerCSVTest`)
- Package: `mvn -q package -DskipTests`
- Test stack (test scope): JUnit 5 (junit-jupiter 5.11.4), AssertJ 3.27.3, Mockito 5.14.2 (mockito-core + mockito-junit-jupiter). JUnit 4 was removed.
- Tests live in `src/test/java/com/cvmaker/...`, mirroring the main package, named `*Test.java`. Use `@TempDir` for files; no network, Playwright or OpenAI.
- `.gitignore` now ignores `/cache/` (repo-root runtime cache only); `src/main/java/com/cvmaker/crawler/cache/` is tracked.
- `PageInputCacheManagerCSV` was reconstructed from its single call site (AbstractJobCrawler); owner's local copy should replace it if it differs.
- `ApplicationConfig` uses `Dotenv.configure().ignoreIfMissing().load()`, so it loads without a `.env` (getters return null / defaults).
