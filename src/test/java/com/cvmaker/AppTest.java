package com.cvmaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AppTest {

    @Test
    void runGenerationShutsTheGeneratorDownAfterSuccess() throws Exception {
        CVGenerator generator = mock(CVGenerator.class);

        App.runGeneration(generator);

        InOrder order = inOrder(generator);
        order.verify(generator).generate();
        order.verify(generator).shutdown();
    }

    @Test
    void runGenerationShutsTheGeneratorDownWhenGenerationFails() throws Exception {
        CVGenerator generator = mock(CVGenerator.class);
        IllegalStateException failure = new IllegalStateException("LaTeX compiler not found");
        doThrow(failure).when(generator).generate();

        assertThatThrownBy(() -> App.runGeneration(generator)).isSameAs(failure);

        verify(generator).shutdown();
    }

    @Test
    void formatDurationPicksAReadableUnit() {
        assertThat(App.formatDuration(999)).isEqualTo("999ms");
        assertThat(App.formatDuration(1500)).isEqualTo("1.5s");
        assertThat(App.formatDuration(125_000)).isEqualTo("2m 5s");
    }
}
