# C-ai report (task T4)

Items 2, 12, 35 and the AiService side of 13. All four are done. Branch
`worktree-agent-afbdadba66af3fb0e`, four item commits plus this report. Nothing was pushed.

Files owned: `src/main/java/com/cvmaker/service/ai/*.java`, `src/main/java/com/cvmaker/websearch/*.java`,
and their tests under `src/test/java/com/cvmaker/service/ai/` and `.../websearch/`.
No file outside that set was edited, and `pom.xml` was not touched.

Check: `mvn -q test` passes (118 tests, 115 of them new). No test uses the network, a key or a browser.

## What other clusters need to know

- **Nothing breaks.** Every existing public constructor and method is still there with the same
  signature. New behaviour comes through overloads.
- **T16 (wire the config into `CVGenerator`)** is one line:
  `new AiService(config.getAiModel(), config.getAiTemperature(), AiOptions.of(config.getAiTimeoutSeconds(), config.getAiMaxRetries(), config.getAiRequestDelayMs()))`.
  `ConfigManager` is a Lombok `@Data` class, so the three getters exist.
- **Callers that pass no options now get a deadline and retries**: 300 seconds per attempt, 2 retries,
  1 second then 2 seconds between attempts. Before, they waited without limit and never retried.
- **`AiService` is `AutoCloseable`**, and `shutdown()` can be called twice.
- **Full HTTP bodies are no longer printed.** To see them, run with `-Dcvmaker.debug.http=true` or
  `CVMAKER_DEBUG_HTTP=true`.
- **A missing `EXA_API_KEY` is now an `IOException` from `search()`**, where it used to be an NPE.
  `EmailAnalysisService.enrichWithWebSearch` already catches `IOException`, so enrichment is skipped
  and the AI result is kept.

## Item 2: GPT-5 temperature, maxTokens, missing usage (commit `81374cb`)

**Wrong:** `OpenAIClient` sent `temperature` to every model. The GPT-5 family rejects any value but
the default with HTTP 400, so every `FormAnalyzer` and `FormFiller` call failed. `LLMRequest.maxTokens`
was never sent. `completion.usage().get()` threw when the response had no usage block.

**Changed:**
- New `ModelCapabilities`: a table of model-name prefixes to two facts, whether the model takes a
  temperature and which field carries the token limit. `gpt-5*`, `gpt-6*`, `o1*`, `o3*`, `o4*` and
  `codex-mini*` take no temperature. Everything else on OpenAI does. All OpenAI models use
  `max_completion_tokens`; local models use `max_tokens`. A new model family is one new row.
- `LLMModel`: `getCapabilities()`, `supportsTemperature()`, and a correctly named `GPT_5_MINI`.
  `GPT_5_1_MINI` stays as the old name for the same model, because `FormAnalyzer`, `FormFiller` and
  config files use it.
- `LLMRequest`: `hasMaxTokens()`. `getMaxTokens()` still returns 2048 when nothing was set.
- `OpenAIClient`: `buildParams` and `toResponse` are package-private and tested. Usage is optional.
  An answer with no text is an error that names the finish reason. A second constructor takes an SDK
  client, which is how the tests avoid the network.

**How the table was checked** (web, 2026-11-12; the sources are also listed in the class Javadoc):
- OpenAI API reference, "Create chat completion": `max_tokens` is deprecated in favour of
  `max_completion_tokens` and is not compatible with o-series models. The new limit includes
  reasoning tokens.
- Microsoft Learn, "Azure OpenAI reasoning models" (updated 2026-10-30): reasoning models, which
  include the GPT-5 family, do not support `temperature`, `top_p`, the penalties, `logprobs`,
  `logit_bias` or `max_tokens`.
- OpenAI "latest model" guide: for GPT-6, remove `temperature` unless reasoning effort is `none`.
- OpenAI community thread quoting the 400 for `gpt-5-mini-2025-08-07`.

**Two decisions a reviewer should look at:**
1. **OpenAI gets a token limit only when the caller set one.** The backlog said to map `maxTokens` to
   `max_completion_tokens`. Doing that with the builder's default of 2048 would cut every CV short,
   and on a reasoning model the limit also covers hidden reasoning tokens, so answers could come back
   empty. The local client still sends the 2048 default, as it always did.
2. **All `gpt-5*` and `gpt-6*` names are treated as reasoning models**, including `-chat` variants and
   point releases that may accept a temperature when reasoning effort is `none`. I could not confirm
   those cases from the documentation. Leaving the temperature out is always accepted, so the cost of
   being wrong is a setting that has no effect, not a failed call.

**Verified by:** `ModelCapabilitiesTest` (26), `OpenAIClientTest` (18): no temperature for the GPT-5
family, temperature kept for GPT-4.1, explicit limit sent as `max_completion_tokens`, no limit sent
by default, response with and without usage, empty answer, and `complete()` through a mocked SDK client.

**Not done:** no live call (needs `OPENAI_API_KEY`). Reasoning effort is not configurable.

## Item 12: timeout, retries, prompt builder (commit `8b056ba`)

**Wrong:** `AiService` polled with no deadline and never retried, although `ConfigManager` loads
`ai.timeout_seconds`, `ai.max_retries` and `ai.request_delay_ms`. `AiService(LLMProvider)` ignored
its argument. The constructor always printed the Ollama URL. The two prompt builders were copies,
glued the instructions to the first section, and turned a null prompt into the text "null".
`extractLatexFromResponse` threw an NPE on null.

**Changed:**
- New `AiOptions` (immutable, with a builder): timeout, max retries, retry delay, optional max
  tokens. `AiOptions.of(timeoutSeconds, maxRetries, requestDelayMs)` takes the three config values.
  Zero or less switches a feature off.
- `AiService`: new constructors `(model, temperature, options)` and `(client, model, temperature, options)`.
  Each attempt runs on a worker thread and is cancelled at the deadline. A failed attempt is retried
  after a wait that doubles each time, capped at 30 seconds.
- New `LLMException` with a `retryable` flag. Not retried: HTTP 4xx other than 408, 409 and 429, an
  invalid request, an answer cut off by the token limit or the content filter, and an interrupt.
  `OpenAIClient` and `LocalLLMClient` set the flag from the HTTP status.
- One `buildLatexPrompt` for both documents, null-safe, with a blank line after the instructions.
- `extractLatexFromResponse` returns an empty string for null, and now also drops text after the last
  `\end{document}` (a fenced answer after an introduction used to leave a stray fence).
  `generateDirectLatexCV` and `generateDirectLatexCoverLetter` fail with "the AI returned an empty
  response" where they used to throw an NPE or return an empty document.
- `AiService(LLMProvider)` uses `LLMModel.defaultFor(provider)`: `GPT_5_MINI` for OpenAI (the model
  it used before) and `MISTRAL_SMALL_3_2` for local.
- New getters `getModel()`, `getTemperature()`, `getOptions()`.

**Decisions:**
- `ai.request_delay_ms` is used as the wait before the first retry. Nothing is added between
  successful requests, so form filling does not slow down.
- The pool is a cached pool, not a single thread. A blocking socket read does not react to an
  interrupt, so a timed-out attempt can hold its thread until the HTTP client gives up, and the
  retry must not queue behind it.
- `AiOptions` does not import `ConfigManager`, because `ConfigManager` already imports `LLMModel` and
  the packages would then depend on each other.

**Verified by:** `AiServiceTest` (18) with `FakeLLMClient` and a recording sleeper: retry count and
backoff values, no retry for rejected or invalid requests, timeout with the worker interrupted,
retry while the first attempt still holds its thread, no timeout, interrupted caller, request
content. `AiServiceTextTest` (21): fence and `\documentclass` stripping, prompt layout, options
arithmetic, retry classification.

**Not done:**
- `CVGenerator.java:25` still uses the two-argument constructor (T16).
- `setModel()` to a model of another provider keeps the old client. Nothing calls `setModel` today.
- The OpenAI SDK has its own retry and timeout settings. They are unchanged, so a transient error
  can be retried at both levels.

## Item 35: logged bodies, Exa options (commit `b51facf`)

**Wrong:** `LocalLLMClient` and `ExaSearchClient` printed full request and response bodies, which
hold CV text, email content and search queries. `search()` dropped `numResults`, `includeDomains`
and `excludeDomains`. It read `published_date`, but the API returns `publishedDate`. A null API key
gave an NPE. `testConnection` probed `localhost:1234` whatever the endpoint, and printed the URL
without its "/".

**Changed:**
- New `ApiLog` (in `service.ai`, used by both packages): prints the URL, the status and the body
  length. Full bodies appear only in debug mode. An error body inside an exception message is cut to
  300 characters outside debug mode.
- `ExaSearchClient`: sends `numResults` (kept within 1 to 100, 10 when not positive) and the two
  domain lists as JSON arrays, split from the comma-separated strings. Reads `publishedDate`, with
  `published_date` as a fallback. JSON nulls become empty strings where Jackson gave the word "null".
  The key is checked before each request. `chat()` no longer throws an NPE on an empty `choices`.
- `LocalLLMClient`: `testConnection` takes host and port from the endpoint. Request field names come
  from `ModelCapabilities`. A null `usage` no longer fails the call. A trailing "/" on the endpoint
  is removed.
- Both clients take an `OkHttpClient`, and `SearchService` takes an `ExaSearchClient`, for tests.

Field names and bounds were checked against docs.exa.ai/reference/search on 2026-11-12.

**Verified by:** `ExaSearchClientTest` (14) and `LocalLLMClientTest` (12), both through `StubHttp`,
an OkHttp interceptor that answers without opening a connection: request JSON, response mapping,
error paths, and that the prompt, the answer, the query, the result text and the API key never
appear in the log by default.

**Not done:**
- `SearchRequest.useAutoprompt` is not sent. The current API schema has no such field, and
  `type: auto` covers it. The getter and builder method stay.
- `ExaSearchClient.main` still prints result text. It is a manual test harness.
- `AiService` still prints token counts, which hold no content.
- `EmailAnalysisService.java:85` prints the full enrichment answer. That file belongs to C-email.

## Item 13, AiService side (commit `17ccce7`)

**Wrong:** every `AiService` started a non-daemon executor, so a service that was never shut down
kept the JVM alive. `shutdown()` skipped the executor when the client failed to close, and waited up
to 60 seconds for a call in flight.

**Changed:** worker threads are daemon threads named `ai-service-worker-N`, created only while a
query runs. `shutdown()` is idempotent, cancels a call in flight, always stops the workers, waits at
most 5 seconds, and then rethrows a client close failure. A query after shutdown throws
`IllegalStateException`, and a running retry loop stops. `AiService` implements `AutoCloseable`.

**Verified by:** `AiServiceLifecycleTest` (6).

**Not done (other clusters):** one `CVGenerator` per crawler, restoring the output directory in
`finally`, and `App` shutting down in `finally` are the crawler side of item 13 (T3).
`FormAnalyzer` and `FormFiller` each create their own `AiService`, so two OpenAI clients; the
`(client, model, temperature, options)` constructor lets T13 share one, but then only one of the two
services may shut it down.
