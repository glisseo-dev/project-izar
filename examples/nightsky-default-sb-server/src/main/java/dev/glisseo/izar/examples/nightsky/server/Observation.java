package dev.glisseo.izar.examples.nightsky.server;

import java.time.OffsetDateTime;

/**
 * {@code notes} is nullable, matching the schema's optional {@code Observation.notes}.
 *
 * <p>{@code observedAt} is an {@link OffsetDateTime}, not an {@link java.time.Instant}: GraphQL
 * Java Extended Scalars' {@code DateTime} serializer expects one on the way out, the same type
 * {@code integration-tests/bookstore-consumer}'s fixture server returns. A generated client still
 * decodes the wire value as {@code Instant} through {@code izar-scalars}' {@code
 * InstantScalarCodec}; the two sides only agree on the ISO-8601 string, not the Java type either
 * side happens to hold it in.
 */
record Observation(
        String id,
        String objectId,
        String locationId,
        OffsetDateTime observedAt,
        ViewingConditions conditions,
        String notes) {}
