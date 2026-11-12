package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

class PageVisualizerTest {

    @Test
    void enabledVisualizerInstallsTheScriptForFutureAndCurrentDocuments() {
        Page page = mock(Page.class);

        PageVisualizer visualizer = new PageVisualizer(page, true);

        assertThat(visualizer.isEnabled()).isTrue();
        verify(page).addInitScript(PageVisualizer.INIT_SCRIPT);
        verify(page).evaluate(PageVisualizer.INIT_SCRIPT);
    }

    @Test
    void initScriptIsInvokedAndDefinesBothHelpers() {
        // Playwright runs init scripts as plain source: a bare "() => {...}" is
        // evaluated but never called, which is how this script used to be dead.
        assertThat(PageVisualizer.INIT_SCRIPT).startsWith("(() => {").endsWith("})();");
        assertThat(PageVisualizer.INIT_SCRIPT)
                .contains("window.highlightElement = function")
                .contains("window.displayAction = function");
    }

    @Test
    void disabledVisualizerNeverTouchesThePage() {
        Page page = mock(Page.class);
        Locator element = mock(Locator.class);

        PageVisualizer visualizer = new PageVisualizer(page, false);
        visualizer.visualizeAction("Applying");
        visualizer.highlightElements("button");
        visualizer.highlightElement(element);

        assertThat(visualizer.isEnabled()).isFalse();
        verifyNoInteractions(page, element);
    }

    @Test
    void disabledFactoryAndMissingPageAreSafeNoOps() {
        PageVisualizer disabled = PageVisualizer.disabled();
        PageVisualizer noPage = new PageVisualizer(null, true);

        assertThat(disabled.isEnabled()).isFalse();
        assertThat(noPage.isEnabled()).isFalse();
        assertThatCode(() -> {
            disabled.visualizeAction("x");
            disabled.highlightElements("x");
            disabled.highlightElement(mock(Locator.class));
            noPage.visualizeAction("x");
            noPage.highlightElements("x");
        }).doesNotThrowAnyException();
    }

    @Test
    void visualizeActionPassesTheTextAsAnArgumentNotAsScript() {
        Page page = mock(Page.class);
        PageVisualizer visualizer = new PageVisualizer(page, true);
        String action = "Applying to \"Dev\"'); alert(1); //";

        visualizer.visualizeAction(action);

        verify(page).evaluate(anyString(), eq(action));
    }

    @Test
    void highlightElementsPassesTheSelectorAsAnArgument() {
        Page page = mock(Page.class);
        when(page.evaluate(anyString(), any())).thenReturn(2);
        PageVisualizer visualizer = new PageVisualizer(page, true);

        visualizer.highlightElements("button[type='submit']");

        verify(page).evaluate(anyString(), eq("button[type='submit']"));
    }

    @Test
    void highlightElementUsesACompleteArrowFunction() {
        Page page = mock(Page.class);
        Locator element = mock(Locator.class);
        PageVisualizer visualizer = new PageVisualizer(page, true);

        visualizer.highlightElement(element);
        visualizer.highlightElement(null);

        org.mockito.ArgumentCaptor<String> script = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(element).evaluate(script.capture());
        // The closing brace of the function body used to be missing (a syntax error in the page).
        assertThat(script.getValue()).startsWith("el => {").endsWith("}");
        assertThat(count(script.getValue(), '{')).isEqualTo(count(script.getValue(), '}'));
    }

    @Test
    void pageFailuresNeverEscape() {
        Page page = mock(Page.class);
        doThrow(new PlaywrightException("Target closed")).when(page).addInitScript(anyString());
        when(page.evaluate(anyString(), any())).thenThrow(new PlaywrightException("Execution context was destroyed"));
        Locator element = mock(Locator.class);
        when(element.evaluate(anyString())).thenThrow(new PlaywrightException("Element is not attached"));

        assertThatCode(() -> {
            PageVisualizer visualizer = new PageVisualizer(page, true);
            visualizer.visualizeAction("x");
            visualizer.highlightElements("x");
            visualizer.highlightElement(element);
        }).doesNotThrowAnyException();
    }

    private static long count(String text, char c) {
        return text.chars().filter(ch -> ch == c).count();
    }
}
