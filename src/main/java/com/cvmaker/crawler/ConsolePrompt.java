package com.cvmaker.crawler;

import java.io.IOException;
import java.io.InputStream;

/**
 * Console helpers for the "press Enter to continue" prompts used by the crawlers.
 *
 * <p>The stream is read one byte at a time and is never closed or wrapped in a
 * buffering reader. A {@code Scanner} or {@code BufferedReader} around
 * {@code System.in} would either close it or read ahead, and both break the
 * next prompt that reads from the same stream.
 */
public final class ConsolePrompt {

    private ConsolePrompt() {
    }

    /**
     * Block until the user presses Enter, consuming the whole line (including
     * the line terminator) and nothing after it.
     *
     * @param in the stream to read from, normally {@code System.in}; it is left open
     * @return {@code true} if a line terminator was read, {@code false} if the
     *         stream ended first (for example when stdin is not a terminal)
     * @throws IOException if reading fails
     */
    public static boolean waitForEnter(InputStream in) throws IOException {
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                return true;
            }
        }
        return false;
    }
}
