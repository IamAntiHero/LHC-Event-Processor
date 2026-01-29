package com.hussam.lhc.ingestion;

/**
 * Generic parser interface using the Strategy pattern.
 * <p>
 * Allows different parsing implementations (CSV, JSON, etc.) to be
 * used interchangeably. Implementations should be thread-safe if used
 * by multiple producer threads.
 * </p>
 *
 * @param <T> the type of object to parse
 */
public interface DataParser<T> {
    /**
     * Parses a string into an object of type T.
     */
    T parse(String line) throws ParseException;
}
