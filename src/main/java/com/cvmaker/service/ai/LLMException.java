package com.cvmaker.service.ai;

/**
 * A failed LLM call that knows whether trying again can help. A rejected
 * request (HTTP 400, 401, 404 and similar) or an answer cut off by the token
 * limit fails the same way every time, so {@link AiService} does not retry it.
 */
public class LLMException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean retryable;

    public LLMException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public LLMException(String message, boolean retryable) {
        this(message, null, retryable);
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Whether a failed call is worth another attempt. The outermost
     * {@link LLMException} in the cause chain decides. Without one, an invalid
     * request ({@link IllegalArgumentException}) and an interrupt are final,
     * and anything else (network errors, timeouts) can be retried.
     */
    public static boolean isRetryable(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < 20; t = t.getCause(), depth++) {
            if (t instanceof LLMException llm) {
                return llm.isRetryable();
            }
            if (t instanceof IllegalArgumentException || t instanceof InterruptedException) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an HTTP status is worth another attempt: request timeout (408),
     * conflict (409), rate limit (429) and server errors (500 and above).
     */
    public static boolean isRetryableStatus(int httpStatus) {
        return httpStatus == 408 || httpStatus == 409 || httpStatus == 429 || httpStatus >= 500;
    }
}
