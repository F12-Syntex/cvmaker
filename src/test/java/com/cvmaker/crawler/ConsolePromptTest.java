package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ConsolePromptTest {

    /** Records whether close() was called. */
    private static final class TrackingStream extends ByteArrayInputStream {

        boolean closed;

        TrackingStream(String content) {
            super(content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    @Test
    void consumesExactlyOneLineAndLeavesTheStreamOpen() throws Exception {
        TrackingStream in = new TrackingStream("first line\nsecond\n");

        boolean gotLine = ConsolePrompt.waitForEnter(in);

        assertThat(gotLine).isTrue();
        assertThat(in.closed).isFalse();
        // The next prompt still sees its own input.
        assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second\n");
    }

    @Test
    void windowsLineEndingIsConsumedWhole() throws Exception {
        TrackingStream in = new TrackingStream("\r\nnext");

        assertThat(ConsolePrompt.waitForEnter(in)).isTrue();
        assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("next");
    }

    @Test
    void twoPromptsInARowEachWaitForTheirOwnEnter() throws Exception {
        TrackingStream in = new TrackingStream("\n\n");

        assertThat(ConsolePrompt.waitForEnter(in)).isTrue();
        assertThat(ConsolePrompt.waitForEnter(in)).isTrue();
        assertThat(ConsolePrompt.waitForEnter(in)).isFalse();
    }

    @Test
    void endOfStreamReturnsFalseInsteadOfThrowing() throws Exception {
        TrackingStream in = new TrackingStream("no newline");

        assertThat(ConsolePrompt.waitForEnter(in)).isFalse();
        assertThat(in.closed).isFalse();
    }
}
