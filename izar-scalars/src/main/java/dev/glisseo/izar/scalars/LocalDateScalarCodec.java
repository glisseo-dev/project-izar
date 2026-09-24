package dev.glisseo.izar.scalars;

import dev.glisseo.izar.operation.ScalarCodec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Maps GraphQL Java Extended Scalars' {@code Date} scalar to {@link LocalDate}, round-tripping
 * through its ISO-8601 wire representation. {@link LocalDate} is immutable, so no defensive
 * copying is needed to keep a generated response or built input safe from later mutation.
 */
public final class LocalDateScalarCodec implements ScalarCodec<LocalDate> {

    @Override
    public LocalDate decode(Object raw) {
        if (raw instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Not a valid ISO-8601 date: '" + text + "'.", e);
            }
        }
        throw new IllegalArgumentException("Expected a String for GraphQL Date, got " + ScalarValues.describe(raw));
    }

    @Override
    public Object encode(LocalDate value) {
        return value.toString();
    }
}
