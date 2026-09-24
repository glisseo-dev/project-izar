package dev.glisseo.izar.examples.nightsky.server;

record Galaxy(
        String id,
        String name,
        double magnitude,
        String constellationId,
        GalaxyType galaxyType,
        double distanceLightYears,
        double riseHour,
        double setHour)
        implements CelestialObject {}
