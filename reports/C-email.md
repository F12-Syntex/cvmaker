# C-email report (task T1)

Branch `worktree-agent-a3dfa6ec6f9a1370d`, based on `66038ba`. Five item commits plus this report.
All checks ran offline. `J/` = `src/main/java/com/cvmaker/`, `T/` = `src/test/java/com/cvmaker/`.

| Item | Commit | Status |
|---|---|---|
| 3 | `0327296` | done |
| 4 | `d4edbdd` | done |
| 5 | `19b68f8` | done |
| 6 | `8c072bf` | done |
| 37 (EmailAnalysisService part) | `b0bfa68` | done |

Check: `mvn -q test` passes (138 tests in 7 classes; 135 are new).

## Item 3: email category was never parsed

**Wrong.** The prompt asks the model for a display name ("Interview Invitation"). The answer was
parsed with `EmailCategory.valueOf`, which always threw. A missing category threw NPE, which the
outer catch turned into rule-based fallback data, so every AI field was lost.

**Changed.**
- `J/application/management/EmailCategory.java`: new `fromLabel(String)` returning
  `Optional<EmailCategory>`. Null-safe. Ignores case, spaces and punctuation. Accepts the enum name
  and the display name.
- `J/application/management/EmailAnalysisService.java`: uses `fromLabel`. A missing or unknown
  category now falls back to the rule-based category and keeps the other AI fields.
- New package-private constructor `EmailAnalysisService(Function<String, String> llm)`. Tests pass
  a lambda for the prompt-to-answer call. The public `EmailAnalysisService(AiService)` is unchanged.

**Verified.** `T/application/management/EmailCategoryTest.java`,
`EmailAnalysisServiceTest.java` (item 3 section). Three of the service tests failed against the old
`valueOf` line and pass now.

## Item 4: rule-based fallback rejected almost every email

**Wrong.** `isJobRelatedRuleBased` vetoed on substrings such as "all", "update", "connect",
"invitation" and "opportunity". "call" and "finally" were enough to reject an email.
`categorizeEmailRuleBased` matched "test" inside "latest".

**Changed.**
- New `J/application/management/EmailRuleClassifier.java` (pure, static, null-safe):
  `isJobRelated(subject, from, body)`, `categorize(subject, body)`, `provider(from, body)`.
  Keywords match whole words or phrases; a trailing `*` matches the rest of a word.
- The veto list is now only mass-mailing markers: newsletter, job alert, career tips, career
  advice, webinar, digest, recommended jobs, "jobs you may", "jobs for you".
- Category order: rejection, clear offer, interview invitation, confirmation, assessment,
  screening call, weak offer ("offer" plus job/position/role), then confirmation as the default.
- A bare "test" no longer means an assessment, because it is part of job titles ("Test Analyst").
- `EmailAnalysisService` delegates to the classifier; its three private rule methods are gone.

**Behaviour change beyond the backlog text.** `determineProvider` had the same substring defect
("dice" in "Candice", "indeed" as an adverb, a "Follow us on LinkedIn" footer). `provider` now
trusts the sender first and uses the body only for "via X" or "through X". An AI-supplied provider
still wins.

**Verified.** `T/application/management/EmailRuleClassifierTest.java` (table-driven, 48 cases) and
the item 4 section of `EmailAnalysisServiceTest`. Two service tests failed before the change.

## Item 5: emails were marked processed when the AI call failed

**Wrong.** `analyzeEmail` hid a failed AI call behind fallback data, and `ApplicationManager` wrote
every email ID to `processed_emails.txt`. Those emails were never retried.

**Changed.**
- `EmailAnalysisService`: new `analyzeEmailDetailed(...)` returning the nested record
  `AnalysisResult(JobApplicationData data, Outcome outcome)` with `shouldRetry()`.
  `Outcome` is `AI_ANSWER`, `RULES_AFTER_BAD_ANSWER` or `RULES_AFTER_AI_FAILURE`.
  A thrown exception and a null or blank answer both count as `RULES_AFTER_AI_FAILURE`.
  `analyzeEmail` keeps its signature and returns `analyzeEmailDetailed(...).data()`.
- `J/application/management/ApplicationManager.java`: when `shouldRetry()` is true, nothing is
  saved (no processed ID, no job record), so the next run fetches the email again.
- The run stops after 3 AI failures in a row (`MAX_CONSECUTIVE_AI_FAILURES`), so a dead API is not
  called once per email. A summary line reports how many emails were deferred.
- `Thread.sleep` interruption now restores the interrupt flag and ends the loop.
- New package-private constructor
  `ApplicationManager(DataStorage, GmailService, GoogleSheetsService, EmailAnalysisService)`.

**Decision.** An answer that arrived but cannot be used (`RULES_AFTER_BAD_ANSWER`) is still
recorded with rule-based data. Retrying it could loop for ever on one email.

**Verified.** `T/application/management/ApplicationManagerTest.java` (Mockito mocks for storage,
Gmail and Sheets; the AI is a lambda) and the item 5 section of `EmailAnalysisServiceTest`. All
four manager tests failed with the retry check disabled.

## Item 6: LLM JSON was parsed with regex and substring

**Wrong.** `extractJsonValue` stopped at escaped quotes, commas and nested objects, and turned JSON
`null` into the text "null". Only a leading "```json" fence was stripped. `AIResponseParser` took
a substring from the first "{" and flattened nested objects with `Map.toString`.

**Changed.**
- New `J/util/LlmJson.java`:
  - `extractObject(String)`: `Optional<JsonNode>`. Looks inside code fences first, then the whole
    answer. Takes the first balanced `{...}` that Jackson parses as an object. Braces inside
    strings are ignored. Trailing commas and single quotes are accepted.
  - `text(node, field)`: null for missing, JSON null, blank and the word "null". Arrays become
    "a, b"; objects become "key: value; key: value".
  - `bool(node, field)`, `number(node, field)`: accept JSON values and their text forms.
  - `toStringMap(node)`: top-level fields as strings, nested values as compact JSON, nulls dropped.
  - Field lookup is exact first, then ignores case and separators (`company_name`).
- `EmailAnalysisService.parseEnhancedAIResponse` uses `LlmJson`. `extractJsonValue` is deleted.
  An answer with no object, or without a usable `isJobRelated`, is now a bad answer and goes to
  the keyword rules. Before, it was recorded as "not job related".
- Confidence: missing gives 0.7 as before; a value from 1 to 100 is read as a percentage; the
  result is kept within 0 to 1.
- `J/crawler/generic/utils/AIResponseParser.java` uses `LlmJson`. Same signature. It still returns
  a modifiable `HashMap`. Nested values are now JSON text instead of `Map.toString` output.

**Verified.** `T/util/LlmJsonTest.java`, `T/crawler/generic/utils/AIResponseParserTest.java`, and
the item 6 section of `EmailAnalysisServiceTest` (fenced answer with prose, escaped quotes, commas,
nulls, array and nested object). The nested-value parser test failed against the old parser.

## Item 37 (this cluster's part): dead code

Removed `enrichWithWebSearch`, `extractEmailFromText`, the commented-out call and the
`SearchService` field from `EmailAnalysisService`. The service no longer builds an unused Exa HTTP
client. No behaviour change; covered by the existing tests.

## Left, or deliberately not done

- **Jackson is only a transitive dependency** (2.18.2 through `openai-java`). `LlmJson` and three
  older classes import it directly. C-build should declare `jackson-databind` in `pom.xml`.
- **`extractTextFromHtml` bug, not in the backlog, not fixed.** The `<script>` and `<style>`
  patterns lack DOTALL, so multi-line blocks stay in the text. Because the text is then cut to 2000
  characters, an HTML email can reach the model as mostly CSS. Fix: `(?is)` on both patterns, or
  use jsoup.
- **No callers left** for `com.cvmaker.websearch.SearchService` and
  `ApplicationConfig.getExaApiKey()` in main code. They belong to C-ai and C-data.
- **Placeholder values** such as "N/A", "Unknown" or "Not specified" from the model are kept as
  text. Only null, blank and "null" are treated as absent.
- **`ApplicationManager.getStatusPriorityMap`** (item 18) is untouched; it is T16's.
- **No retry limit across runs.** An email whose AI call fails on every run is retried on every
  run. A persisted attempt count would need `DataStorage`, which C-data owns.
- **Merge note.** `ApplicationManagerTest` mocks `DataStorage`, `GmailService`,
  `GmailService.EmailData` and `GoogleSheetsService`, and uses `loadProcessedEmailIds`,
  `loadJobApplicationsDb`, `saveProcessedEmailId`, `saveJobApplicationData`,
  `findUnprocessedEmails(Set)` and `getEmailContent(String)`. It needs those signatures to stay.
- The rule-based classifier is a heuristic. Its expected results are the tables in
  `EmailRuleClassifierTest`; extend the tables when a real email is misjudged.
