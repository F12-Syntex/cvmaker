package com.cvmaker.service.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * An {@link LLMClient} for tests. Each call takes the next scripted step; when
 * the script runs out, the last step repeats. Nothing reaches the network.
 */
class FakeLLMClient implements LLMClient {

    private final Deque<Function<LLMRequest, LLMResponse>> script = new ArrayDeque<>();
    private Function<LLMRequest, LLMResponse> lastStep = request -> answer("ok");

    final List<LLMRequest> requests = new CopyOnWriteArrayList<>();
    final AtomicInteger shutdowns = new AtomicInteger();

    static LLMResponse answer(String content) {
        return LLMResponse.builder().content(content).build();
    }

    FakeLLMClient thenAnswer(String content) {
        return then(request -> answer(content));
    }

    FakeLLMClient thenFail(RuntimeException failure) {
        return then(request -> {
            throw failure;
        });
    }

    FakeLLMClient then(Function<LLMRequest, LLMResponse> step) {
        script.add(step);
        return this;
    }

    int calls() {
        return requests.size();
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        Function<LLMRequest, LLMResponse> step;
        synchronized (this) {
            requests.add(request);
            if (!script.isEmpty()) {
                lastStep = script.poll();
            }
            step = lastStep;
        }
        return step.apply(request);
    }

    @Override
    public void shutdown() {
        shutdowns.incrementAndGet();
    }
}
