package dev.glisseo.izar.examples.nightsky.server;

/**
 * {@code members} is not a stored field: a {@link Constellation} and its {@link CelestialObject}s
 * only reference each other by id, resolved on demand in {@link NightskyGraphQlController}. Record
 * {@code equals}/{@code hashCode} recurse into every component, so a direct, bidirectional object
 * reference between the two would recurse forever.
 */
record Constellation(String id, String name, String abbreviation, ConstellationFamily family, String mythology) {}
