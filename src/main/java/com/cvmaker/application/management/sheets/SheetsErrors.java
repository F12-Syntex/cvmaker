package com.cvmaker.application.management.sheets;

import java.io.IOException;

import com.google.api.client.http.HttpResponseException;

/** Classifies errors from the Sheets API. */
public final class SheetsErrors {

    private SheetsErrors() {
    }

    /**
     * Tells whether an error means "this spreadsheet does not exist" (HTTP 404). Every other
     * error (403 no access, 401 bad credentials, 429 or 5xx, network failure) says nothing
     * about whether the spreadsheet exists, so it must not lead to creating a new one.
     *
     * @param error the error thrown by an API call
     * @return true only for an HTTP 404 response
     */
    public static boolean isNotFound(IOException error) {
        return error instanceof HttpResponseException response && response.getStatusCode() == 404;
    }

    /**
     * A short description for messages: the HTTP status when there is one, else the message.
     *
     * @param error the error thrown by an API call
     * @return for example {@code HTTP 403 Forbidden} or {@code Connection reset}
     */
    public static String describe(IOException error) {
        if (error instanceof HttpResponseException response) {
            String statusMessage = response.getStatusMessage();
            return "HTTP " + response.getStatusCode() + (statusMessage == null ? "" : " " + statusMessage);
        }
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
