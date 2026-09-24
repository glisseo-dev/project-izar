package dev.glisseo.izar.examples.nightsky.server;

/**
 * Common shape for everything the {@code visibleNow} query and the schema's {@code CelestialObject}
 * interface expose. {@code riseHour}/{@code setHour} back {@link NightSky}'s visibility check; they
 * are not schema fields, so they are simply never fetched by a GraphQL selection that doesn't name
 * them.
 */
sealed interface CelestialObject permits Star, Nebula, Galaxy {

    String id();

    String name();

    double magnitude();

    String constellationId();

    /** Compressed-night-cycle hour (0-24) this object rises, inclusive. */
    double riseHour();

    /** Compressed-night-cycle hour (0-24) this object sets, exclusive. */
    double setHour();
}
