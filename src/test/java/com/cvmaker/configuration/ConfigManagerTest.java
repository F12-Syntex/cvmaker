package com.cvmaker.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cvmaker.service.ai.LLMModel;

class ConfigManagerTest {

    private static final String USER_DATA = "Jane Example, jane@example.com, secret CV body";
    private static final String CV_PROMPT = "PRIVATE CV PROMPT TEXT";

    @TempDir
    Path tempDir;

    private ConfigManager load(String... extraLines) throws Exception {
        Path userData = tempDir.resolve("userdata.txt");
        Files.writeString(userData, USER_DATA, StandardCharsets.UTF_8);
        Path cvPrompt = tempDir.resolve("cv_prompt.txt");
        Files.writeString(cvPrompt, CV_PROMPT, StandardCharsets.UTF_8);

        List<String> lines = new ArrayList<>();
        lines.add("input.user.data.file=" + userData);
        lines.add("input.cv.prompt.file=" + cvPrompt);
        lines.add("input.cover.letter.prompt.file=" + tempDir.resolve("absent_cover_prompt.txt"));
        lines.addAll(Arrays.asList(extraLines));

        Path file = tempDir.resolve("config.properties");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return new ConfigManager(file.toString());
    }

    @Test
    void defaultsApplyWhenKeysAreAbsent() throws Exception {
        ConfigManager config = load();

        assertThat(config.getAiModel()).isEqualTo(LLMModel.GPT_4_1_MINI);
        assertThat(config.getAiTemperature()).isEqualTo(0.3);
        assertThat(config.getAiMaxRetries()).isEqualTo(3);
        assertThat(config.getAiTimeoutSeconds()).isEqualTo(60);
        assertThat(config.getLatexCompilerArgs()).containsExactly("-interaction=nonstopmode");
        assertThat(config.isGenerateCoverLetter()).isTrue();
        assertThat(config.getUserDataContent()).isEqualTo(USER_DATA);
    }

    @Test
    void malformedIntegersFallBackToDefaultInsteadOfThrowing() throws Exception {
        ConfigManager config = load(
                "ai.request_delay_ms=1s",
                "ai.max_retries=three",
                "ai.timeout_seconds=",
                "latex.progress.report.interval=x");

        assertThat(config.getAiRequestDelayMs()).isEqualTo(1000);
        assertThat(config.getAiMaxRetries()).isEqualTo(3);
        assertThat(config.getAiTimeoutSeconds()).isEqualTo(60);
        assertThat(config.getProgressReportInterval()).isEqualTo(5);
    }

    @Test
    void integersWithWhitespaceAreAccepted() throws Exception {
        ConfigManager config = load("ai.max_retries= 5 ");

        assertThat(config.getAiMaxRetries()).isEqualTo(5);
    }

    @Test
    void outOfRangeIntegersFallBackToDefault() throws Exception {
        ConfigManager config = load(
                "ai.max_retries=-2",
                "ai.timeout_seconds=0");

        assertThat(config.getAiMaxRetries()).isEqualTo(3);
        assertThat(config.getAiTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void temperatureAcceptsTheFullOpenAiRange() throws Exception {
        assertThat(load("ai.temperature=1.5").getAiTemperature()).isEqualTo(1.5);
        assertThat(load("ai.temperature=2.0").getAiTemperature()).isEqualTo(2.0);
    }

    @Test
    void temperatureOutOfRangeOrMalformedFallsBackToDefault() throws Exception {
        assertThat(load("ai.temperature=2.5").getAiTemperature()).isEqualTo(0.3);
        assertThat(load("ai.temperature=-0.1").getAiTemperature()).isEqualTo(0.3);
        assertThat(load("ai.temperature=warm").getAiTemperature()).isEqualTo(0.3);
    }

    @Test
    void modelNameIsTrimmedAndCaseInsensitive() throws Exception {
        assertThat(load("ai.model= gpt_4_1_nano ").getAiModel()).isEqualTo(LLMModel.GPT_4_1_NANO);
    }

    @Test
    void unknownModelFallsBackToDefault() throws Exception {
        assertThat(load("ai.model=GPT_9").getAiModel()).isEqualTo(LLMModel.GPT_4_1_MINI);
    }

    @Test
    void listValuesAreTrimmedAndEmptyEntriesDropped() throws Exception {
        ConfigManager config = load(
                "latex.compiler.args= -interaction=nonstopmode , -halt-on-error ,",
                "latex.cleanup.extensions=.log, .aux ,,.out");

        assertThat(config.getLatexCompilerArgs()).containsExactly("-interaction=nonstopmode", "-halt-on-error");
        assertThat(config.getCleanupExtensions()).containsExactly(".log", ".aux", ".out");
    }

    @Test
    void misspelledBooleanFallsBackToDefault() throws Exception {
        ConfigManager config = load(
                "output.generate.cover.letter=flase",
                "debug.save.ai.responses= TRUE ");

        assertThat(config.isGenerateCoverLetter()).isTrue();
        assertThat(config.isSaveAiResponses()).isTrue();
    }

    @Test
    void jobDescriptionContentIsEmptyNotNullWithoutFileOrUrl() throws Exception {
        ConfigManager config = load("input.job.url=", "input.job.description.file=");

        assertThat(config.getJobDescriptionContent()).isEqualTo("");
    }

    @Test
    void jobDescriptionIsReadFromFileWhenNoUrl() throws Exception {
        Path jd = tempDir.resolve("jd.txt");
        Files.writeString(jd, "Job text", StandardCharsets.UTF_8);

        ConfigManager config = load("input.job.description.file=" + jd);

        assertThat(config.getJobDescriptionContent()).isEqualTo("Job text");
        assertThat(config.hasJobUrl()).isFalse();
    }

    @Test
    void toStringDoesNotLeakFileContents() throws Exception {
        ConfigManager config = load();

        String text = config.toString();

        assertThat(text).doesNotContain(USER_DATA).doesNotContain(CV_PROMPT);
        assertThat(text).contains("templateName");
    }

    @Test
    void programmaticConstructorToleratesNullJobUrl() throws Exception {
        Path userData = tempDir.resolve("userdata2.txt");
        Files.writeString(userData, USER_DATA, StandardCharsets.UTF_8);

        ConfigManager config = new ConfigManager(null, userData.toString(),
                tempDir.resolve("no_cv_prompt.txt").toString(), tempDir.resolve("no_cl_prompt.txt").toString());

        assertThat(config.hasJobUrl()).isFalse();
        assertThat(config.getJobDescriptionContent()).isEqualTo("");
        assertThat(config.getCvPromptContent()).isEqualTo("");
    }
}
