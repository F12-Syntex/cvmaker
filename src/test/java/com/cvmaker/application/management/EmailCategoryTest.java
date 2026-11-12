package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailCategoryTest {

    @ParameterizedTest
    @EnumSource(EmailCategory.class)
    void displayNameAndEnumNameBothResolve(EmailCategory category) {
        assertThat(EmailCategory.fromLabel(category.getDisplayName())).contains(category);
        assertThat(EmailCategory.fromLabel(category.name())).contains(category);
    }

    @ParameterizedTest
    @CsvSource({
        "interview invitation,        INTERVIEW_INVITATION",
        "'  Interview Invitation  ',  INTERVIEW_INVITATION",
        "INTERVIEW INVITATION,        INTERVIEW_INVITATION",
        "interview_invitation,        INTERVIEW_INVITATION",
        "Interview Follow-Up,         INTERVIEW_FOLLOWUP",
        "Interview Followup,          INTERVIEW_FOLLOWUP",
        "job offer,                   OFFER",
        "offer,                       OFFER",
        "Assessment / Test Request,   ASSESSMENT_REQUEST",
        "'\"Rejection\"',             REJECTION",
        "Rejection.,                  REJECTION"
    })
    void caseSpacingAndPunctuationAreIgnored(String label, EmailCategory expected) {
        assertThat(EmailCategory.fromLabel(label)).contains(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "null", "Newsletter", "Interview", "Offer Accepted Maybe"})
    void nullBlankAndUnknownLabelsGiveEmpty(String label) {
        assertThat(EmailCategory.fromLabel(label)).isEmpty();
    }

    @Test
    void everyNameInThePromptListResolves() {
        // getAllCategories() is what the prompt shows the model, so each entry must round-trip.
        for (String shown : EmailCategory.getAllCategories().split(", ")) {
            assertThat(EmailCategory.fromLabel(shown)).as(shown).isPresent();
        }
    }
}
