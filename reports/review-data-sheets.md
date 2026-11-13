# Review T17a: backlog items 7 (data store) and 8 (Sheets formula injection)

Reviewer did not write this code. Read-only review of the code and its tests, time-boxed to about
ten minutes. Not reviewed: the full test suite, `ApplicationManager` callers, concurrency across
processes.

Checks re-run (offline, targeted): `DataStorageTest` 17, `JobApplicationDataTest` 9,
`HyperlinkFormulaTest` 27, `HyperlinkRequestsTest` 4. All pass, 0 failures.

## Item 7: application data store: PASS (with findings)

What holds: JSON lines via `JSONObject` (newlines, quotes and `||` in any field round-trip);
UTF-8 on read and write; rewrite goes to a temp file in the same directory, fsync, then atomic
move, temp removed on failure; legacy `||` files load with `split(..., -1)`; a backup copy is taken
before the first JSON rewrite of a legacy or partly unreadable file; CSV cells are always quoted,
quotes doubled, cells starting with `= + - @ TAB CR` prefixed with `'`; `Locale.ROOT` for the score.

Findings:

1. Medium. `src/main/java/com/cvmaker/application/management/DataStorage.java:193`.
   The `{` test runs before the "continue the pending legacy record" test. A legacy record whose
   field was split by a newline and whose continuation line starts with `{` is flushed incomplete
   (counted unreadable) and the continuation is parsed as JSON and also rejected. The record is
   missing from the loaded map and from the JSON rewrite; it survives only in the `.bak-` file.
   Input: legacy file with the two lines `id1||Subj||a@b.c||Co||Dev||2024-01-01||APPLICATION||notes:`
   and `{"k":1}||||||applied||0.9||true||2024-01-01T00:00:00`.
   Fix: when `pending` is an incomplete legacy record, append the line before testing for `{`.
   No test covers this (`legacyRecordsBrokenByNewlinesAreJoinedBack` uses plain-text continuations).

2. Low. `src/main/java/com/cvmaker/application/management/DataStorage.java:200` (also `:120`, `:232`).
   Records are keyed by `getEmailId()` with no null check, so every record without an ID collapses
   onto the `null` key and only the last is kept and rewritten.
   Input: two lines `{"subject":"a"}` and `{"subject":"b"}`; after one save only `b` remains.
   Fix: count a record without an email ID as unreadable (which triggers the backup) or key it by
   a synthetic value.

3. Low. `src/main/java/com/cvmaker/application/management/DataStorage.java:163`.
   The backup name has one-second resolution and an existing name is silently skipped. This is safe
   for the first conversion, but if a file gains an unreadable line again within the same second as
   an earlier backup, the rewrite proceeds with no copy of the current content.
   Input: two saves over a file with a bad line inside one second, the file being changed in between.
   Fix: add a counter or millisecond suffix instead of swallowing `FileAlreadyExistsException`.

4. Low. `src/main/java/com/cvmaker/application/management/DataStorage.java:284`.
   Only the first character is tested. Text that a spreadsheet trims before evaluating (leading
   spaces or a leading line feed before `=`) is not neutralised, and the CR branch has no test
   (`DataStorageTest.java:306-316` covers `= + - @ TAB` only).
   Input: subject `" =HYPERLINK(\"http://evil\",\"x\")"`.
   Fix: test the first non-whitespace character, and add CR and leading-space cases to the test.

Not a defect but worth knowing: the directory is not fsynced after the move, so after a power loss
the old file (not a truncated one) may reappear. Acceptable for this tool.

## Item 8: Google Sheets formula injection: PASS

What holds: every values write uses `RAW` (`GoogleSheetsService.java:208`, `:265`), so email and
LLM text is stored as literal text. The only formula written from record data is built in
`sheets/HyperlinkFormula.java:83-86`: http/https only, host required, no control characters, length
cap, `"` doubled in both URL and label; labels are constants. `HyperlinkRequests.java:74` is the
only `setFormulaValue` call and is fed only by that helper. Unsafe URLs produce no request and an
empty cell (`SheetRowMapper.java:45-46`). Tests exercise the quote break-out
(`HyperlinkFormulaTest.java:27-36`) and count the remaining unescaped quotes, so they would fail
without the fix. No other class under `src/main/java` writes to the Sheets API.

Finding:

5. Low (data safety, outside the injection fix). `GoogleSheetsService.java:203-209`.
   The sheet is cleared and then rewritten in a second call; if the update fails (quota, network),
   the sheet is left empty until the next successful run. The local database is the source, so
   nothing is lost permanently.
   Input: any `IOException` from the `update` at `:207` after `clearExistingData()` succeeded.
   Fix: write the new rows first and clear only the rows below them, or do both in one batchUpdate.
