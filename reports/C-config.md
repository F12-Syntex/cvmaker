# C-config report (task T8, backlog items 15 and 16)

Files owned: `J/configuration/ConfigManager.java`, `J/configuration/CrawlerConfig.java`,
`configuration/*.properties`. New: `J/configuration/TypedProperties.java` (package-private helper),
`src/test/java/com/cvmaker/configuration/*Test.java`. `J/` = `src/main/java/com/cvmaker/`.

All public constructors, getters, setters and their return types on `ConfigManager` and
`CrawlerConfig` are unchanged, so no caller in another cluster needs to change.

## Item 15: config parsing crashes on typos and has wrong defaults

**What was wrong**
- Every integer was read with a bare `Integer.parseInt`, so one typo (`60s`, `abc`, a trailing
  space, an empty value) stopped startup with a `NumberFormatException`.
- Booleans used `Boolean.parseBoolean`, so `ture` or `TRUE ` (trailing space) silently became `false`.
- LaTeX list values were split on `,` without trimming; empty entries were kept.
- Temperature was limited to 0-1 although OpenAI accepts 0-2, and the out-of-range warning printed
  the bad value as if it were being used.
- `CrawlerConfig` default `cvConfigFile` was `config.properties`, which does not exist.
- `INDEED_CONFIG_FILE` was an unused constant.
- `jobDescriptionContent` stayed `null` when there was neither a job file nor a URL; the
  programmatic constructor threw an NPE when given a `null` job URL.
- Lombok `@ToString` printed the user's full CV and prompts.

**What changed**
- New `TypedProperties` (package-private) with `stringProp`, `intProp` (optional minimum),
  `doubleProp` (range), `boolProp`, `listProp` and `splitList`. All trim the value. A missing key
  returns the default silently; a malformed or out-of-range value returns the default and prints
  `Warning: config value <key>='<value>' <problem>, using default <default>`.
- `ConfigManager` and `CrawlerConfig` read every property through these helpers. Minimums: delays,
  timeouts, `slow.mo`, `max.applications`, `ai.request_delay_ms`, `ai.max_retries` >= 0;
  viewport sizes, `crawler.polling.rate`, `ai.timeout_seconds`, `latex.progress.report.interval` >= 1.
  `crawler.speed` keeps its old clamp to 1-10 (a non-number now falls back to 5).
- `ai.temperature` accepts 0.0-2.0; anything else, or a non-number, uses 0.3.
- `ai.model` is trimmed and case-insensitive (`gpt_4_1_nano` works); unknown names keep the default.
- LaTeX `compiler.args` / `cleanup.extensions` are trimmed with empty entries dropped. The lists are
  now unmodifiable (they were fixed-size `Arrays.asList` before; no caller mutates them).
- String values are trimmed (paths, URLs, selectors); an explicitly empty value stays empty.
- Default `cvConfigFile` is `configuration/config.properties`. `INDEED_CONFIG_FILE` removed.
- `jobDescriptionContent` is always non-null (`""` without a file or URL); a `null` job URL in the
  programmatic constructor is treated as empty. A whitespace-only URL now counts as "no URL".
- The four `*Content` fields carry `@ToString.Exclude`.

**How verified**
- `ConfigManagerTest` (14 tests) and `CrawlerConfigTest` (9 tests) use `@TempDir` properties files.
  18 of the 23 failed or errored on the old code (NumberFormatException, NPE, wrong values, leaked
  CV text in `toString`) and all pass after the change. `mvn -q test` passes.

**Left / deliberately not done**
- Warnings go to `System.out`, matching the rest of the code base (no logging framework yet).
- Unused crawler settings in `reedcrawler.properties` (rate limit, retry, screenshot, stealth) are
  item 36 (task T14) and were not touched.
- Comma splitting of `browser.args` still cannot express a flag whose value contains a comma.

## Item 16: unsafe Chromium flags on by default for a logged-in persistent profile

**What was wrong**
- The `CrawlerConfig` default `browser.args` and `configuration/reedcrawler.properties` both included
  `--no-sandbox` and `--disable-web-security`. They applied to the persistent profile
  (`playwright-session`) that holds the user's logins, while the generic and form crawlers visit
  arbitrary sites.

**What changed**
- New documented property `browser.unsafe.args.enabled` (default `false`), exposed as
  `CrawlerConfig.UNSAFE_BROWSER_ARGS_PROPERTY`; the flag list is `CrawlerConfig.UNSAFE_BROWSER_ARGS`.
  Accessors: `isUnsafeBrowserArgsEnabled()` / `setUnsafeBrowserArgsEnabled(boolean)` (Lombok).
- Default `browser.args` is now `--disable-blink-features=AutomationControlled,--disable-dev-shm-usage`.
- `getBrowserArgs()` keeps its name and `String` return type (comma-joined, as `AbstractJobCrawler`
  splits it) but now returns the effective list: without the opt-in, the unsafe flags (also in
  `--flag=value` form) are removed even if a user's `browser.args` or a `setBrowserArgs` call
  still lists them; with the opt-in they are added if missing. Entries are trimmed, empty ones and
  duplicates dropped. New `getBrowserArgList()` returns the same as a `List<String>`.
- Loading prints a warning when unsafe flags are ignored, and a louder one when the opt-in is on.
- `configuration/reedcrawler.properties`: removed the two flags from `browser.args` and added the
  commented `browser.unsafe.args.enabled=false`. No other setting in any properties file changed;
  `config.properties` and `autoapplication.config.properties` have no browser settings.

**How verified**
- `CrawlerConfigBrowserArgsTest` (8 tests): defaults exclude the flags; flags in `browser.args` and
  via the setter are stripped without opt-in; `--flag=value` forms are stripped; a misspelled
  opt-in stays off; opt-in adds each flag once; the shipped `reedcrawler.properties` has the property
  set to `false`, no unsafe flag on its `browser.args` line, and its other settings (150 applications,
  keywords `labour`) still load. Before the change the test did not compile (new accessors) and the
  default/shipped-file assertions describe the old unsafe behaviour. `mvn -q test` passes (34 tests).

**Left / deliberately not done**
- `AbstractJobCrawler` (owned by T3) still does `getBrowserArgs().split(",")`. If `browser.args` is
  empty this passes one empty argument to Chromium (same as before). T3 or a later task can switch
  it to `getBrowserArgList()`.
- Users who run Chromium as root in a container will now see a sandbox launch error until they set
  `browser.unsafe.args.enabled=true`; this is intended.
