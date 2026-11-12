package com.cvmaker.application.management;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sheets.v4.Sheets;

/**
 * An in-memory stand-in for the Google Sheets HTTP API. A real {@link Sheets} client runs on
 * top of it, so tests see the requests exactly as they would be sent, and nothing touches the
 * network.
 */
final class FakeSheetsApi extends MockHttpTransport {

    /** One HTTP request the client made. The URL is percent-decoded. */
    record Call(String method, String url, String body) {

        boolean is(String expectedMethod, String urlPart) {
            return method.equals(expectedMethod) && url.contains(urlPart);
        }
    }

    /** Answers a request, or throws to simulate a network failure. */
    @FunctionalInterface
    interface Responder {

        MockLowLevelHttpResponse respond(Call call) throws IOException;
    }

    private final List<Call> calls = new ArrayList<>();
    private final Responder responder;

    FakeSheetsApi(Responder responder) {
        this.responder = responder;
    }

    Sheets client() {
        return new Sheets.Builder(this, GsonFactory.getDefaultInstance(), null)
                .setApplicationName("cvmaker-test")
                .build();
    }

    List<Call> calls() {
        return calls;
    }

    List<Call> calls(String method, String urlPart) {
        return calls.stream().filter(call -> call.is(method, urlPart)).toList();
    }

    static MockLowLevelHttpResponse json(String body) {
        return new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContentType("application/json; charset=UTF-8")
                .setContent(body);
    }

    static MockLowLevelHttpResponse error(int status, String reason) {
        return new MockLowLevelHttpResponse()
                .setStatusCode(status)
                .setReasonPhrase(reason)
                .setContentType("application/json; charset=UTF-8")
                .setContent("{\"error\":{\"code\":" + status + ",\"message\":\"" + reason + "\"}}");
    }

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest(url) {
            @Override
            public LowLevelHttpResponse execute() throws IOException {
                // The path is percent-encoded, not form-encoded: a literal '+' must stay a '+'.
                String decodedUrl = URLDecoder.decode(url.replace("+", "%2B"), StandardCharsets.UTF_8);
                Call call = new Call(method, decodedUrl, getContentAsString());
                calls.add(call);
                return responder.respond(call);
            }
        };
    }
}
