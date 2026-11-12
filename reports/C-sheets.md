# C-sheets report (task T2)

Branch `worktree-agent-a847615fd128715ff`, based on `66038ba`. Five code commits, one per backlog
item, in the order 8, 19, 18, 20, 37, plus this report. Nothing was pushed.

`J/` = `src/main/java/com/cvmaker/`, `T/` = `src/test/java/com/cvmaker/`.

## Summary

| Item | Commit | State |
|---|---|---|
| 8 Formula injection through HYPERLINK | `8dfe1e7` | done |
| 19 Pure logic out of `GoogleSheetsService`, with tests | `b7de9d4` | done |
| 18 One status ranking (sheets side) | `f774fca` | done; `ApplicationManager` side is left for T16 |
| 20 Spreadsheet init | `c9c4689` | done offline; not run against the real Google API |
| 37 Dead code in `GoogleSheetsService` | `5e4db9a` | done |

`GoogleSheetsService.java` went from 1141 to 403 lines. Its public methods `initialize()`,
`initializeSpreadsheet()` and `bulkUpdateAllApplications(Map)` keep their signatures.

Check: `mvn -q test` passes. This task adds 12 test classes with 241 test cases, all offline and
without credentials.

New files:

- `J/application/management/ApplicationStatus.java`
- `J/application/management/sheets/`: `HyperlinkFormula`, `HyperlinkRequests`, `DateParsing`,
  `A1Notation`, `SheetColumn`, `SheetRowMapper`, `ApplicationRecency`, `StatusFormatRules`,
  `SpreadsheetIdStore`, `SheetRef`, `SheetsErrors`
- `T/application/management/`: `ApplicationStatusTest`, `GoogleSheetsServiceTest`, `FakeSheetsApi`
- `T/application/management/sheets/`: one test class per pure class (`SheetsErrors` is covered in
  `SheetRefTest`)

## Item 8: formula injection through HYPERLINK

**What was wrong.** Four places built `"=HYPERLINK(\"" + url + "\", ...)"` from `applicationUrl`
(AI and email content) and `emailLink`. A `"` in the URL closed the string, and the rest of the URL
became formula code (for example `IMPORTXML`, which can send sheet data to another server).

**What changed.** `sheets/HyperlinkFormula`:

- A URL is linked only if, after trimming, it starts with `http://` or `https://`, has a host, has no
  control characters and is at most 2000 characters long.
- Every `"` in the URL and in the label is doubled, which is the Sheets escape for a quote in a string.
- Any other value gives no formula. The link cell then stays empty: the placeholder text ("View
  Email", "Apply") is written only when the URL is safe.

All other cells are written with `valueInputOption=RAW`, so Sheets does not parse them as formulas.
That was already the case and is now asserted in `GoogleSheetsServiceTest`.

**Verified by.** `HyperlinkFormulaTest` (27 cases: quote escaping, `javascript:`, `data:`, `file:`,
formula text, missing host, control characters, length limit), `HyperlinkRequestsTest`, and
`GoogleSheetsServiceTest.aMaliciousApplicationUrlNeverBecomesFormulaCode`, which sends three hostile
URLs through `bulkUpdateAllApplications` and inspects the request body.

**Left.** A URL with a doubled quote still links to an odd address; it is harmless text, not code.

## Item 19: pure logic out of the service

**What was wrong.** Three overlapping date parsers (one dead), `MMM` patterns without
`Locale.ENGLISH`, two copies of the hyperlink request builder, a column-letter helper documented as
0-based but 1-based, and no tests.

**What changed.**

- `DateParsing` is the only date parser. It reads RFC 2822 email dates (with or without day of week,
  seconds, a trailing comment such as `(UTC)`, or an old zone name such as `PST`), ISO-8601 dates and
  date-times, numeric dates and English text dates. `toDisplayDate` gives `dd/MM/yyyy`, an empty
  string for no value, and the original text when it is not a date. `parseTimestamp` gives an
  `Instant` for the recency comparison.
- `A1Notation.columnLetter(n)` is 1-based and throws for `n < 1` (it used to return `A`).
- `SheetColumn` is the column layout as an enum, replacing `ColumnMetadata` and its field-name strings.
- `SheetRowMapper.toRow` and `combineNotes` replace `createRowData` and `combineNotes`.
- `HyperlinkRequests` is the single builder for the link-cell requests.

**Behaviour changes.**

- Month names parse on a JVM whose default locale is not English.
- A numeric date that can be read both ways, such as `03/04/2025`, is always 3 April. Before, the
  display code read it month first and the comparison code read it day first. `12/25/2024` is still
  accepted, month first, because it is not a valid day-first date.
- A row for an application without an email ID has an empty Email ID cell (it was `null`).

**Verified by.** `DateParsingTest` (59 cases, including a run with the default locale set to
German), `A1NotationTest` (1→A, 26→Z, 27→AA, 702→ZZ, 703→AAA), `SheetRowMapperTest` (every column,
defaults, notes order), `SheetColumnTest` (the layout that existing spreadsheets depend on).

## Item 18: one status ranking (sheets side)

**What was wrong.** `ApplicationManager.getStatusPriorityMap` ranks "Rejected" highest;
`GoogleSheetsService.isMoreAdvancedStatus` ranked it lowest and treated any unknown status as more
advanced. Both compared exact strings, while the AI writes "Offer", "Offered" or "Interview
Scheduled". The conditional formatting matched only exact upper-case values.

**What changed.**

- `ApplicationStatus` (enum): `normalize(String)` maps free text to a status with ordered keyword
  rules; `rank()` is the single ranking; `rankOf`, `isMoreAdvanced` and `displayLabel` are helpers.
- Ranking, low to high: Unknown, Applied, Screening, Assessment, Interview, Final Interview,
  Rejected, Withdrawn, Offered, Accepted.
- `sheets/ApplicationRecency` replaces `isNewerApplication` and `isMoreAdvancedStatus`. Order of
  criteria is unchanged (email timestamp, last updated, processed, application date, then status
  rank). Equal timestamps now fall through to the next criterion, and a tie keeps the current record.
- The Status cell holds the canonical label. "Interview Scheduled" is written as "Interview";
  unrecognised text is kept as written; no status is "Applied" as before.
- `sheets/StatusFormatRules` builds the conditional-format formulas from the same labels, with
  `TRIM`. Final Interview rows are coloured like Interview rows.
- The notes put the rejection reason first for any status that normalises to Rejected, not only the
  exact text "Rejected".

**Decision the owner may want to change.** Rejected ranks above every in-progress stage (as in
`ApplicationManager`) but below Offered and Accepted (`ApplicationManager` had Rejected above
Offer). Reason: records are merged per company, so an offer for one role should not be hidden by a
rejection for another. The ranking is the `rank` argument of the enum constants, in one place.

**Verified by.** `ApplicationStatusTest` (82 cases), `ApplicationRecencyTest`,
`StatusFormatRulesTest` (which checks that what the row mapper writes is what the rules match).

**Left for T16.** `ApplicationManager.java:179-187` still has its own map. Replace
`statusPriority.getOrDefault(x.getApplicationStatus(), 0)` with
`ApplicationStatus.rankOf(x.getApplicationStatus())` and delete `getStatusPriorityMap`.

**Not done.** Existing spreadsheets keep their old conditional-format rules, because rules are only
added when a spreadsheet is created. The old rules still match the labels Rejected, Offered,
Accepted and Interview, but not Final Interview.

## Item 20: spreadsheet initialisation

**What was wrong.** Any `IOException` from the lookup (403, 5xx, network) created a new spreadsheet.
The new ID was only printed, so each run without `GOOGLE_SHEETS_ID` created another one. All
requests used sheet id 0.

**What changed.**

- A spreadsheet is created only when no ID is known, or every known ID answers HTTP 404
  (`SheetsErrors.isNotFound`). Any other error is rethrown as an `IOException` that names the ID and
  the HTTP status and says that nothing was created.
- The new ID is saved to `google_sheet_id.txt` in the working directory (`SpreadsheetIdStore`). The
  file is ignored by git through the existing `*.txt` rule. The console prints the line
  `GOOGLE_SHEETS_ID=<id>` to add to `.env`.
- IDs are tried in this order: configured (`GOOGLE_SHEETS_ID`), then saved. So a configured ID that
  points to a deleted spreadsheet does not create a new spreadsheet on every run.
- `SheetRef.firstOf` looks up the first sheet. Grid ranges use its real sheet id; value ranges are
  qualified with its title (`'Title'!A2:V`).
- `bulkUpdateAllApplications` checks for "nothing to write" before it clears the sheet. It used to
  clear first.
- New package-private constructor `GoogleSheetsService(Sheets, String, SpreadsheetIdStore)` for tests.

**Verified by.** `GoogleSheetsServiceTest` runs the real `Sheets` client on `FakeSheetsApi`, an
in-memory HTTP transport (it uses `MockHttpTransport` from google-http-client, which is already a
dependency). Covered: no ID creates and saves; the saved ID is reused on the next run; 400, 401,
403, 429, 500, 503 and a network error create nothing; 404 creates; stale configured ID falls back
to the saved ID; real sheet id and title are used. Also `SpreadsheetIdStoreTest` and `SheetRefTest`.

**Not verified.** Nothing was run against Google (no credentials here). The request shapes are those
of the real client, but the first real run should be watched.

**Left.**

- `ApplicationConfig` (owned by C-data) does not know about `google_sheet_id.txt`. If C-data wants a
  single place for the ID, it can read the file through `SpreadsheetIdStore`.
- `ApplicationManager` catches the new `IOException` like any other init error and disables the
  Sheets sync for that run, which is the intended result of a 403.
- The header row and formatting are still written only when the spreadsheet is created.

## Item 37: dead code in this file

Removed `updateJobApplicationData` (public, no caller in the repo) and the private helpers only it
used: `extractJobDataFromRow`, `addHyperlinks`, `deleteDuplicateRows`, `deleteRow`,
`getExistingData`. `parseEmailDate` went with item 19.

## Notes for later work

- Item 21 (T15) will replace the OAuth code in `initialize()`. That method was not touched, apart
  from the fields around it.
- Item 17 (C-data) changes how `emailTimestamp` is filled. `DateParsing.parseTimestamp` accepts
  ISO-8601 local and offset date-times, RFC 2822 dates and epoch milliseconds, so any of these
  formats works with `ApplicationRecency`.
- `ApplicationManager` merges records per company before it calls the service, using its own
  ranking and ignoring dates. Until T16 changes that, `ApplicationRecency.latestPerCompany` in the
  service usually receives one record per company.
