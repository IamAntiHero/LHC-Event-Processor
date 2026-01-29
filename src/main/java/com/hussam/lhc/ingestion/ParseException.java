package com.hussam.lhc.ingestion;

/**
 * Thrown when data cannot be parsed during ingestion.
 * <p>
 * Extends RuntimeException to avoid forcing try-catch blocks everywhere.
 * Parsing errors are logged and skipped, so the pipeline continues processing.
 * </p>
 */
public class ParseException extends RuntimeException {
    /**
     * Constructs a new ParseException with the specified message.
     *
     * @param message the detail message explaining the parsing error
     */
    public ParseException(String message) {
        super(message);
    }

    /**
     * Constructs a new ParseException with the specified message and cause.
     *
     * @param message the detail message explaining the parsing error
     * @param cause the underlying cause of the parsing error
     */
    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
