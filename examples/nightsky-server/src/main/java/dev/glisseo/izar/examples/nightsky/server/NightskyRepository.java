package dev.glisseo.izar.examples.nightsky.server;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * The Nightsky catalog: five real constellations, a representative star, nebula, and galaxy for
 * each family (circumpolar objects never set, seasonal ones do), and three viewing locations
 * spread across longitude so {@link NightSky} shows them a different sky at the same instant.
 * Logged observations are in-memory only and reset on restart, matching this suite's role as a
 * demo, not a persistent application.
 */
@Component
class NightskyRepository {

    private final List<Constellation> constellations;
    private final List<CelestialObject> celestialObjects;
    private final List<ViewingLocation> viewingLocations;
    private final List<Observation> observations = new CopyOnWriteArrayList<>();
    private final AtomicInteger observationSequence = new AtomicInteger();

    NightskyRepository() {
        constellations = List.of(
                new Constellation(
                        "orion",
                        "Orion",
                        "Ori",
                        ConstellationFamily.SEASONAL,
                        "The hunter, facing the charging bull Taurus, accompanied across the sky by his two dogs."),
                new Constellation(
                        "lyra",
                        "Lyra",
                        "Lyr",
                        ConstellationFamily.SEASONAL,
                        "The lyre Hermes built from a tortoise shell and gave to Orpheus, whose playing could charm stones."),
                new Constellation(
                        "ursa-major",
                        "Ursa Major",
                        "UMa",
                        ConstellationFamily.CIRCUMPOLAR,
                        "The great bear; Callisto, transformed and placed in the sky where she never dips below the horizon."),
                new Constellation(
                        "cassiopeia",
                        "Cassiopeia",
                        "Cas",
                        ConstellationFamily.CIRCUMPOLAR,
                        "The vain queen, condemned to circle the pole forever as punishment for her boasting."),
                new Constellation(
                        "andromeda",
                        "Andromeda",
                        "And",
                        ConstellationFamily.SEASONAL,
                        "The chained princess, rescued by Perseus from the sea monster Cetus."));

        celestialObjects = List.of(
                new Star("betelgeuse", "Betelgeuse", 0.5, "orion", "M1-2", 20, 2),
                new Star("rigel", "Rigel", 0.13, "orion", "B8", 20, 2),
                new Nebula("orion-nebula", "Orion Nebula (M42)", 4.0, "orion", NebulaType.EMISSION, 20, 2),
                new Star("vega", "Vega", 0.03, "lyra", "A0", 2, 8),
                new Nebula("ring-nebula", "Ring Nebula (M57)", 8.8, "lyra", NebulaType.PLANETARY, 2, 8),
                new Star("dubhe", "Dubhe", 1.8, "ursa-major", "K0", 0, 24),
                new Star("schedar", "Schedar", 2.2, "cassiopeia", "K0", 0, 24),
                new Galaxy("andromeda-galaxy", "Andromeda Galaxy (M31)", 3.4, "andromeda", GalaxyType.SPIRAL, 2_500_000, 22, 6));

        viewingLocations = List.of(
                new ViewingLocation("amsterdam", "Amsterdam backyard", new GeoCoordinates(52.37, 4.90), 2),
                new ViewingLocation(
                        "mauna-kea", "Mauna Kea Observatory", new GeoCoordinates(19.8207, -155.4681), 4207),
                new ViewingLocation("atacama", "Atacama Desert", new GeoCoordinates(-24.6282, -70.4045), 2635));
    }

    List<Constellation> constellations() {
        return constellations;
    }

    List<ViewingLocation> viewingLocations() {
        return viewingLocations;
    }

    List<CelestialObject> allObjects() {
        return celestialObjects;
    }

    Constellation constellationById(String id) {
        return constellations.stream()
                .filter(c -> c.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown constellation: " + id));
    }

    List<CelestialObject> objectsInConstellation(String constellationId) {
        return celestialObjects.stream().filter(o -> o.constellationId().equals(constellationId)).toList();
    }

    ViewingLocation locationById(String id) {
        return viewingLocations.stream()
                .filter(l -> l.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown viewing location: " + id));
    }

    CelestialObject objectById(String id) {
        return celestialObjects.stream()
                .filter(o -> o.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown celestial object: " + id));
    }

    List<Observation> observationsAt(String locationId) {
        return observations.stream().filter(o -> o.locationId().equals(locationId)).toList();
    }

    Observation logObservation(LogObservationInput input) {
        // Fail fast on an unknown id rather than storing an observation that can never resolve
        // its 'object' or 'location' field.
        objectById(input.objectId());
        locationById(input.locationId());
        Observation observation = new Observation(
                "obs-" + observationSequence.incrementAndGet(),
                input.objectId(),
                input.locationId(),
                OffsetDateTime.now(),
                input.conditions(),
                input.notes());
        observations.add(observation);
        return observation;
    }
}
