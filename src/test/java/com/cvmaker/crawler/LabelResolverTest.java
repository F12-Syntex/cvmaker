package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.cvmaker.crawler.generic.utils.FieldExtractor;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

class LabelResolverTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
        "email                       | \"email\"",
        "ctl00$Main$txtName          | \"ctl00$Main$txtName\"",
        "ns:field.1                  | \"ns:field.1\"",
        "it's                        | \"it's\"",
        "say \"hi\"                  | \"say \\\"hi\\\"\"",
        "back\\slash                 | \"back\\\\slash\"",
        "``                          | \"\"",
    })
    void cssStringQuotesAndEscapes(String input, String expected) {
        assertThat(LabelResolver.cssString(input)).isEqualTo(expected);
    }

    @Test
    void cssStringEscapesControlCharactersAndNul() {
        assertThat(LabelResolver.cssString("a\nb")).isEqualTo("\"a\\a b\"");
        assertThat(LabelResolver.cssString("a\u0000b")).isEqualTo("\"a�b\"");
        assertThat(LabelResolver.cssString(null)).isEqualTo("\"\"");
    }

    @Test
    void selectorsQuoteTheValue() {
        assertThat(LabelResolver.labelForSelector("ctl00$a'b:c"))
                .isEqualTo("label[for=\"ctl00$a'b:c\"]");
        assertThat(LabelResolver.idSelector("1st:name")).isEqualTo("[id=\"1st:name\"]");
        assertThat(LabelResolver.radioGroupSelector("q\"1"))
                .isEqualTo("input[type=\"radio\"][name=\"q\\\"1\"]");
    }

    private static Locator textLocator(String text) {
        Locator l = mock(Locator.class);
        when(l.count()).thenReturn(1);
        when(l.first()).thenReturn(l);
        when(l.textContent()).thenReturn(text);
        return l;
    }

    private static Locator emptyLocator() {
        Locator l = mock(Locator.class);
        when(l.count()).thenReturn(0);
        return l;
    }

    @Test
    void findLabelUsesLabelForWithAnEscapedSelector() {
        Page page = mock(Page.class);
        Locator field = mock(Locator.class);
        when(field.page()).thenReturn(page);
        when(field.getAttribute("id")).thenReturn("ctl00$Main$it's");
        Locator noMatch = emptyLocator();
        when(page.locator(anyString())).thenReturn(noMatch);
        Locator forLabel = textLocator("  First\n name ");
        when(page.locator("label[for=\"ctl00$Main$it's\"]")).thenReturn(forLabel);

        assertThat(LabelResolver.findLabel(field)).isEqualTo("First name");
        // FormField and FieldExtractor go through the same code
        assertThat(new FormField(field).getLabel()).isEqualTo("First name");
        assertThat(FieldExtractor.findAssociatedLabel(field)).isEqualTo("First name");
    }

    @Test
    void findLabelFallsBackToTheNearestPrecedingLabel() {
        Page page = mock(Page.class);
        Locator field = mock(Locator.class);
        when(field.page()).thenReturn(page);
        when(field.getAttribute("id")).thenReturn("x");
        Locator noMatch = emptyLocator();
        when(page.locator(anyString())).thenReturn(noMatch);
        Locator preceding = textLocator("Surname");
        when(field.locator("xpath=./preceding::label[1]")).thenReturn(preceding);

        assertThat(LabelResolver.findLabel(field)).isEqualTo("Surname");
    }

    @Test
    void findLabelReturnsEmptyWhenThereIsNoLabelOrNoPage() {
        Locator field = mock(Locator.class);
        Locator noMatch = emptyLocator();
        when(field.locator(anyString())).thenReturn(noMatch);
        assertThat(LabelResolver.findLabel(field)).isEmpty();
        assertThat(LabelResolver.findLabel(null)).isEmpty();
        assertThat(new FormField(field).getLabel()).isNull();
    }

    @Test
    void textByIdsJoinsAriaLabelledByTargets() {
        Page page = mock(Page.class);
        Locator noMatch = emptyLocator();
        when(page.locator(anyString())).thenReturn(noMatch);
        Locator firstPart = textLocator("Do you need");
        when(page.locator("[id=\"q$1\"]")).thenReturn(firstPart);
        Locator secondPart = textLocator(" sponsorship? ");
        when(page.locator("[id=\"q:2\"]")).thenReturn(secondPart);

        assertThat(LabelResolver.textByIds(page, " q$1  missing q:2 ")).isEqualTo("Do you need sponsorship?");
        assertThat(LabelResolver.textByIds(page, null)).isEmpty();
    }
}
