package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.operation.ScalarCodec;

/**
 * Maps the schema's custom {@code Coordinates} scalar to {@link GeoCoordinates}, round-tripping
 * through the {@code "latitude,longitude"} wire representation the Nightsky server's own {@code
 * Coercing} implementation writes. No shipped {@code izar-scalars} codec fits this scalar, so this
 * is an ordinary application-supplied {@link ScalarCodec}, the same way an application would write
 * one for any domain-specific scalar Izar doesn't ship a codec for.
 */
public final class GeoCoordinatesScalarCodec implements ScalarCodec<GeoCoordinates> {

    @Override
    public GeoCoordinates decode(Object raw) {
        if (raw instanceof String text) {
            String[] parts = text.split(",", 2);
            if (parts.length == 2) {
                try {
                    return new GeoCoordinates(
                            Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Not a valid 'latitude,longitude' pair: '" + text + "'.", e);
                }
            }
        }
        throw new IllegalArgumentException(
                "Expected a String for GraphQL Coordinates, got " + (raw == null ? "null" : raw.getClass().getName()));
    }

    @Override
    public Object encode(GeoCoordinates value) {
        return value.latitude() + "," + value.longitude();
    }
}
