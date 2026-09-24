package dev.glisseo.izar.examples.nightsky.server;

record Nebula(
        String id,
        String name,
        double magnitude,
        String constellationId,
        NebulaType nebulaType,
        double riseHour,
        double setHour)
        implements CelestialObject {}
