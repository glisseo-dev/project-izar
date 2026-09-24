package dev.glisseo.izar.examples.nightsky.server;

record LogObservationInput(String objectId, String locationId, ViewingConditions conditions, String notes) {}
