package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Item 5: an email whose AI analysis failed must not be recorded as processed.
 * Gmail, Sheets and file storage are mocks; the AI is a function, so nothing leaves the JVM.
 */
class ApplicationManagerTest {

    private static final String JOB_ANSWER = """
            {"isJobRelated": true, "confidenceScore": 0.9, "category": "Application Confirmation",
             "companyName": "Acme Ltd", "positionTitle": "Test Analyst", "applicationStatus": "Applied"}
            """;
    private static final String NOT_JOB_ANSWER = "{\"isJobRelated\": false, \"confidenceScore\": 0.9}";

    private DataStorage dataStorage;
    private GmailService gmailService;
    private GoogleSheetsService sheetsService;
    private final List<String> promptsSeen = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataStorage = mock(DataStorage.class);
        gmailService = mock(GmailService.class);
        sheetsService = mock(GoogleSheetsService.class);
        when(dataStorage.loadProcessedEmailIds()).thenReturn(new HashSet<>());
        when(dataStorage.loadJobApplicationsDb()).thenReturn(new HashMap<>());
    }

    /** Makes Gmail report these emails as unprocessed; each body is "body of <id>". */
    private void inboxHolds(String... ids) throws Exception {
        Set<String> unprocessed = new LinkedHashSet<>(List.of(ids));
        when(gmailService.findUnprocessedEmails(anySet())).thenReturn(unprocessed);
        for (String id : ids) {
            GmailService.EmailData email = mock(GmailService.EmailData.class);
            when(email.getId()).thenReturn(id);
            when(email.getSubject()).thenReturn("Thank you for applying");
            when(email.getFrom()).thenReturn("Acme Careers <no-reply@acme.example>");
            when(email.getDate()).thenReturn("Mon, 9 Nov 2026 10:15:00 +0000");
            when(email.getBody()).thenReturn("body of " + id);
            when(gmailService.getEmailContent(id)).thenReturn(email);
        }
    }

    /** Runs the manager with an AI that answers per email; the function gets the prompt. */
    private void run(Function<String, String> ai) {
        EmailAnalysisService analysis = new EmailAnalysisService(prompt -> {
            promptsSeen.add(prompt);
            return ai.apply(prompt);
        });
        ApplicationManager manager = new ApplicationManager(dataStorage, gmailService, sheetsService, analysis);
        manager.initialize();
        manager.processJobApplicationEmails();
    }

    @Test
    void emailWhoseAiCallFailedIsNotMarkedProcessedButOthersAre() throws Exception {
        inboxHolds("ok-1", "broken", "ok-2");

        run(prompt -> {
            if (prompt.contains("body of broken")) {
                throw new IllegalStateException("429 quota exceeded");
            }
            return prompt.contains("body of ok-1") ? JOB_ANSWER : NOT_JOB_ANSWER;
        });

        verify(dataStorage).saveProcessedEmailId("ok-1");
        verify(dataStorage).saveProcessedEmailId("ok-2");
        verify(dataStorage, never()).saveProcessedEmailId("broken");

        // Only the email the AI judged job related is stored; the keyword guess for "broken" is not.
        ArgumentCaptor<JobApplicationData> stored = ArgumentCaptor.forClass(JobApplicationData.class);
        verify(dataStorage, times(1)).saveJobApplicationData(stored.capture());
        assertThat(stored.getValue().getEmailId()).isEqualTo("ok-1");
        assertThat(stored.getValue().getCompanyName()).isEqualTo("Acme Ltd");
    }

    @Test
    void emptyAiAnswerIsTreatedAsAFailedCall() throws Exception {
        inboxHolds("empty");

        run(prompt -> "   ");

        verify(dataStorage, never()).saveProcessedEmailId(anyString());
        verify(dataStorage, never()).saveJobApplicationData(any());
    }

    @Test
    void runStopsAfterRepeatedAiFailuresAndLeavesTheRestForNextTime() throws Exception {
        inboxHolds("a", "b", "c", "d", "e");

        run(prompt -> {
            throw new IllegalStateException("connection refused");
        });

        assertThat(promptsSeen).hasSize(ApplicationManager.MAX_CONSECUTIVE_AI_FAILURES);
        verify(gmailService, never()).getEmailContent("d");
        verify(dataStorage, never()).saveProcessedEmailId(anyString());
    }

    @Test
    void aSuccessBetweenFailuresResetsTheFailureCount() throws Exception {
        inboxHolds("f1", "f2", "ok", "f3", "f4", "last");

        run(prompt -> {
            if (prompt.contains("body of ok") || prompt.contains("body of last")) {
                return NOT_JOB_ANSWER;
            }
            throw new IllegalStateException("timeout");
        });

        assertThat(promptsSeen).hasSize(6);
        verify(dataStorage).saveProcessedEmailId("ok");
        verify(dataStorage).saveProcessedEmailId("last");
        verify(dataStorage, times(2)).saveProcessedEmailId(anyString());
    }
}
