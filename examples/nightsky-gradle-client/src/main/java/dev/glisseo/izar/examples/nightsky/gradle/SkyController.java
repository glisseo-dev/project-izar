package dev.glisseo.izar.examples.nightsky.gradle;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.examples.nightsky.gradle.generated.GetObservationsQuery;
import dev.glisseo.izar.examples.nightsky.gradle.generated.GetSkyCatalogQuery;
import dev.glisseo.izar.examples.nightsky.gradle.generated.GetVisibleNowQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the three operations Gradle generated for this project as plain JSON over HTTP, so
 * another process can drive them without speaking GraphQL.
 */
@RestController
class SkyController {

    private final SynchronousGraphQlOperations operations;

    SkyController(SynchronousGraphQlOperations operations) {
        this.operations = operations;
    }

    /** A dependency-free liveness check, for Docker Compose and similar orchestrators. */
    @GetMapping("/health")
    String health() {
        return "OK";
    }

    @GetMapping("/catalog")
    List<ConstellationView> catalog() {
        return operations.execute(new GetSkyCatalogQuery()).assertNoErrors().constellations().stream()
                .map(constellation -> new ConstellationView(
                        constellation.id(),
                        constellation.name(),
                        constellation.abbreviation(),
                        constellation.members().size()))
                .toList();
    }

    @GetMapping("/visible-now")
    List<VisibleObjectView> visibleNow(@RequestParam String locationId) {
        GetVisibleNowQuery query = GetVisibleNowQuery.builder().locationId(locationId).build();
        return operations.execute(query).assertNoErrors().visibleNow().stream()
                .map(object -> new VisibleObjectView(
                        object.id(), object.name(), object.magnitude(), object.constellation().name(), kind(object)))
                .toList();
    }

    @GetMapping("/observations")
    List<ObservationView> observations(@RequestParam String locationId) {
        GetObservationsQuery query = GetObservationsQuery.builder().locationId(locationId).build();
        return operations.execute(query).assertNoErrors().observations().stream()
                .map(observation -> new ObservationView(
                        observation.id(), observation.observedAt().toString(), observation.subject().name()))
                .toList();
    }

    private static String kind(GetVisibleNowQuery.CelestialObject object) {
        return switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar ignored -> "Star";
            case GetVisibleNowQuery.CelestialObjectNebula ignored -> "Nebula";
            case GetVisibleNowQuery.CelestialObjectGalaxy ignored -> "Galaxy";
            case GetVisibleNowQuery.CelestialObjectUnrecognized ignored -> "Unrecognized";
        };
    }

    record ConstellationView(String id, String name, String abbreviation, int memberCount) {}

    record VisibleObjectView(String id, String name, double magnitude, String constellation, String kind) {}

    record ObservationView(String id, String observedAt, String subjectName) {}
}
