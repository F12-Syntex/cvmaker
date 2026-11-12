# Swarm report and handoff log

This file is the running record of an autonomous multi-agent session on this repo.
It is written so that a later session (any model) can pick the work up without the
original conversation. It is updated and pushed every time a batch of tasks closes.

- Branch: `claude/brave-fermat-dt1zw3`
- Session start: 2026-11-12 22:04 UTC
- Hard stop: 2026-11-13 01:00 UTC
- Companion files at the repo root:
  - `STACK.md` — real versions, package layout, build/test commands, what cannot run without credentials
  - `BACKLOG.md` — the audited list of bugs, security issues, missing tests and features, with evidence
  - `PLAN.md` — the task list: ID, owner tier, files owned, acceptance check, dependencies, status

## How to resume

1. Read `STACK.md` for the build and test commands.
2. Open `PLAN.md`. Every task has a status: `todo`, `in-progress`, `done`, `failed`, `blocked`.
3. Anything `in-progress` at the time of the last log entry below may be half-applied: run the
   build and tests first, then check `git status` and `git log` against the "Files" column.
4. Pick up `todo` tasks in dependency order. Items in `BACKLOG.md` that never made it into
   `PLAN.md` are listed under "Not started" at the bottom of this file.

## Log

### 22:04 UTC — session start
- Toolchain present: Java 21, Maven 3.9.11, Node 22, Python 3.13. Repo is 68 tracked files, about 7,100 lines of Java.
- No tests directory seen in the tracked tree at start; no README, no CI.
- Dispatched two agents in parallel:
  - Scout: writes `STACK.md`, checks whether `mvn compile` works in this environment.
  - Auditor: reads every source file, writes `BACKLOG.md` (25-40 evidenced items with file ownership clusters).
- Next: write `PLAN.md` from the backlog with disjoint file ownership, then dispatch implementers and workers in parallel.

### 22:08 UTC — T0 done (commit `ccc0ca0`)
- Scout found the baseline build red. Causes: `pom.xml` had the invalid compiler level `1.21`; `.gitignore`
  pattern `cache/` hid the source package `com.cvmaker.crawler.cache`, so `PageInputCacheManagerCSV` was never
  committed; a static `Dotenv.load()` crashed class loading without `.env`.
- Fixed: compiler release 21; JUnit 5 + AssertJ + Mockito + surefire 3.5.2; `.gitignore` anchored to `/cache/`;
  `PageInputCacheManagerCSV` rebuilt from its only call site (`AbstractJobCrawler`, static `saveInputCache(String, Page)`);
  Dotenv now ignores a missing `.env`.
- Verified by the lead: `mvn -q test` exits 0 (3 tests) with no extra flags.

### 22:14 UTC — audit done, plan written
- `BACKLOG.md`: 39 evidenced items with file-ownership clusters.
- `PLAN.md`: wave 1 = 12 parallel tasks (T1-T12), one per cluster, each in its own git worktree;
  wave 2 = cross-cluster items (T13-T16) and a reviewer pass on high-risk items (T17).
- Each agent writes `reports/<cluster>.md` with per-item detail. Those files are the detailed record;
  this file is the index.

## Decisions and deviations

- Agents work in separate git worktrees because a shared tree would let one agent's half-edited file break
  another agent's `mvn test`. The lead merges; file ownership is disjoint so merges should be conflict-free.
- Model routing: the four most design-heavy clusters (T1-T4) and reviewers run on the lead's model, the other
  implementers on Opus, T12 on Sonnet. Reason: the owner asked for the hard thinking to happen in this session.
- JUnit 4.11 was replaced by JUnit 5 (there were no existing tests to migrate).

## Open questions for the repo owner

- `src/main/java/com/cvmaker/crawler/cache/PageInputCacheManagerCSV.java` was rebuilt from its call site. If you
  have the original locally (it was git-ignored), yours should replace it. Git may refuse to pull while an
  untracked local file sits at that path: move it aside first, pull, then compare.
- Personal CV data is still in git history (`userdata.txt`, `userdata_ideal.txt`, generated CV and cover-letter
  PDFs under `generation/`). Removing it needs a history rewrite and a force push, which was not done.
- No API keys or tokens were found in the working tree or in history.

## Not started

(filled in at the end of the session)
