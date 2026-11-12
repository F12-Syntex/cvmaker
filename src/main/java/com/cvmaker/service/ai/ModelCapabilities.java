package com.cvmaker.service.ai;

import java.util.List;
import java.util.Locale;

/**
 * Which optional request parameters a model accepts. The clients ask this
 * table instead of hard-coding model names, so a new model family is one new
 * row in {@link #OPENAI_RULES}.
 *
 * <p>
 * Sources, read on 2026-11-12:
 * <ul>
 * <li>OpenAI API reference, "Create chat completion": {@code max_tokens} is
 * "deprecated in favor of max_completion_tokens, and is not compatible with
 * o-series models"; {@code max_completion_tokens} is the upper bound that
 * covers visible output tokens and reasoning tokens.</li>
 * <li>Microsoft Learn, "Azure OpenAI reasoning models" (updated 2026-10-30):
 * reasoning models (o-series and the GPT-5 family) do not support
 * {@code temperature}, {@code top_p}, {@code presence_penalty},
 * {@code frequency_penalty}, {@code logprobs}, {@code top_logprobs},
 * {@code logit_bias} or {@code max_tokens}, and "will only work with the
 * max_completion_tokens parameter when using the Chat Completions API".</li>
 * <li>OpenAI "latest model" guide: for the GPT-6 family, remove
 * {@code temperature} whenever reasoning effort is not {@code none}.</li>
 * <li>OpenAI community reports of the HTTP 400 for
 * {@code gpt-5-mini-2025-08-07}: "'temperature' does not support 0.2 with this
 * model. Only the default (1) value is supported."</li>
 * </ul>
 *
 * <p>
 * Assumptions, because the sources do not settle them:
 * <ul>
 * <li>Every model whose name starts with {@code gpt-5} or {@code gpt-6} is
 * treated as a reasoning model, including {@code -chat} variants and later
 * point releases that may accept a temperature when reasoning effort is
 * {@code none}. This client never sets reasoning effort, and leaving
 * {@code temperature} out is always accepted (the API then uses its default),
 * while sending it can be rejected with HTTP 400.</li>
 * <li>An OpenAI model that matches no row is treated like the GPT-4 family:
 * it accepts {@code temperature}.</li>
 * </ul>
 */
public final class ModelCapabilities {

    /**
     * Name of the request field that limits the number of generated tokens.
     */
    public enum TokenLimitParameter {
        /**
         * Legacy name. Used by the local OpenAI-compatible {@code /completions}
         * endpoint.
         */
        MAX_TOKENS("max_tokens"),
        /**
         * Current name for OpenAI Chat Completions. The limit includes
         * reasoning tokens.
         */
        MAX_COMPLETION_TOKENS("max_completion_tokens");

        private final String fieldName;

        TokenLimitParameter(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    private record Rule(String prefix, ModelCapabilities capabilities) {

    }

    /**
     * Non-reasoning OpenAI chat models (GPT-4 family and older).
     */
    private static final ModelCapabilities OPENAI_SAMPLING
            = new ModelCapabilities(true, TokenLimitParameter.MAX_COMPLETION_TOKENS);

    /**
     * OpenAI reasoning models: temperature is fixed, and the token limit covers
     * reasoning as well as the visible answer.
     */
    private static final ModelCapabilities OPENAI_REASONING
            = new ModelCapabilities(false, TokenLimitParameter.MAX_COMPLETION_TOKENS);

    /**
     * Models served by a local OpenAI-compatible server (LM Studio, Ollama).
     */
    private static final ModelCapabilities LOCAL
            = new ModelCapabilities(true, TokenLimitParameter.MAX_TOKENS);

    /**
     * Matched against the lower-cased model name by prefix. The first match
     * wins, so a more specific prefix goes above a shorter one.
     */
    private static final List<Rule> OPENAI_RULES = List.of(
            new Rule("gpt-5", OPENAI_REASONING),
            new Rule("gpt-6", OPENAI_REASONING),
            new Rule("o1", OPENAI_REASONING),
            new Rule("o3", OPENAI_REASONING),
            new Rule("o4", OPENAI_REASONING),
            new Rule("codex-mini", OPENAI_REASONING)
    );

    private final boolean supportsTemperature;
    private final TokenLimitParameter tokenLimitParameter;

    private ModelCapabilities(boolean supportsTemperature, TokenLimitParameter tokenLimitParameter) {
        this.supportsTemperature = supportsTemperature;
        this.tokenLimitParameter = tokenLimitParameter;
    }

    /**
     * Capabilities of a model on the OpenAI API, looked up by model name.
     *
     * @param modelName for example {@code gpt-5-mini-2025-08-07}; a fine-tuned
     * name ({@code ft:gpt-4.1-mini:org::id}) resolves to its base model
     */
    public static ModelCapabilities forOpenAiModel(String modelName) {
        String name = modelName == null ? "" : modelName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("ft:")) {
            name = name.substring(3);
        }
        for (Rule rule : OPENAI_RULES) {
            if (name.startsWith(rule.prefix())) {
                return rule.capabilities();
            }
        }
        return OPENAI_SAMPLING;
    }

    /**
     * Capabilities of a model behind a local OpenAI-compatible server.
     */
    public static ModelCapabilities forLocalModel() {
        return LOCAL;
    }

    /**
     * False when the model rejects any {@code temperature} other than its
     * default. The parameter must then be left out of the request.
     */
    public boolean supportsTemperature() {
        return supportsTemperature;
    }

    public TokenLimitParameter getTokenLimitParameter() {
        return tokenLimitParameter;
    }
}
