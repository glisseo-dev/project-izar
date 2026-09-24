package dev.glisseo.izar.scalars;

import dev.glisseo.izar.operation.ScalarCodec;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Maps GraphQL Java Extended Scalars' {@code DateTime} scalar to {@link Instant}, round-tripping
 * through its ISO-8601 wire representation. {@link Instant} is immutable, so no defensive copying
 * is needed to keep a generated response or built input safe from later mutation.
 */
public final class InstantScalarCodec implements ScalarCodec<Instant> {

    @Override
    public Instant decode(Object raw) {
        if (raw instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Not a valid ISO-8601 instant: '" + text + "'.", e);
            }
        }
        throw new IllegalArgumentException("Expected a String for GraphQL DateTime, got " + ScalarValues.describe(raw));
    }

    @Override
    public Object encode(Instant value) {
        return value.toString();
    }
}
