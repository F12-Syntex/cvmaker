package com.cvmaker.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Timeout, retry and request building of {@link AiService}, with a fake client
 * and a recording sleeper, so the tests neither call an API nor wait.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class AiServiceTest {

    private final FakeLLMClient client = new FakeLLMClient();
    private final List<Long> sleeps = new ArrayList<>();
    private AiService service;

    private AiService service(AiOptions options) {
        return service(LLMModel.GPT_4_1_MINI, 0.3, options);
    }

    private AiService service(LLMModel model, double temperature, AiOptions options) {
        service = new AiService(client, model, temperature, options, sleeps::add);
        return service;
    }

    private static AiOptions options(int maxRetries, long retryDelayMs) {
        return AiOptions.builder()
                .timeout(Duration.ofSeconds(10))
                .maxRetries(maxRetries)
                .retryDelay(Duration.ofMillis(retryDelayMs))
                .build();
    }

    @AfterEach
    void shutDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    // ---- request building ----
    @Test
    void queryReturnsTheAnswerAndSendsModelAndTemperature() {
        client.thenAnswer("42");

        String answer = service(LLMModel.GPT_4_1, 0.9, AiOptions.defaults()).query("What is 6 x 7?");

        assertThat(answer).isEqualTo("42");
        assertThat(client.requests).hasSize(1);
        LLMRequest sent = client.requests.get(0);
        assertThat(sent.getPrompt()).isEqualTo("What is 6 x 7?");
        assertThat(sent.getModel()).isEqualTo(LLMModel.GPT_4_1.getModelName());
        assertThat(sent.getTemperature()).isEqualTo(0.9);
        assertThat(sent.hasMaxTokens()).isFalse();
    }

    @Test
    void maxTokensFromTheOptionsIsPutOnTheRequest() {
        AiOptions withLimit = AiOptions.builder().maxTokens(6000).build();

        service(withLimit).query("hi");

        assertThat(client.requests.get(0).hasMaxTokens()).isTrue();
        assertThat(client.requests.get(0).getMaxTokens()).isEqualTo(6000);
    }

    @Test
    void settersChangeLaterRequests() {
        service(AiOptions.defaults()).setModel(LLMModel.GPT_5_MINI).setTemperature(0.1).query("hi");

        assertThat(client.requests.get(0).getModel()).isEqualTo(LLMModel.GPT_5_MINI.getModelName());
        assertThat(client.requests.get(0).getTemperature()).isEqualTo(0.1);
    }

    @Test
    void nullOptionsFallBackToTheDefaults() {
        AiService withNullOptions = service(null);

        assertThat(withNullOptions.getOptions().getTimeout()).isEqualTo(AiOptions.DEFAULT_TIMEOUT);
        assertThat(withNullOptions.getOptions().getMaxRetries()).isEqualTo(AiOptions.DEFAULT_MAX_RETRIES);
    }

    // ---- retries ----
    @Test
    void failedCallIsRetriedWithGrowingBackoffUntilItSucceeds() {
        client.thenFail(new RuntimeException("connection reset"))
                .thenFail(new RuntimeException("HTTP 503"))
                .thenAnswer("third time lucky");

        String answer = service(options(3, 100)).query("hi");

        assertThat(answer).isEqualTo("third time lucky");
        assertThat(client.calls()).isEqualTo(3);
        assertThat(sleeps).containsExactly(100L, 200L);
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfRetries() {
        RuntimeException failure = new RuntimeException("HTTP 503");
        client.thenFail(failure);

        assertThatThrownBy(() -> service(options(2, 50)).query("hi"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Query failed")
                .hasMessageContaining("HTTP 503")
                .hasRootCause(failure);

        assertThat(client.calls()).isEqualTo(3);
        assertThat(sleeps).containsExactly(50L, 100L);
    }

    @Test
    void zeroRetriesMeansASingleAttempt() {
        client.thenFail(new RuntimeException("boom"));

        assertThatThrownBy(() -> service(options(0, 50)).query("hi")).hasMessageContaining("boom");

        assertThat(client.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void rejectedRequestIsNotRetried() {
        client.thenFail(new LLMException("OpenAI API call failed: 400 unsupported value: temperature", false));

        assertThatThrownBy(() -> service(options(3, 50)).query("hi"))
                .hasMessageContaining("unsupported value");

        assertThat(client.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void invalidRequestIsNotRetried() {
        client.thenFail(new IllegalArgumentException("LLM request has no prompt"));

        assertThatThrownBy(() -> service(options(3, 50)).query(null)).hasMessageContaining("no prompt");

        assertThat(client.calls()).isEqualTo(1);
    }

    @Test
    void retryableLlmExceptionIsRetried() {
        client.thenFail(new LLMException("API returned status code: 429", true)).thenAnswer("ok now");

        assertThat(service(options(1, 0)).query("hi")).isEqualTo("ok now");
        assertThat(client.calls()).isEqualTo(2);
        assertThat(sleeps).containsExactly(0L);
    }

    // ---- timeout ----
    @Test
    void callThatNeverAnswersTimesOutAndTheWorkerIsInterrupted() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        client.then(request -> {
            try {
                never.await();
            } catch (InterruptedException e) {
                workerInterrupted.countDown();
            }
            throw new RuntimeException("cancelled");
        });
        AiOptions shortTimeout = AiOptions.builder()
                .timeout(Duration.ofMillis(150)).maxRetries(0).build();

        long start = System.nanoTime();
        assertThatThrownBy(() -> service(shortTimeout).query("hi"))
                .hasMessageContaining("timed out after 0.15 seconds");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMillis).isBetween(100L, 5000L);
        assertThat(client.calls()).isEqualTo(1);
        assertThat(workerInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void timedOutCallIsRetriedEvenWhileTheFirstAttemptStillHoldsItsThread() {
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean firstAttemptIgnoredInterrupt = new AtomicBoolean();
        client.then(request -> {
            // Like a blocking socket read: does not react to the interrupt.
            while (true) {
                try {
                    if (release.await(10, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    firstAttemptIgnoredInterrupt.set(true);
                }
            }
            return FakeLLMClient.answer("too late");
        }).thenAnswer("second attempt");
        AiOptions shortTimeout = AiOptions.builder()
                .timeout(Duration.ofMillis(150)).maxRetries(1).retryDelay(Duration.ofMillis(10)).build();

        try {
            assertThat(service(shortTimeout).query("hi")).isEqualTo("second attempt");
            assertThat(client.calls()).isEqualTo(2);
            assertThat(sleeps).containsExactly(10L);
            assertThat(firstAttemptIgnoredInterrupt).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void noTimeoutWaitsForASlowAnswer() {
        client.then(request -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return FakeLLMClient.answer("slow but fine");
        });
        AiOptions noTimeout = AiOptions.builder().timeout(Duration.ZERO).maxRetries(0).build();

        assertThat(service(noTimeout).query("hi")).isEqualTo("slow but fine");
    }

    @Test
    void interruptedCallerStopsWithoutRetrying() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        client.then(request -> {
            started.countDown();
            try {
                never.await();
            } catch (InterruptedException e) {
                // cancelled by the service
            }
            throw new RuntimeException("cancelled");
        });
        AiService slow = service(options(3, 10));
        List<Throwable> thrown = new ArrayList<>();
        AtomicBoolean interruptFlagKept = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            try {
                slow.query("hi");
            } catch (RuntimeException e) {
                thrown.add(e);
                interruptFlagKept.set(Thread.currentThread().isInterrupted());
            }
        });

        caller.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        caller.interrupt();
        caller.join(5000);

        assertThat(caller.isAlive()).isFalse();
        assertThat(thrown).hasSize(1);
        assertThat(thrown.get(0)).hasMessageContaining("interrupted");
        assertThat(interruptFlagKept).isTrue();
        assertThat(client.calls()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    // ---- LaTeX generation ----
    @Test
    void generateCvSendsTheBuiltPromptAndCleansTheAnswer() {
        client.thenAnswer("Here is your CV:\n```latex\n\\documentclass{article}\n\\begin{document}\nJane\n\\end{document}\n```\nGood luck!");

        String latex = service(AiOptions.defaults())
                .generateDirectLatexCV("Jane Doe, tester", "TEMPLATE", "QA lead", "Write a CV.");

        assertThat(latex).isEqualTo("\\documentclass{article}\n\\begin{document}\nJane\n\\end{document}");
        assertThat(client.requests.get(0).getPrompt()).isEqualTo(
                "Write a CV.\n\nJOB DESCRIPTION:\nQA lead\n\nCANDIDATE INFORMATION:\nJane Doe, tester\n\n"
                + "LATEX TEMPLATE:\nTEMPLATE\n\n");
    }

    @Test
    void generateCoverLetterUsesItsOwnTemplateLabel() {
        client.thenAnswer("\\documentclass{letter}\\begin{document}Hi\\end{document}");

        service(AiOptions.defaults())
                .generateDirectLatexCoverLetter("Jane", "TEMPLATE", null, "Write a letter.");

        assertThat(client.requests.get(0).getPrompt()).isEqualTo(
                "Write a letter.\n\nCANDIDATE INFORMATION:\nJane\n\nLATEX COVER LETTER TEMPLATE:\nTEMPLATE\n\n");
    }

    @Test
    void emptyAnswerFailsGenerationWithAClearMessage() {
        client.thenAnswer(null);

        assertThatThrownBy(() -> service(options(0, 0)).generateDirectLatexCV("Jane", null, null, "Write a CV."))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to generate LaTeX CV: the AI returned an empty response");
    }

    // ---- constructors ----
    @Test
    void providerConstructorPicksAModelOfThatProvider() {
        assertThat(LLMModel.defaultFor(LLMProvider.OPENAI).getProvider()).isEqualTo(LLMProvider.OPENAI);
        assertThat(LLMModel.defaultFor(LLMProvider.LOCAL).getProvider()).isEqualTo(LLMProvider.LOCAL);

        // LOCAL builds an OkHttp client and opens no connection, so it is safe offline.
        service = new AiService(LLMProvider.LOCAL);

        assertThat(service.getModel().getProvider()).isEqualTo(LLMProvider.LOCAL);
        assertThat(service.getTemperature()).isEqualTo(0.3);
    }
}
