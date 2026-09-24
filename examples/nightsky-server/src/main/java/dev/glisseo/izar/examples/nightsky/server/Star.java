package dev.glisseo.izar.examples.nightsky.server;

record Star(
        String id,
        String name,
        double magnitude,
        String constellationId,
        String spectralType,
        double riseHour,
        double setHour)
        implements CelestialObject {}
