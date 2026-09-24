package dev.glisseo.izar.examples.nightsky.server;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

/**
 * Coerces the schema's custom {@code Coordinates} scalar to and from a {@code "latitude,longitude"}
 * string, the same wire format the example clients' {@code GeoCoordinatesScalarCodec} decodes.
 * Unlike {@code DateTime}, no published GraphQL Java Extended Scalars type fits this domain, so
 * the server writes its own {@link Coercing}, the same way an application would for any scalar
 * without a shipped implementation.
 */
final class GeoCoordinatesCoercing implements Coercing<GeoCoordinates, String> {

    @Override
    public String serialize(Object dataFetcherResult) {
        if (dataFetcherResult instanceof GeoCoordinates coordinates) {
            return coordinates.latitude() + "," + coordinates.longitude();
        }
        throw new CoercingSerializeException(
                "Expected a GeoCoordinates, got " + dataFetcherResult.getClass().getName());
    }

    @Override
    public GeoCoordinates parseValue(Object input) {
        return parse(input);
    }

    @Override
    public GeoCoordinates parseLiteral(Object input) {
        return parse(input);
    }

    private static GeoCoordinates parse(Object input) {
        if (input instanceof String text) {
            String[] parts = text.split(",", 2);
            if (parts.length == 2) {
                try {
                    return new GeoCoordinates(
                            Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException e) {
                    throw new CoercingParseValueException("Not a valid 'latitude,longitude' pair: '" + text + "'.", e);
                }
            }
        }
        throw new CoercingParseValueException("Expected a 'latitude,longitude' string, got " + input);
    }
}
