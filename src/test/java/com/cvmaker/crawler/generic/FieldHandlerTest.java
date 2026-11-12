package com.cvmaker.crawler.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cvmaker.crawler.FormField;
import com.cvmaker.crawler.LabelResolver;
import com.cvmaker.crawler.generic.FieldHandler.Option;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

class FieldHandlerTest {

    private final Page page = mock(Page.class);
    private final FieldHandler handler = new FieldHandler(page);

    /** A mocked element with the given tag and type attribute, wrapped in a FormField. */
    private FormField field(Locator el, String tag, String type, String name) {
        when(el.evaluate("el => el.tagName")).thenReturn(tag);
        when(el.getAttribute("type")).thenReturn(type);
        when(el.getAttribute("name")).thenReturn(name);
        when(el.getAttribute("id")).thenReturn(name);
        when(el.page()).thenReturn(page);
        when(page.locator(anyString())).thenReturn(mock(Locator.class));
        return new FormField(el);
    }

    private static Locator option(String text, String value) {
        Locator o = mock(Locator.class);
        when(o.textContent()).thenReturn(text);
        when(o.getAttribute("value")).thenReturn(value);
        return o;
    }

    private static Locator list(Locator... items) {
        Locator l = mock(Locator.class);
        when(l.count()).thenReturn(items.length);
        for (int i = 0; i < items.length; i++) {
            when(l.nth(i)).thenReturn(items[i]);
        }
        return l;
    }

    @Test
    void selectIsDispatchedOnTagAndPicksTheMatchingOption() {
        Locator select = mock(Locator.class);
        FormField f = field(select, "SELECT", null, "rtw");
        Locator options = list(option("Please select", ""), option("Yes", "Y"), option("No", "N"));
        when(select.locator("option")).thenReturn(options);
        when(select.inputValue()).thenReturn("Y");

        assertThat(f.getKind()).isEqualTo("select");
        assertThat(handler.fill(f, " yes ")).isTrue();
        verify(select).selectOption("Y");
        verify(select, never()).fill(anyString());
    }

    @Test
    void selectWithNoMatchingOptionIsLeftUnfilledInsteadOfPickingTheFirst() {
        Locator select = mock(Locator.class);
        FormField f = field(select, "SELECT", null, "visa");
        Locator options = list(option("Yes", "Y"), option("No", "N"));
        when(select.locator("option")).thenReturn(options);

        assertThat(handler.fill(f, "Maybe")).isFalse();
        verify(select, never()).selectOption(anyString());
        assertThat(handler.getUnfilledFields()).singleElement().asString().startsWith("visa:");
    }

    @Test
    void textareaIsFilled() {
        Locator area = mock(Locator.class);
        FormField f = field(area, "TEXTAREA", null, "why");
        when(area.inputValue()).thenReturn("Because");

        assertThat(f.getKind()).isEqualTo("textarea");
        assertThat(handler.fill(f, "Because")).isTrue();
        verify(area).fill("Because");
    }

    @Test
    void checkboxIsVerifiedWithIsCheckedNotInputValue() {
        Locator box = mock(Locator.class);
        FormField f = field(box, "INPUT", "checkbox", "terms");
        when(box.inputValue()).thenReturn("on"); // what inputValue() gives for any checkbox
        when(box.isChecked()).thenReturn(false); // the click did not take

        assertThat(handler.fill(f, "yes")).isFalse();
        verify(box).check();
    }

    @Test
    void checkboxUncheckIsVerified() {
        Locator box = mock(Locator.class);
        FormField f = field(box, "INPUT", "checkbox", "news");
        when(box.isChecked()).thenReturn(false);

        assertThat(handler.fill(f, "no")).isTrue();
        verify(box).uncheck();
    }

    @Test
    void radioWithNoMatchingChoiceChecksNothing() {
        Locator radio = mock(Locator.class);
        FormField f = field(radio, "INPUT", "radio", "sponsor");
        Locator yes = option(null, "yes");
        Locator no = option(null, "no");
        Locator radios = list(yes, no);
        when(page.locator(LabelResolver.radioGroupSelector("sponsor"))).thenReturn(radios);

        assertThat(handler.fill(f, "perhaps")).isFalse();
        verify(yes, never()).check();
        verify(no, never()).check();
    }

    @Test
    void radioChecksTheMatchingChoice() {
        Locator radio = mock(Locator.class);
        FormField f = field(radio, "INPUT", "radio", "sponsor");
        Locator yes = option(null, "yes");
        Locator no = option(null, "no");
        when(no.isChecked()).thenReturn(true);
        Locator radios = list(yes, no);
        when(page.locator(LabelResolver.radioGroupSelector("sponsor"))).thenReturn(radios);

        assertThat(handler.fill(f, "No")).isTrue();
        verify(no).check();
        verify(yes, never()).check();
    }

    @Test
    void radioMatchesOnItsLabelTextWhenTheValueIsOpaque() {
        Locator radio = mock(Locator.class);
        FormField f = field(radio, "INPUT", "radio", "ctl00$Main$Sponsor");
        Locator first = option(null, "1");
        when(first.getAttribute("id")).thenReturn("ctl00$Main$Sponsor_0");
        Locator second = option(null, "2");
        when(second.getAttribute("id")).thenReturn("ctl00$Main$Sponsor_1");
        when(second.isChecked()).thenReturn(true);
        Locator label = mock(Locator.class);
        when(label.count()).thenReturn(1);
        when(label.first()).thenReturn(label);
        when(label.textContent()).thenReturn("  No,\n I do not ");
        when(page.locator(LabelResolver.labelForSelector("ctl00$Main$Sponsor_1"))).thenReturn(label);
        Locator group = list(first, second);
        when(page.locator(LabelResolver.radioGroupSelector("ctl00$Main$Sponsor"))).thenReturn(group);

        assertThat(handler.fill(f, "No, I do not")).isTrue();
        verify(second).check();
        verify(first, never()).check();
    }

    @Test
    void kindOfUsesTagBeforeType() {
        assertThat(FormField.kindOf("SELECT", null)).isEqualTo("select");
        assertThat(FormField.kindOf("textarea", "text")).isEqualTo("textarea");
        assertThat(FormField.kindOf("input", "Email")).isEqualTo("email");
        assertThat(FormField.kindOf("input", null)).isEqualTo("text");
        assertThat(FormField.kindOf(null, null)).isEqualTo("text");
    }

    @Test
    void findOptionValueMatchesValueThenTextAndNeverGuesses() {
        List<Option> opts = List.of(new Option("Please select", ""), new Option("United Kingdom", "GB"),
                new Option("  Ireland ", null));
        assertThat(FieldHandler.findOptionValue(opts, "gb")).isEqualTo("GB");
        assertThat(FieldHandler.findOptionValue(opts, "united  kingdom")).isEqualTo("GB");
        assertThat(FieldHandler.findOptionValue(opts, "Ireland")).isEqualTo("Ireland");
        assertThat(FieldHandler.findOptionValue(opts, "France")).isNull();
        assertThat(FieldHandler.findOptionValue(opts, "")).isNull();
        assertThat(FieldHandler.findOptionValue(opts, null)).isNull();
    }

    @Test
    void affirmativeValues() {
        assertThat(FieldHandler.isAffirmative(" Yes ")).isTrue();
        assertThat(FieldHandler.isAffirmative("true")).isTrue();
        assertThat(FieldHandler.isAffirmative("no")).isFalse();
        assertThat(FieldHandler.isAffirmative(null)).isFalse();
    }
}
