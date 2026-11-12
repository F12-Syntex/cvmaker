# C-data report (T6)

Cluster C-data, task T6 of PLAN.md. Items in order: 23, 7, 17, 22. Files owned:
`J/application/management/JobApplicationData.java`, `DataStorage.java`, `GmailService.java`,
`ApplicationConfig.java` (`J/` = `src/main/java/com/cvmaker/`). One commit per item.

## Item 23: ApplicationConfig env fallback, validation, dead getters

**What was wrong.** Every getter returned `null` silently when a key was missing, so a missing
`GOOGLE_CLIENT_ID` showed up later as an obscure OAuth error. Two getters were unused. The data
files were fixed relative paths.

**Note.** dotenv-java 3.2.0 already merges `System.getenv()` into its entries at load time (checked
in the bytecode of `DotenvBuilder$DotenvImpl`), so environment variables did work before; the
explicit fallback now also covers blank `.env` values and is testable.

**What changed.**
- `ApplicationConfig.get(key)` / `get(key, default)`: `.env` first, then `System.getenv`; values
  are trimmed and blank counts as missing. Pure, package-private `lookup(...)` does the work.
- `ApplicationConfig.require(key)`: throws `IllegalStateException` naming the key and telling the
  user to add it to `.env` or the environment. `GmailService.initialize()` now uses it for
  `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`, so a missing secret fails fast with a clear message.
- Key-name constants (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_SHEETS_ID`,
  `EXA_API_KEY`, `DATA_DIR`).
- New optional setting `CVMAKER_DATA_DIR` (default: working directory) with
  `getDataDirectory()`, `getProcessedEmailsFile()`, `getJobApplicationsDbFile()`,
  `getSystemStateFile()`. `DataStorage` uses these. The old `*_FILE` constants stay (file names).
- Existing getters keep their signatures and behaviour (null when unset; `getGoogleSheetId()`
  still returns `""` when unset).

**Deliberately not done.** `getGoogleSheetsId()` / `getGoogleSheetsSecret()` are unused (grepped
the whole tree) but are marked `@Deprecated` instead of deleted, because other clusters edit
`GoogleSheetsService` in parallel and the brief asked for source-compatible changes. Delete them
after wave 2 (item 21). `GoogleSheetsService` (C-sheets) still calls the nullable getters for the
OAuth client; it should switch to `require(...)` (or item 21's `GoogleAuthProvider` should).

**Verified by.** `ApplicationConfigTest`: lookup order, blank handling, `require` message, data-dir
default.
