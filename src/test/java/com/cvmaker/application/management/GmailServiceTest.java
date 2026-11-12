package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;

class GmailServiceTest {

    // ------------------------------------------------------------- helpers

    private static MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }

    private static MessagePart leaf(String mimeType, String text, Charset charset, MessagePartHeader... headers) {
        return new MessagePart()
                .setMimeType(mimeType)
                .setHeaders(headers.length == 0 ? null : Arrays.asList(headers))
                .setBody(new MessagePartBody().encodeData(text.getBytes(charset)));
    }

    private static MessagePart text(String mimeType, String text) {
        return leaf(mimeType, text, StandardCharsets.UTF_8, header("Content-Type", mimeType + "; charset=UTF-8"));
    }

    private static MessagePart multipart(String mimeType, MessagePart... parts) {
        return new MessagePart().setMimeType(mimeType).setParts(Arrays.asList(parts));
    }

    // ------------------------------------------------------------- body decoding

    @Test
    void alternativePrefersPlainTextAndDoesNotDuplicate() {
        MessagePart payload = multipart("multipart/alternative",
                text("text/plain", "Hello plain"),
                text("text/html", "<p>Hello html</p>"));

        assertThat(GmailService.extractBody(payload)).isEqualTo("Hello plain");
    }

    @Test
    void usesHtmlWhenThereIsNoPlainText() {
        assertThat(GmailService.extractBody(multipart("multipart/alternative", text("text/html", "<b>Hi</b>"))))
                .isEqualTo("<b>Hi</b>");
    }

    @Test
    void nestedMixedMessageSkipsAttachments() {
        MessagePart attachment = text("text/plain", "attachment text");
        attachment.setFilename("cv.txt");
        MessagePart dispositionAttachment = leaf("text/plain", "also attached", StandardCharsets.UTF_8,
                header("Content-Disposition", "attachment; filename=\"x.txt\""));
        MessagePart payload = multipart("multipart/mixed",
                multipart("multipart/alternative", text("text/plain", "Body"), text("text/html", "<p>Body</p>")),
                attachment,
                dispositionAttachment,
                text("text/plain", "Footer"));

        assertThat(GmailService.extractBody(payload)).isEqualTo("Body\nFooter");
    }

    @Test
    void decodesUtf8RegardlessOfPlatformCharset() {
        assertThat(GmailService.extractBody(text("text/plain", "Grüße – €"))).isEqualTo("Grüße – €");
    }

    @Test
    void honoursDeclaredCharset() {
        MessagePart latin1 = leaf("text/plain", "Café", StandardCharsets.ISO_8859_1,
                header("Content-Type", "text/plain; charset=\"ISO-8859-1\""));

        assertThat(GmailService.extractBody(latin1)).isEqualTo("Café");
    }

    @Test
    void unknownCharsetFallsBackToUtf8() {
        MessagePart part = leaf("text/plain", "Grüße", StandardCharsets.UTF_8,
                header("content-type", "text/plain; charset=x-no-such-charset"));

        assertThat(GmailService.charsetOf(part)).isEqualTo(StandardCharsets.UTF_8);
        assertThat(GmailService.extractBody(part)).isEqualTo("Grüße");
    }

    @Test
    void nullMimeTypesHeadersAndBodiesDoNotThrow() {
        MessagePart noMime = new MessagePart().setBody(new MessagePartBody().encodeData("bare".getBytes(StandardCharsets.UTF_8)));
        MessagePart noBody = new MessagePart().setMimeType("text/plain");
        MessagePart emptyBody = new MessagePart().setMimeType("text/plain").setBody(new MessagePartBody());
        List<MessagePart> parts = new ArrayList<>(Arrays.asList(noMime, noBody, emptyBody, null));
        MessagePart payload = new MessagePart().setParts(parts);

        assertThat(GmailService.extractBody(payload)).isEqualTo("bare");
        assertThat(GmailService.extractBody(null)).isEmpty();
        assertThat(GmailService.extractBody(new MessagePart())).isEmpty();
    }

    @Test
    void ignoresNonTextLeaves() {
        MessagePart payload = multipart("multipart/mixed",
                text("text/plain", "Body"),
                leaf("text/calendar", "BEGIN:VCALENDAR", StandardCharsets.UTF_8),
                leaf("image/png", "PNG", StandardCharsets.UTF_8));

        assertThat(GmailService.extractBody(payload)).isEqualTo("Body");
    }

    // ------------------------------------------------------------- headers and message conversion

    @Test
    void headerValueIsCaseInsensitiveAndNullSafe() {
        List<MessagePartHeader> headers = Arrays.asList(null, header("subject", "Hi"), header("From", null));

        assertThat(GmailService.headerValue(headers, "Subject")).isEqualTo("Hi");
        assertThat(GmailService.headerValue(headers, "From")).isEmpty();
        assertThat(GmailService.headerValue(headers, "Date")).isEmpty();
        assertThat(GmailService.headerValue(null, "Date")).isEmpty();
    }

    @Test
    void toEmailDataReadsHeadersAndBody() {
        MessagePart payload = multipart("multipart/alternative", text("text/plain", "Body"), text("text/html", "<p>Body</p>"));
        payload.setHeaders(List.of(header("Subject", "Interview"), header("From", "hr@acme.example"),
                header("Date", "Tue, 3 Jun 2025 11:05:30 +0200")));

        GmailService.EmailData data = GmailService.toEmailData("m1", new Message().setId("m1").setPayload(payload));

        assertThat(data.getId()).isEqualTo("m1");
        assertThat(data.getSubject()).isEqualTo("Interview");
        assertThat(data.getFrom()).isEqualTo("hr@acme.example");
        assertThat(data.getDate()).isEqualTo("Tue, 3 Jun 2025 11:05:30 +0200");
        assertThat(data.getBody()).isEqualTo("Body");
    }

    @Test
    void toEmailDataSurvivesMissingPayloadAndUsesSnippetAndInternalDate() {
        Message message = new Message().setId("m2").setSnippet("short preview").setInternalDate(1748948730000L);

        GmailService.EmailData data = GmailService.toEmailData("m2", message);

        assertThat(data.getSubject()).isEmpty();
        assertThat(data.getBody()).isEqualTo("short preview");
        assertThat(data.getDate()).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
    }

    // ------------------------------------------------------------- dates

    @Test
    void effectiveDateKeepsParseableHeader() {
        String header = "Tue, 3 Jun 2025 11:05:30 +0200";

        assertThat(GmailService.effectiveDate(header, 0L)).isEqualTo(header);
    }

    @Test
    void effectiveDateFallsBackToInternalDate() {
        long internal = 1748948730000L; // 2025-06-03T11:05:30Z

        assertThat(GmailService.effectiveDate("", internal)).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
        assertThat(GmailService.effectiveDate("garbage", internal)).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
        assertThat(GmailService.effectiveDate(null, internal)).isEqualTo("Tue, 3 Jun 2025 11:05:30 GMT");
    }

    @Test
    void effectiveDateWithoutInternalDateKeepsHeader() {
        assertThat(GmailService.effectiveDate("garbage", null)).isEqualTo("garbage");
    }

    @Test
    void buildDateRangeFormatsAndValidates() {
        assertThat(GmailService.buildDateRange(null, null)).isEmpty();
        assertThat(GmailService.buildDateRange(" ", "")).isEmpty();
        assertThat(GmailService.buildDateRange("2025/07/07", null)).isEqualTo("after:2025/07/07");
        assertThat(GmailService.buildDateRange("2025-07-07", "2025/7/15"))
                .isEqualTo("after:2025/07/07 before:2025/7/15");
        assertThatThrownBy(() -> GmailService.buildDateRange("last week", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ApplicationConfig.GMAIL_SEARCH_AFTER);
    }

    // ------------------------------------------------------------- pagination

    private static ListMessagesResponse page(String nextToken, String... ids) {
        List<Message> messages = new ArrayList<>();
        for (String id : ids) {
            messages.add(new Message().setId(id));
        }
        return new ListMessagesResponse().setMessages(ids.length == 0 ? null : messages).setNextPageToken(nextToken);
    }

    @Test
    void listAllMessagesFollowsNextPageToken() throws Exception {
        Map<String, ListMessagesResponse> pages = Map.of(
                "first", page("t2", "a", "b"),
                "t2", page("t3"),
                "t3", page(null, "c"));
        List<String> requested = new ArrayList<>();

        List<Message> all = GmailService.listAllMessages(token -> {
            requested.add(String.valueOf(token));
            return pages.get(token == null ? "first" : token);
        }, 10);

        assertThat(all).extracting(Message::getId).containsExactly("a", "b", "c");
        assertThat(requested).containsExactly("null", "t2", "t3");
    }

    @Test
    void listAllMessagesStopsAtPageLimit() throws Exception {
        int[] calls = {0};

        List<Message> all = GmailService.listAllMessages(token -> {
            calls[0]++;
            return page("more", "id" + calls[0]);
        }, 3);

        assertThat(calls[0]).isEqualTo(3);
        assertThat(all).hasSize(3);
    }

    @Test
    void listAllMessagesHandlesNullResponse() throws Exception {
        assertThat(GmailService.listAllMessages(token -> null, 5)).isEmpty();
    }
}
