package com.cvmaker.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * The pure text helpers of {@link AiService} (prompt building, LaTeX
 * extraction) and the arithmetic of {@link AiOptions} and {@link LLMException}.
 */
class AiServiceTextTest {

    private static final String DOC = "\\documentclass{article}\n\\begin{document}\nHello\n\\end{document}";

    // ---- extractLatexFromResponse ----
    @Test
    void plainDocumentIsReturnedUnchanged() {
        assertThat(AiService.extractLatexFromResponse(DOC)).isEqualTo(DOC);
    }

    @Test
    void nullAndBlankAnswersGiveAnEmptyString() {
        assertThat(AiService.extractLatexFromResponse(null)).isEmpty();
        assertThat(AiService.extractLatexFromResponse("   \n ")).isEmpty();
    }

    @Test
    void codeFenceWithLanguageIsStripped() {
        assertThat(AiService.extractLatexFromResponse("```latex\n" + DOC + "\n```")).isEqualTo(DOC);
    }

    @Test
    void codeFenceWithoutLanguageIsStripped() {
        assertThat(AiService.extractLatexFromResponse("```\n" + DOC + "\n```\n")).isEqualTo(DOC);
    }

    @Test
    void textBeforeDocumentclassIsDropped() {
        assertThat(AiService.extractLatexFromResponse("Sure, here it is:\n\n" + DOC)).isEqualTo(DOC);
    }

    @Test
    void fenceAfterAnIntroductionAndTextAfterTheDocumentAreDropped() {
        String answer = "Here is the CV:\n```latex\n" + DOC + "\n```\nLet me know if you want changes.";

        assertThat(AiService.extractLatexFromResponse(answer)).isEqualTo(DOC);
    }

    @Test
    void answerWithoutDocumentclassKeepsItsText() {
        assertThat(AiService.extractLatexFromResponse("```\n\\section{Skills}\nJava\n```"))
                .isEqualTo("\\section{Skills}\nJava");
    }

    @Test
    void leadingBlankLinesAreDropped() {
        assertThat(AiService.extractLatexFromResponse("\n\n" + DOC + "\n\n")).isEqualTo(DOC);
    }

    // ---- buildLatexPrompt ----
    @Test
    void promptHasInstructionsThenLabelledSections() {
        String prompt = AiService.buildLatexPrompt("Write a CV.", "Jane Doe", "TEMPLATE", "QA lead", "LATEX TEMPLATE");

        assertThat(prompt).isEqualTo(
                "Write a CV.\n\n"
                + "JOB DESCRIPTION:\nQA lead\n\n"
                + "CANDIDATE INFORMATION:\nJane Doe\n\n"
                + "LATEX TEMPLATE:\nTEMPLATE\n\n");
    }

    @Test
    void instructionsAreSeparatedFromTheFirstSection() {
        String prompt = AiService.buildLatexPrompt("Write a CV.", "Jane Doe", null, "QA lead", "LATEX TEMPLATE");

        assertThat(prompt).doesNotContain("Write a CV.JOB DESCRIPTION").startsWith("Write a CV.\n\nJOB DESCRIPTION:");
    }

    @Test
    void instructionsThatAlreadyEndWithNewlinesGetExactlyOneBlankLine() {
        String prompt = AiService.buildLatexPrompt("Write a CV.\n\n\n", "Jane", null, null, "LATEX TEMPLATE");

        assertThat(prompt).isEqualTo("Write a CV.\n\nCANDIDATE INFORMATION:\nJane\n\n");
    }

    @Test
    void nullInstructionsDoNotBecomeTheWordNull() {
        String prompt = AiService.buildLatexPrompt(null, "Jane Doe", "TEMPLATE", "QA lead", "LATEX TEMPLATE");

        assertThat(prompt).doesNotContain("null").startsWith("JOB DESCRIPTION:");
    }

    @Test
    void blankJobDescriptionAndTemplateAreLeftOut() {
        String prompt = AiService.buildLatexPrompt("Write.", "Jane Doe", "  ", " \n", "LATEX TEMPLATE");

        assertThat(prompt).isEqualTo("Write.\n\nCANDIDATE INFORMATION:\nJane Doe\n\n");
    }

    @Test
    void nullUserDataGivesAnEmptySectionNotTheWordNull() {
        String prompt = AiService.buildLatexPrompt("Write.", null, null, null, "LATEX TEMPLATE");

        assertThat(prompt).isEqualTo("Write.\n\nCANDIDATE INFORMATION:\n\n\n");
    }

    @Test
    void templateLabelIsUsedAsGiven() {
        String prompt = AiService.buildLatexPrompt("Write.", "Jane", "T", null, "LATEX COVER LETTER TEMPLATE");

        assertThat(prompt).endsWith("LATEX COVER LETTER TEMPLATE:\nT\n\n");
    }

    // ---- AiOptions ----
    @Test
    void defaultsAreGenerousAndBounded() {
        AiOptions defaults = AiOptions.defaults();

        assertThat(defaults.getTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(defaults.hasTimeout()).isTrue();
        assertThat(defaults.getMaxRetries()).isEqualTo(2);
        assertThat(defaults.getRetryDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(defaults.getMaxTokens()).isNull();
    }

    @Test
    void optionsFromConfigValues() {
        AiOptions options = AiOptions.of(60, 3, 1000);

        assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(options.getMaxRetries()).isEqualTo(3);
        assertThat(options.getRetryDelay()).isEqualTo(Duration.ofMillis(1000));
    }

    @Test
    void zeroOrNegativeConfigValuesSwitchTheFeatureOff() {
        AiOptions options = AiOptions.of(0, -1, -5);

        assertThat(options.hasTimeout()).isFalse();
        assertThat(options.getMaxRetries()).isZero();
        assertThat(options.backoffMillis(1)).isZero();
        assertThat(AiOptions.builder().timeout(null).retryDelay(null).maxTokens(0).build().getMaxTokens()).isNull();
    }

    @Test
    void backoffDoublesAndIsCapped() {
        AiOptions options = AiOptions.of(60, 10, 1000);

        assertThat(options.backoffMillis(1)).isEqualTo(1000);
        assertThat(options.backoffMillis(2)).isEqualTo(2000);
        assertThat(options.backoffMillis(3)).isEqualTo(4000);
        assertThat(options.backoffMillis(6)).isEqualTo(30000);
        assertThat(options.backoffMillis(500)).isEqualTo(30000);
    }

    // ---- LLMException ----
    @Test
    void retryabilityFollowsTheHttpStatus() {
        assertThat(LLMException.isRetryableStatus(400)).isFalse();
        assertThat(LLMException.isRetryableStatus(401)).isFalse();
        assertThat(LLMException.isRetryableStatus(404)).isFalse();
        assertThat(LLMException.isRetryableStatus(408)).isTrue();
        assertThat(LLMException.isRetryableStatus(429)).isTrue();
        assertThat(LLMException.isRetryableStatus(500)).isTrue();
        assertThat(LLMException.isRetryableStatus(503)).isTrue();
    }

    @Test
    void retryabilityIsReadThroughWrappers() {
        LLMException rejected = new LLMException("HTTP 400", false);

        assertThat(LLMException.isRetryable(new RuntimeException("wrapped", rejected))).isFalse();
        assertThat(LLMException.isRetryable(new RuntimeException("wrapped", new LLMException("HTTP 503", true)))).isTrue();
        assertThat(LLMException.isRetryable(new RuntimeException("socket closed"))).isTrue();
        assertThat(LLMException.isRetryable(new RuntimeException(new IllegalArgumentException("no prompt")))).isFalse();
        assertThat(LLMException.isRetryable(new RuntimeException(new InterruptedException()))).isFalse();
    }
}
