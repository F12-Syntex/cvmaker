package com.cvmaker.service.ai;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Console logging for the HTTP clients (local LLM, Exa search). Request and
 * response bodies carry CV text, email content and search queries, so by
 * default only the URL, the status and the body length are printed. Full
 * bodies are printed only in debug mode, which is switched on with the system
 * property {@code -Dcvmaker.debug.http=true} or the environment variable
 * {@code CVMAKER_DEBUG_HTTP=true}.
 */
public final class ApiLog {

    public static final String DEBUG_PROPERTY = "cvmaker.debug.http";
    public static final String DEBUG_ENV = "CVMAKER_DEBUG_HTTP";

    /**
     * How much of an error body goes into an exception message outside debug
     * mode. Enough for the API's error text, too little for a whole CV.
     */
    static final int MAX_ERROR_BODY_CHARS = 300;

    private final Consumer<String> sink;
    private final boolean debug;

    /**
     * @param sink receives each log line
     * @param debug true to include full bodies
     */
    public ApiLog(Consumer<String> sink, boolean debug) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.debug = debug;
    }

    /**
     * Logs to standard output, with debug mode taken from the system property
     * or the environment variable.
     */
    public static ApiLog standard() {
        return new ApiLog(System.out::println, debugEnabled());
    }

    static boolean debugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY)
                || "true".equalsIgnoreCase(System.getenv(DEBUG_ENV));
    }

    public boolean isDebug() {
        return debug;
    }

    /**
     * Logs a line that holds no request or response content.
     */
    public void info(String message) {
        sink.accept(message);
    }

    /**
     * Logs an outgoing request: label, URL and body length, plus the body in
     * debug mode.
     */
    public void request(String label, String url, String body) {
        sink.accept(label + " request to " + url + " (" + length(body) + " chars)");
        if (debug) {
            sink.accept(label + " request body: " + body);
        }
    }

    /**
     * Logs a response: label, status and body length, plus the body in debug
     * mode.
     */
    public void response(String label, int status, String body) {
        sink.accept(label + " response: HTTP " + status + " (" + length(body) + " chars)");
        if (debug) {
            sink.accept(label + " response body: " + body);
        }
    }

    /**
     * The part of an error body that may go into an exception message: all of
     * it in debug mode, otherwise the first 300 characters.
     */
    public String errorBody(String body) {
        if (body == null) {
            return "";
        }
        if (debug || body.length() <= MAX_ERROR_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY_CHARS)
                + "... (" + (body.length() - MAX_ERROR_BODY_CHARS) + " more chars, set "
                + DEBUG_PROPERTY + "=true to see them)";
    }

    private static int length(String body) {
        return body == null ? 0 : body.length();
    }
}
