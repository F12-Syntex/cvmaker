package com.cvmaker.crawler.form;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.cvmaker.crawler.form.FieldClassifier.Section;

class FieldClassifierTest {

    @ParameterizedTest(name = "[{index}] {0}/{1} key={2} label={3} -> {4}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        // applicant: whole-word matches, written together or apart
        "input    | text     | txtFirstName                 | -                          | APPLICANT",
        "input    | text     | firstname                    | -                          | APPLICANT",
        "input    | text     | ctl00$Main$txtName           | -                          | APPLICANT",
        "input    | text     | -                            | Name                       | APPLICANT",
        "input    | text     | -                            | Your full name             | APPLICANT",
        "input    | email    | -                            | E-mail                     | APPLICANT",
        "input    | tel      | tel                          | -                          | APPLICANT",
        "input    | text     | -                            | Phone number               | APPLICANT",
        "input    | url      | linkedInUrl                  | -                          | APPLICANT",
        "input    | url      | -                            | GitHub profile             | APPLICANT",
        "input    | text     | -                            | Post code                  | APPLICANT",
        // substring misfires of the old rules
        "input    | text     | -                            | Hotel chain                | OTHER",
        "input    | number   | -                            | Number of digits           | OTHER",
        "input    | text     | CompanyName                  | -                          | OTHER",
        "input    | text     | -                            | Reference name             | OTHER",
        "input    | text     | -                            | Username                   | OTHER",
        // eligibility
        "select   | -        | rightToWork                  | -                          | ELIGIBILITY",
        "input    | text     | -                            | Notice period (weeks)      | ELIGIBILITY",
        "select   | -        | -                            | Do you require visa sponsorship? | ELIGIBILITY",
        // documents
        "input    | file     | -                            | Your name on the file      | DOCUMENTS",
        "input    | text     | CVUpload                     | -                          | DOCUMENTS",
        "input    | text     | -                            | Résumé link                | DOCUMENTS",
        "input    | text     | -                            | Cover letter               | DOCUMENTS",
        // consents
        "input    | checkbox | -                            | I agree to the Terms       | CONSENTS",
        "input    | checkbox | gdprConsent                  | -                          | CONSENTS",
        "input    | checkbox | -                            | Email me about new jobs and I agree | CONSENTS",
        "input    | checkbox | -                            | I disagree                 | OTHER",
        // questions
        "textarea | -        | q1                           | -                          | QUESTIONS",
        "textarea | -        | -                            | Why do you want to apply?  | QUESTIONS",
        "select   | -        | -                            | Years of experience        | QUESTIONS",
        // actions only for real buttons
        "button   | -        | -                            | Continue                   | ACTIONS",
        "input    | submit   | -                            | -                          | ACTIONS",
        "input    | text     | applyDate                    | Available to start         | OTHER",
        // nothing known
        "input    | text     | -                            | -                          | OTHER",
    })
    void classify(String tag, String type, String key, String label, Section expected) {
        assertThat(FieldClassifier.classify(tag, type, key, label)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
        "facade                  | facade",
        "accede                  | accede",
        "FirstName               | First Name",
        "notice_period           | notice period",
        "Question_3fa9c2e1b      | Question",
        "Answer_123456           | Answer",
        "Answer_2                | Answer 2",
        "Q_0f8fad5b-d9cb-469f-a165-70867728950e | Q",
        "``                      | ``",
    })
    void humanizeKeepsRealWordsAndDropsHashes(String input, String expected) {
        assertThat(FieldClassifier.humanize(input)).isEqualTo(expected);
    }

    @Test
    void niceNamePrefersLabelThenKeyThenButtonText() {
        assertThat(FieldClassifier.niceName("input", "x", "  First \n name ", "")).isEqualTo("First name");
        assertThat(FieldClassifier.niceName("input",
                "ctl00$Main$SingleLineTextBoxFacadeColourTextBoxControl", "", ""))
                .isEqualTo("Facade Colour");
        assertThat(FieldClassifier.niceName("button", "", "", " Submit\napplication "))
                .isEqualTo("Submit application");
        assertThat(FieldClassifier.niceName("input", null, null, null)).isEqualTo("(unnamed field)");
    }

    @Test
    void wordsSplitCaseDigitsPunctuationAndAccents() {
        assertThat(FieldClassifier.words("ctl00$Main$txtFirstName")).containsExactly("ctl", "00", "main", "txt", "first", "name");
        assertThat(FieldClassifier.words("CVUpload e-mail")).containsExactly("cv", "upload", "e", "mail");
        assertThat(FieldClassifier.words("Résumé")).containsExactly("resume");
        assertThat(FieldClassifier.words(null)).isEmpty();
    }

    @Test
    void matchesWholeWordsAndPlurals() {
        assertThat(FieldClassifier.matches(FieldClassifier.words("Terms and conditions"), "conditions")).isTrue();
        assertThat(FieldClassifier.matches(FieldClassifier.words("Supporting documents"), "document")).isTrue();
        assertThat(FieldClassifier.matches(FieldClassifier.words("hotel"), "tel")).isFalse();
        assertThat(FieldClassifier.matches(FieldClassifier.words("digit"), "git")).isFalse();
        assertThat(FieldClassifier.matches(FieldClassifier.words("x"), "")).isFalse();
    }
}
