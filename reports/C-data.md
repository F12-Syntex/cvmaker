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

## Item 7: local application database corrupts records; CSV export unsafe

**What was wrong.** `serialize()` escaped `||` only in `extractedInfo`; a newline in any field broke
the line-based loader; `split("\\|\\|")` without `-1` dropped trailing empty fields (a record whose
later fields were all empty fell below 14 fields and was discarded); the CSV export did not
neutralise formula cells, used a locale-dependent `%.2f`, and `FileWriter`/`Scanner` used the
platform charset. Writes appended in place, so a crash could leave a partial record.

**What changed.**
- Storage format is now one JSON object per line, UTF-8 (`JobApplicationData.toJsonLine()` /
  `fromJsonLine()`, using org.json, which is already a direct dependency; Jackson would have needed
  a pom change). Null fields are omitted; control characters are escaped so a record is always one
  line. NaN confidence is stored as 0.
- Old `||` files still load (`DataStorage.parseRecords`): `deserialize` now splits with `-1`, and a
  legacy record that a newline split over several lines is joined back (a line with fewer than 14
  fields cannot start a record, so it is a continuation). Lines that still cannot be decoded are
  counted and reported on stderr. Mixed JSON/legacy files load.
- Every rewrite (database, system state, CSV export) goes through `DataStorage.writeAtomically`:
  temp file in the same directory, `fsync`, then `ATOMIC_MOVE` over the target (plain replace if
  the file system has no atomic move); the temp file is removed on failure. The temp file is
  created by `Files.createTempFile`, so on POSIX the rewritten file is owner-only (0600).
- `saveJobApplicationData` now reads the file, replaces the record with the same email ID and
  rewrites the file atomically (previously it appended duplicates; loading kept the last, so the
  visible result is the same). New `saveJobApplicationsDb(Map)` writes a whole map.
- First rewrite of a file that holds legacy or unreadable lines first copies it to
  `job_applications.txt.bak-<yyyyMMdd-HHmmss>`, so the conversion to JSON never loses old data.
- `saveProcessedEmailId` still appends (rewriting the whole ID list per email would be quadratic);
  an interrupted append only leaves a partial last line that matches no real ID, and the next
  append starts on a fresh line. Null/blank IDs are ignored. Reads are UTF-8 with replacement of
  invalid bytes, so an old platform-charset file still loads.
- CSV export: UTF-8, every cell quoted, quotes doubled, cells starting with `= + - @ TAB CR` are
  prefixed with `'`, confidence formatted with `Locale.ROOT`.
- New constructor `DataStorage(Path dataDirectory)`; the no-arg constructor uses
  `ApplicationConfig.getDataDirectory()`. Directories are created as needed.
- `serialize()` is kept but `@Deprecated` (only tests use it, to build legacy fixtures).

**Verified by.** `DataStorageTest` (with `@TempDir`): JSON round trip of every field including
newlines, `||`, trailing `|`, quotes, non-ASCII; file round trip; replace-by-ID; old-serializer file
loads; 14-field oldest format; trailing-empty-field record (failed before the fix); newline-broken
legacy records rejoined (failed before); legacy-to-JSON conversion with backup; truncated last JSON
line skipped; failed atomic write leaves the original and no temp file; processed-ID append after a
partial line; CSV formula neutralising, quoting, UTF-8, `Locale.GERMANY` default.

**Left.** A record split by a newline in fields 13 to 15 (timestamps, URL, email link) cannot be
told apart from two records; such newlines should not occur. JSON keys are the Java field names;
renaming a field needs a reader alias.

## Item 17: emailTimestamp recorded processing time, not email time

**What was wrong.** The `JobApplicationData(emailId, subject, from, date)` constructor set
`emailTimestamp = now()`. `GoogleSheetsService.isNewerApplication` uses that field to pick the
latest status per company, and emails are processed in `HashSet` order, so "latest" was random.

**What changed.**
- New `JobApplicationData.parseEmailTimestamp(String dateHeader, ZoneId zone)`: parses the RFC
  2822 `Date` header (strips `(comments)`, collapses spaces, retries without a mismatched weekday,
  accepts numeric offsets and `GMT`/`UT`/`UTC`/named zones) and returns ISO local date-time in the
  given zone, or `null`.
- The constructor sets `emailTimestamp` from the header, in the system time zone (same format and
  zone as before, so `LocalDateTime.parse` in `GoogleSheetsService` and existing stored values stay
  compatible). Only an unparseable header falls back to the processing time.
- `GmailService.getEmailContent` passes the header through `effectiveDate(header, internalDate)`:
  when the header is missing or unparseable, Gmail's `internalDate` is used, formatted as RFC 1123
  in UTC, so the constructor still gets a real email time.

**Verified by.** `JobApplicationDataTest` (offsets, comments, missing seconds, wrong weekday, named
zones, zone conversion, null/garbage, constructor uses email time and orders across offsets) and
`GmailServiceTest` (`effectiveDate` fallbacks). The constructor test fails before the fix.

**Left.** Records already stored keep their old (processing-time) `emailTimestamp`; they are not
re-derived from `date` on load, because the stored value may have been edited in the sheet.
