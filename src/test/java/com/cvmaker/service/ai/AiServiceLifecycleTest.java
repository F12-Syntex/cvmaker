package com.cvmaker.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Threads and shutdown of {@link AiService}: nothing may keep the JVM alive or
 * leak when a caller forgets to shut down, shuts down twice, or the client
 * fails to close.
 */
@Timeout(value = 20, unit = TimeUnit.SECONDS)
class AiServiceLifecycleTest {

    private static final AiOptions FAST = AiOptions.builder()
            .timeout(Duration.ofSeconds(10)).maxRetries(0).build();

    private static AiService service(LLMClient client) {
        return new AiService(client, LLMModel.GPT_4_1_MINI, 0.3, FAST, millis -> {
        });
    }

    @Test
    void callsRunOnNamedDaemonThreads() {
        AtomicBoolean daemon = new AtomicBoolean();
        AtomicReference<String> threadName = new AtomicReference<>();
        FakeLLMClient client = new FakeLLMClient().then(request -> {
            daemon.set(Thread.currentThread().isDaemon());
            threadName.set(Thread.currentThread().getName());
            return FakeLLMClient.answer("ok");
        });

        try (AiService service = service(client)) {
            service.query("hi");
        }

        assertThat(daemon).isTrue();
        assertThat(threadName.get()).startsWith("ai-service-worker-");
    }

    @Test
    void creatingAServiceStartsNoThread() {
        long before = workerThreads();

        AiService service = service(new FakeLLMClient());

        // "At most": a worker of an earlier test may end in between.
        assertThat(workerThreads()).isLessThanOrEqualTo(before);
        service.shutdown();
    }

    @Test
    void closeShutsTheClientDownOnce() {
        FakeLLMClient client = new FakeLLMClient();
        AiService service = service(client);

        try (service) {
            service.query("hi");
        }
        service.shutdown();
        service.close();

        assertThat(client.shutdowns).hasValue(1);
        assertThat(service.isShutDown()).isTrue();
    }

    @Test
    void queryAfterShutdownFailsFastWithoutCallingTheClient() {
        FakeLLMClient client = new FakeLLMClient();
        AiService service = service(client);
        service.shutdown();

        assertThatThrownBy(() -> service.query("hi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shut down");
        assertThat(client.calls()).isZero();
    }

    @Test
    void workersAreStoppedEvenWhenTheClientFailsToShutDown() {
        LLMClient failingClient = new LLMClient() {
            @Override
            public LLMResponse complete(LLMRequest request) {
                return FakeLLMClient.answer("ok");
            }

            @Override
            public void shutdown() {
                throw new RuntimeException("Failed to shutdown OpenAI client: boom");
            }
        };
        AiService service = service(failingClient);
        service.query("hi");

        assertThatThrownBy(service::shutdown).hasMessageContaining("boom");

        assertThat(service.isShutDown()).isTrue();
        assertThatThrownBy(() -> service.query("hi")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shutdownCancelsACallInFlightAndDoesNotWaitForIt() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        FakeLLMClient client = new FakeLLMClient().then(request -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                workerInterrupted.countDown();
            }
            throw new RuntimeException("cancelled");
        });
        AiService service = new AiService(client, LLMModel.GPT_4_1_MINI, 0.3,
                AiOptions.builder().timeout(Duration.ofSeconds(15)).maxRetries(3).build(), millis -> {
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                service.query("hi");
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        caller.start();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        long start = System.nanoTime();
        service.shutdown();
        long shutdownMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        caller.join(5000);

        assertThat(shutdownMillis).isLessThan(4000);
        assertThat(workerInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(caller.isAlive()).isFalse();
        assertThat(failure.get()).isNotNull();
        assertThat(client.calls()).as("no retry after shutdown").isEqualTo(1);
    }

    private static long workerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("ai-service-worker-"))
                .filter(Thread::isAlive)
                .count();
    }
}
