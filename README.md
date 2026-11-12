# cvmaker

A personal job-search toolkit written in Java 21. It has four entry points:

| Entry point | Class | What it does |
|---|---|---|
| CV generator | `com.cvmaker.App` | Sends your background, a job description and a LaTeX reference template to an LLM, then compiles the returned LaTeX into `cv.pdf` (and optionally `cover_letter.pdf`). |
| Job crawler | `com.cvmaker.crawler.JobCrawlerApp` | Opens a Chromium browser with Playwright and runs a crawler. The Reed crawler searches jobs, generates a CV per job and applies; the form crawler lists the fields of an application form you navigate to. |
| Application tracker | `com.cvmaker.application.management.ApplicationManager` | Searches Gmail for job-related emails, classifies them with an LLM, stores the results locally and syncs one row per company to a Google Sheet. |
| Exa search demo | `com.cvmaker.websearch.ExaSearchClient` | Runs one sample query against the Exa search API and prints the results. |

The repository also holds a Chrome extension (`auto-apply-plugin/`) and two helper scripts
(`gitinfo.py`, `create-file-structure.js`), described at the end.

## Requirements

- JDK 21 and Maven 3.9 or newer.
- A LaTeX compiler on the `PATH` (`pdflatex` by default) for the CV generator and the crawler.
  The bundled templates use common packages, so a full TeX Live or MiKTeX install is the safe choice.
- For the crawler: Playwright's Chromium. Playwright downloads it on first use; to install it
  ahead of time run `java -cp target/cvmaker-1.0-SNAPSHOT.jar com.microsoft.playwright.CLI install chromium`
  after building.
- Credentials (see [Environment variables](#environment-variables)): an OpenAI API key for every
  entry point except the Exa demo, a Google OAuth client for the tracker, and an Exa key for the
  tracker and the Exa demo.

All paths in the configuration are relative to the working directory, so **run every command
from the repository root**.

## Build

```bash
mvn -q package
```

This compiles, runs the unit tests and writes:

- `target/cvmaker-1.0-SNAPSHOT.jar`, whose manifest runs `com.cvmaker.App` and puts `lib/*` on the class path;
- `target/lib/`, all runtime dependencies.

Keep the jar and `lib/` together if you copy them elsewhere. `mvn -q test` runs only the tests;
`mvn -B verify` is what CI runs.

## Running

| Entry point | From the jar | With Maven |
|---|---|---|
| CV generator | `java -jar target/cvmaker-1.0-SNAPSHOT.jar` | `mvn -q compile exec:java` |
| Job crawler | `java -cp target/cvmaker-1.0-SNAPSHOT.jar com.cvmaker.crawler.JobCrawlerApp` | `mvn -q compile exec:java -Pcrawler` |
| Application tracker | `java -cp target/cvmaker-1.0-SNAPSHOT.jar com.cvmaker.application.management.ApplicationManager` | `mvn -q compile exec:java -Ptracker` |
| Exa search demo | `java -cp target/cvmaker-1.0-SNAPSHOT.jar com.cvmaker.websearch.ExaSearchClient` | `mvn -q compile exec:java -Psearch` |

None of the entry points takes command-line arguments; everything comes from the files below.
Any other main class can be run with `mvn -q compile exec:java -Dexec.mainClass=<class>`.

### CV generator (`App`)

1. Reads `configuration/config.properties`.
2. Loads the input files named there (see [Input files](#input-files-data)).
3. If `input.job.url` is empty, the job description comes from `input.job.description.file`.
   If it is set, `JobDataFetcher` is used instead; for a web URL it currently returns a placeholder
   job ("Software Engineer" at "Tech Company") rather than fetching the page, so use the
   description file for real jobs.
4. Loads `templates/<template.name>/template.tex` (and `cover_letter_template.tex`) as a style
   reference, and asks the configured model to write a complete LaTeX document.
5. Writes `cv.tex` into `output.directory`, runs the LaTeX compiler there, renames the PDF to
   `output.pdf.name`, and deletes the intermediate files unless `debug.save.generated.latex=true`.
6. Repeats for the cover letter when `output.generate.cover.letter=true`.

Templates available: `classic`, `modern`, `professional`, `professional2`.

### Job crawler (`JobCrawlerApp`)

`JobCrawlerApp.main` currently creates the **form** crawler (`CrawlerFactory.CrawlerType.FORM`).
To run another crawler, change the `CrawlerType` in `JobCrawlerApp` (`REED`, `FORM` or `GENERIC`).

Every crawler:

- reads `configuration/reedcrawler.properties` (the default `CrawlerConfig` file) and the CV
  configuration it points to with `cv.config.file` (by default
  `configuration/autoapplication.config.properties`), so the `data/` files must exist;
- launches Chromium with a persistent profile in `browser.data.dir` (default `playwright-session/`),
  so logins survive between runs;
- opens `site.base.url` and waits for you to log in and press Enter;
- records the form-field descriptors (never the values) of each page it loads in
  `cache/<crawler name>_page_inputs.csv`.

Then, per crawler type:

- **FORM**: waits for you to open an application form and press Enter, prints the fields grouped
  into sections (applicant, eligibility, documents, consents, questions, actions), and waits for
  Enter again before closing.
- **REED**: searches the site for `site.search.keywords`, and for each "Easy Apply" job extracts
  the description, generates a CV into `<output.directory>/Job-<title>/`, uploads it and submits,
  until `crawler.max.applications` is reached.
- **GENERIC**: an AI form filler (`FormAnalyzer` + `FormFiller`). It is not usable yet
  (see `BACKLOG.md` item 1).

### Application tracker (`ApplicationManager`)

1. Authorises with Google (see [Google OAuth first run](#google-oauth-first-run)).
2. Opens the spreadsheet `GOOGLE_SHEETS_ID`. If that fails for any reason, it creates a new
   spreadsheet called "Job Application Tracker" and prints its ID; put that ID in `.env` so the
   next run reuses it.
3. Runs a set of Gmail searches for application-related emails and skips the IDs listed in
   `processed_emails.txt`.
4. Classifies each new email with `GPT_4_1_MINI` (company, position, status, interview details).
5. Appends the results to the local files below, then rewrites the sheet with one row per
   company, keeping the most advanced status.

Local files written to the working directory: `processed_emails.txt`, `job_applications.txt`,
`system_state.txt`. `tokens/` holds the stored OAuth credential.

### Exa search demo (`ExaSearchClient`)

Runs a fixed sample query against `https://api.exa.ai/search` using `EXA_API_KEY` and prints the
request and the results.

## Input files (`data/`)

The CV configuration files point to these (paths are configurable). They are your personal
data: `.gitignore` excludes `*.txt`, so they are never committed.

| File | Required | Content |
|---|---|---|
| `data/userdata.txt` | yes | Your background in plain text: contact details, experience, education, skills. |
| `data/job_description.txt` | no | The job description to tailor the CV to (used when `input.job.url` is empty). |
| `data/cv_prompt.txt` | no | Extra instructions for the CV prompt. |
| `data/cover_letter_prompt.txt` | no | Extra instructions for the cover-letter prompt. |

A missing optional file only prints a warning.

## Configuration reference

### `configuration/config.properties` and `configuration/autoapplication.config.properties`

Both are read by `ConfigManager`. `App` uses `config.properties`; the crawlers use the file named
by `cv.config.file` in the crawler properties (`autoapplication.config.properties` by default).

| Key | Default | Meaning |
|---|---|---|
| `template.name` | `classic` | Folder under `template.directory` used as the LaTeX reference. |
| `template.directory` | `templates` | Root folder of the templates. |
| `input.user.data.file` | `userdata.txt` | Your background (required). |
| `input.job.url` | empty | Job URL or file path; see the generator steps above. |
| `input.job.description.file` | empty | Job description file. |
| `input.cv.prompt.file` | `cv_prompt.txt` | Extra CV instructions. |
| `input.cover.letter.prompt.file` | `cover_letter_prompt.txt` | Extra cover-letter instructions. |
| `output.directory` | `generation` | Where PDFs are written. |
| `output.pdf.name` | `cv.pdf` | CV file name. |
| `output.cover.letter.pdf.name` | `cover_letter.pdf` | Cover-letter file name. |
| `output.cv.tex.filename` | `cv.tex` | Intermediate LaTeX file name. |
| `output.cover.letter.tex.filename` | `cover_letter.tex` | Intermediate LaTeX file name. |
| `output.generate.cover.letter` | `true` | Also generate a cover letter. |
| `ai.model` | `GPT_4_1_MINI` | One of `GPT_4_1`, `GPT_4_1_MINI`, `GPT_4_1_NANO`, `GPT_5_1_MINI` (OpenAI) or `LLAMA_3_8B_LEXI_UNCENSORED`, `MISTRAL_SMALL_3_2` (local server at `http://localhost:1234/v1`, for example LM Studio). |
| `ai.temperature` | `0.3` | 0.0 to 1.0; out-of-range values fall back to 0.3. |
| `ai.request_delay_ms` | `1000` | Parsed but not used by the generator yet. |
| `ai.max_retries` | `3` | Parsed but not used by the generator yet. |
| `ai.timeout_seconds` | `60` | Parsed but not used by the generator yet. |
| `debug.save.generated.latex` | `false` | Keep `.tex` and other intermediate files. |
| `debug.save.ai.responses` | `false` | Print the LaTeX compiler output when compilation fails. |
| `latex.compiler` | `pdflatex` | Compiler command. |
| `latex.compiler.args` | `-interaction=nonstopmode` | Comma-separated arguments. |
| `latex.cleanup.extensions` | `.tex,.log,.aux,.out,.fdb_latexmk,.fls,.synctex.gz` | Comma-separated extensions deleted after compiling. |
| `latex.progress.report.interval` | `5` | Print progress every N compiled pages. |

### `configuration/reedcrawler.properties`

Read by `CrawlerConfig` (the default for every crawler). If the file is missing, the defaults are
used. All timings are in milliseconds.

| Key | Default | Meaning |
|---|---|---|
| `crawler.name` | `Reed` | Display name. |
| `cv.config.file` | `config.properties` | CV configuration used to generate CVs (the shipped file sets `configuration/autoapplication.config.properties`). |
| `crawler.max.applications` | `10` | Stop after this many submitted applications (Reed). |
| `crawler.debug.mode` | `false` | Read but not used yet. |
| `crawler.visualization.enabled` | `true` | Read but not used yet. |
| `crawler.polling.rate` | `500` | Read but not used yet. |
| `crawler.speed` | `5` | 1 (slow) to 10 (fast), clamped. The Reed crawler multiplies its delays by 1.5 at speed 1 down to 0.3 at speed 10. |
| `browser.data.dir` | `playwright-session` | Persistent Chromium profile folder. |
| `browser.headless` | `false` | Run without a window. |
| `browser.slow.mo` | `1000` | Delay Playwright adds to each action. |
| `browser.viewport.width` / `browser.viewport.height` | `1920` / `1080` | Viewport size. |
| `browser.user.agent` | a Chrome 120 Windows UA | User-Agent header. |
| `browser.args` | `--disable-blink-features=AutomationControlled,--disable-dev-shm-usage,--no-sandbox,--disable-web-security` | Comma-separated Chromium flags. |
| `browser.accept.header`, `browser.accept.language.header` | browser-like values | Extra HTTP headers. |
| `timing.page.timeout`, `timing.navigation.timeout` | `60000` | Playwright default timeouts. |
| `timing.page.load.delay`, `timing.search.results.delay`, `timing.job.card.load.delay`, `timing.application.delay`, `timing.application.step.delay`, `timing.element.interaction.delay`, `timing.processing.start.delay`, `timing.processing.timeout`, `timing.processing.complete.delay`, `timing.processing.fallback.delay`, `timing.confirmation.dialog.delay` | 1000 to 30000 | Waits between crawler steps. |
| `site.base.url` | empty | Page opened for login and searching. |
| `site.search.keywords` | `junior software development` | Search terms (Reed). |

The shipped file also contains `selectors.*`, `crawler.verbose.logging`, `browser.stealth.mode`,
`crawler.rate.limit.*`, `crawler.retry.*` and `crawler.screenshot.*`. `CrawlerConfig` does not
read these keys yet (`BACKLOG.md` item 36), so changing them has no effect.

`CrawlerConfig.forSite("<name>")` loads `configuration/<name>crawler.properties`; only the Reed
file ships with the repository.

## Environment variables

Copy `.env.example` to `.env` in the repository root and fill it in. `.env` is git-ignored.

| Variable | Used by | Where it is read from |
|---|---|---|
| `OPENAI_API_KEY` | every LLM call (`OpenAIClient`), `gitinfo.py` | **process environment only**; the OpenAI SDK does not read `.env`. Export it in your shell. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Gmail and Sheets OAuth (tracker) | process environment, then `.env` |
| `GOOGLE_SHEETS_ID` | tracker spreadsheet | process environment, then `.env` |
| `EXA_API_KEY` | `EmailAnalysisService`, `ExaSearchClient` | process environment, then `.env` |
| `GOOGLE_SHEETS_CLIENT_ID`, `GOOGLE_SHEETS_CLIENT_SECRET` | nothing yet (getters exist in `ApplicationConfig`) | process environment, then `.env` |
| `GITHUB_KEY` | `gitinfo.py` | process environment only |

The OpenAI SDK also honours its standard optional variables (`OPENAI_BASE_URL`,
`OPENAI_ORG_ID`, `OPENAI_PROJECT_ID`). The local models need no key, only a server listening on
`localhost:1234`.

Without the variables the programs start and then stop with an error naming the missing
credential (for example `` `credential` is required, but was not set `` from the OpenAI SDK).

## Google OAuth first run

1. In Google Cloud Console, enable the Gmail API and the Google Sheets API, and create an OAuth
   client of type **Desktop app**. Put its ID and secret in `.env`.
2. Run the tracker. It prints an authorisation URL and listens on `http://localhost:8888` for the
   redirect; open the URL, sign in and allow access. Port 8888 must be free.
3. The credential is stored in `tokens/StoredCredential` (git-ignored) and reused on later runs.
   Delete that file to sign in again or to change account.

The Gmail flow currently asks for Gmail read-only, Gmail labels, Google Drive and Sheets scopes;
narrowing them is `BACKLOG.md` item 21.

## Chrome extension (`auto-apply-plugin/`)

"AI Auto Applier", a Manifest V3 extension that fills form fields with OpenAI-generated answers
(right-click menu "AI Fill Fields in This Element") and scrapes LinkedIn job searches from its
popup. It is plain JavaScript with no build step.

1. Open `chrome://extensions`, turn on **Developer mode**, click **Load unpacked** and choose
   the `auto-apply-plugin` folder.
2. Open the popup, enter your OpenAI API key and your background, and save. Both are stored in
   `chrome.storage.local` in your browser profile.

Tests, once they exist, run with `node --test auto-apply-plugin/` (Node 20).

## Helper scripts

- `gitinfo.py`: lists the repositories of the GitHub account behind `GITHUB_KEY`, samples their
  code and asks OpenAI to summarise your skills. Writes `<username>_skills_analysis/` and
  `github_skills_analyzer.log`. Needs `requests` and `openai`; run `python3 gitinfo.py`.
- `create-file-structure.js`: creates empty files for the paths listed in its `fileStructure`
  array (currently empty). Run `node create-file-structure.js`.

## Privacy

- Your background, the job description and email contents are sent to OpenAI (or to your local
  model server). Email classification also sends search queries to Exa.
- The tracker reads your Gmail and writes to your Google Sheets; the stored OAuth token in
  `tokens/` grants that access, so keep it private.
- Generated CVs (`generation/`), the tracker files (`*.txt`), the browser profile
  (`playwright-session/`) and `.env` are git-ignored. The crawler's `cache/` folder records form
  field names and labels, not values, and is git-ignored too.
- The crawler profile keeps you logged in to job sites; the default `browser.args` include
  `--no-sandbox` and `--disable-web-security`, which weaken the browser's isolation
  (`BACKLOG.md` item 16).

## Project layout

```
src/main/java/com/cvmaker/
  App.java, CVGenerator.java, TemplateLoader.java, JobDataFetcher.java   CV generation
  configuration/          ConfigManager (CV settings), CrawlerConfig (crawler settings)
  service/ai/             LLM clients (OpenAI, local), AiService prompts
  websearch/              Exa search client
  crawler/                Playwright crawlers: reed/, form/, generic/, cache/
  application/management/ Gmail, Google Sheets, email analysis, local storage
configuration/            .properties files
templates/                LaTeX reference templates
auto-apply-plugin/        Chrome extension
```
