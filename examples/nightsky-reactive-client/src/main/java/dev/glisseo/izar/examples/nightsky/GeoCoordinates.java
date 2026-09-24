package dev.glisseo.izar.examples.nightsky;

/**
 * A point on Earth's surface, decoded from the schema's custom {@code Coordinates} scalar.
 * Duplicated in each Nightsky module rather than shared: the server, this client, and the
 * reactive client are independent Maven modules with no shared source, resolving Izar the same
 * way any separate consumer project would.
 */
public record GeoCoordinates(double latitude, double longitude) {}
