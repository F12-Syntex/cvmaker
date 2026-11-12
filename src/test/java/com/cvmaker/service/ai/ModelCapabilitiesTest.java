package com.cvmaker.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cvmaker.service.ai.ModelCapabilities.TokenLimitParameter;

class ModelCapabilitiesTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "gpt-5", "gpt-5-mini", "gpt-5-mini-2025-08-07", "gpt-5-nano", "gpt-5.1", "gpt-5.4-mini",
        "gpt-6-astra", "o1", "o3-mini", "o4-mini", "codex-mini",
        "GPT-5-MINI", "  gpt-5-mini  ", "ft:gpt-5-mini:acme::abc123"})
    void reasoningModelsDoNotTakeTemperature(String modelName) {
        ModelCapabilities capabilities = ModelCapabilities.forOpenAiModel(modelName);

        assertThat(capabilities.supportsTemperature()).isFalse();
        assertThat(capabilities.getTokenLimitParameter()).isEqualTo(TokenLimitParameter.MAX_COMPLETION_TOKENS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo",
        "ft:gpt-4.1-mini:acme::abc123", "some-future-model"})
    void otherOpenAiModelsTakeTemperature(String modelName) {
        ModelCapabilities capabilities = ModelCapabilities.forOpenAiModel(modelName);

        assertThat(capabilities.supportsTemperature()).isTrue();
        assertThat(capabilities.getTokenLimitParameter()).isEqualTo(TokenLimitParameter.MAX_COMPLETION_TOKENS);
    }

    @Test
    void nullModelNameFallsBackToTheDefaultRow() {
        assertThat(ModelCapabilities.forOpenAiModel(null).supportsTemperature()).isTrue();
    }

    @Test
    void localModelsUseTheLegacyTokenLimitName() {
        ModelCapabilities capabilities = ModelCapabilities.forLocalModel();

        assertThat(capabilities.supportsTemperature()).isTrue();
        assertThat(capabilities.getTokenLimitParameter()).isEqualTo(TokenLimitParameter.MAX_TOKENS);
        assertThat(capabilities.getTokenLimitParameter().getFieldName()).isEqualTo("max_tokens");
    }

    @Test
    void enumConstantsExposeTheirCapabilities() {
        assertThat(LLMModel.GPT_5_MINI.supportsTemperature()).isFalse();
        assertThat(LLMModel.GPT_5_1_MINI.supportsTemperature()).isFalse();
        assertThat(LLMModel.GPT_4_1.supportsTemperature()).isTrue();
        assertThat(LLMModel.GPT_4_1_MINI.supportsTemperature()).isTrue();
        assertThat(LLMModel.GPT_4_1_NANO.supportsTemperature()).isTrue();
        assertThat(LLMModel.MISTRAL_SMALL_3_2.supportsTemperature()).isTrue();
        assertThat(LLMModel.MISTRAL_SMALL_3_2.getCapabilities().getTokenLimitParameter())
                .isEqualTo(TokenLimitParameter.MAX_TOKENS);
    }

    @Test
    void oldGpt5ConstantStillResolvesAndPointsAtTheSameModel() {
        assertThat(LLMModel.valueOf("GPT_5_1_MINI").getModelName())
                .isEqualTo(LLMModel.GPT_5_MINI.getModelName())
                .startsWith("gpt-5-mini");
    }
}
