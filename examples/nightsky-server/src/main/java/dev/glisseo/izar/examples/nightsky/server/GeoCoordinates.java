package dev.glisseo.izar.examples.nightsky.server;

/**
 * A point on Earth's surface, the server-side value {@code Coordinates} coerces to and from the
 * wire. Longitude drives {@link NightSky}'s local-hour offset: two locations at different
 * longitudes see different objects visible at the same instant, the way real longitude
 * determines local time of night.
 */
record GeoCoordinates(double latitude, double longitude) {}
