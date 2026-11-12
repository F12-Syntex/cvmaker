# C-reed report (task T9)

Branch `worktree-agent-a010cc9f4af43cb89`. `J/` = `src/main/java/com/cvmaker/`.
Check: `mvn -q test` passes (35 tests; 32 of them new in this cluster).

## Item 24: Reed search loop indexes the wrong cards and barely waits (commit `9916097`)

**What was wrong**
- `JobSearchService.performJobSearch` waited `config.getCrawlingSpeed()` ms (1 to 10 ms) after
  loading the start page and after submitting the search.
- `findNextEasyApplyJob` looped over every job-card selector and started each one at the
  crawler-wide `jobsChecked` counter. Overlapping selectors visited the same cards again, cards were
  skipped when the counter came from another selector, and `[data-qa*='job']` matched elements
  inside the cards (titles, company names), not whole cards.
- After clicking a card and applying, nothing went back to the results page, so the next search
  step queried job or application pages.
- `adjustedDelay` was copy-pasted in `JobApplicationService` and `AbstractJobCrawler`. Speeds above
  12 gave a negative multiplier, so every delay collapsed to the 100 ms floor.

**What changed**
- New `J/crawler/CrawlDelays.java`: pure `adjustedDelay(baseMs, speed)` and `clampSpeed(speed)`.
  Same float arithmetic as before, so results are identical for speeds 1 to 10 (a test checks every
  speed against the old formula). Speed is clamped into 1..10. Floor stays 100 ms.
  Note: the old comment in `AbstractJobCrawler` says speed 1 is 1.5x; the formula gives 1.38x.
  `CrawlDelays` documents the true values.
- New `J/crawler/reed/ResultsPageCursor.java` (package-private, pure): records the results URL,
  resolves the first matching card selector once per results page, hands out a page-local card
  index, and says when the browser has left the results page (URL compare ignoring `#fragment`).
- `JobSearchService`: waits `pageLoadDelay`, `searchResultsDelay` and (after a card click)
  `jobCardLoadDelay`, each scaled by `CrawlDelays`. Uses the cursor; `jobsChecked` is now only a
  statistic. Dropped `[data-qa*='job']` from the card selectors. New public
  `returnToResults()`; `findNextEasyApplyJob` calls it first, so it always starts from the results.
- `ReedCrawler`: calls `searchService.returnToResults()` after each job, and uses `CrawlDelays` for
  the application delay.
- `JobApplicationService.adjustedDelay` delegates to `CrawlDelays`.

**How verified**: `CrawlDelaysTest` (scaling, floor, clamping, overflow, monotonic, legacy
equivalence), `ResultsPageCursorTest` (selector resolution once per page, invalid selectors skipped,
local index, reset per page, card count changing, return detection), `JobSearchServiceTest`
(no over-broad selector, no duplicates). No live Reed run was possible.

**Left for others**
- C-crawler-core (item 37): make `AbstractJobCrawler.adjustedDelay` delegate to
  `CrawlDelays.adjustedDelay(base, crawlerConfig.getCrawlingSpeed())`. I did not edit that file.
- Item 36 (T14): selectors configurable, unused settings, `site.base.url` pointing at a non-Reed
  site, body fallback in `JobDescriptionExtractor`. Not done here.
- Only the first results page is walked; there is no pagination (it was not there before either).
- `returnToResults()` navigates to the URL recorded after the search. If Reed ever serves results
  only via POST or client state, that URL may not reproduce the list; then the cursor finds no
  cards and the loop ends cleanly.

## Extra: pure logic in the reed package (time permitting)

New package-private `J/crawler/reed/ReedText.java`, tested by `ReedTextTest`.

1. `c7c995c` Easy Apply keyword fallback lowercased with the default locale (Turkish turns
   "QUICK APPLY" into a dotless-i word that never matches) and missed keywords split by line breaks
   or non-breaking spaces. Now `ReedText.mentionsEasyApply`: `Locale.ROOT` and whitespace collapse.
   Both tests failed against the old logic before the fix.
2. `9a0b1d3` `JobDescriptionExtractor` appended the primary element's text unchecked: a visible
   but empty element returned `"null\n"` or a blank description and the backup selectors were never
   tried. Now both paths use `ReedText.usableDescription(raw, minLength)`.
3. `1d41599` `extractJobInfo` called `textContent().trim()` unchecked; a null title threw and the
   shared catch also replaced a valid company with "Unknown Company". Blank titles stayed `""` and
   multi-line titles carried newlines into the CV folder name. Now each field is read on its own
   and cleaned by `ReedText.cardField(raw, fallback)`.

Not done: the Playwright-bound parts of `JobApplicationService` (click/upload/confirm loops) have no
pure logic worth extracting before item 36 makes their selectors configurable.
