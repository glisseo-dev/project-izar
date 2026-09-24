package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.examples.nightsky.generated.GetObservationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetSkyCatalogQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetViewingLocationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetVisibleNowQuery;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation.LogObservationInput;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation.ViewingConditions2;
import dev.glisseo.izar.examples.nightsky.generated.WatchVisibleSkySubscription;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Runs the Nightsky story against a live server: browse the catalog, check what's visible, log an
 * observation, confirm it landed, then receive three persisted sky updates. Catalog and observation
 * operations use {@link SynchronousGraphQlOperations}; the subscription uses
 * {@link ReactiveGraphQlOperations} because it streams events.
 */
@Component
class SkyExplorerRunner implements CommandLineRunner {

    private final SynchronousGraphQlOperations operations;
    private final ReactiveGraphQlOperations subscriptionOperations;

    SkyExplorerRunner(
            SynchronousGraphQlOperations operations, ReactiveGraphQlOperations subscriptionOperations) {
        this.operations = operations;
        this.subscriptionOperations = subscriptionOperations;
    }

    @Override
    public void run(String... args) {
        printCatalog();

        String locationId = "amsterdam";
        List<GetVisibleNowQuery.CelestialObject> visible = printVisibleNow(locationId);

        String observedObjectId = visible.isEmpty() ? "vega" : describeId(visible.getFirst());
        logObservation(locationId, observedObjectId);
        printObservationLog(locationId);
        watchVisibleSky(locationId);
    }

    private void printCatalog() {
        GetSkyCatalogQuery.Data data =
                operations.execute(new GetSkyCatalogQuery()).assertNoErrors();
        System.out.println("=== Sky catalog ===");
        for (GetSkyCatalogQuery.Constellation constellation : data.constellations()) {
            System.out.printf(
                    Locale.ROOT,
                    "%s (%s, %s)%n",
                    constellation.name(),
                    constellation.abbreviation(),
                    constellation.family());
            System.out.printf(Locale.ROOT, "  %s%n", constellation.mythology());
            for (GetSkyCatalogQuery.CelestialObject member : constellation.members()) {
                System.out.printf(Locale.ROOT, "  - %s (magnitude %.2f)%n", member.name(), member.magnitude());
            }
        }
        System.out.println();
    }

    private List<GetVisibleNowQuery.CelestialObject> printVisibleNow(String locationId) {
        GetViewingLocationsQuery.Data locations =
                operations.execute(new GetViewingLocationsQuery()).assertNoErrors();
        GetViewingLocationsQuery.ViewingLocation location = locations.viewingLocations().stream()
                .filter(l -> l.id().equals(locationId))
                .findFirst()
                .orElseThrow();

        GetVisibleNowQuery query = GetVisibleNowQuery.builder().locationId(locationId).build();
        GetVisibleNowQuery.Data data = operations.execute(query).assertNoErrors();

        System.out.printf(Locale.ROOT, "=== Visible now from %s ===%n", location.name());
        for (GetVisibleNowQuery.CelestialObject object : data.visibleNow()) {
            System.out.println("  " + describe(object));
        }
        System.out.println();
        return data.visibleNow();
    }

    private void logObservation(String locationId, String objectId) {
        LogObservationInput input = LogObservationInput.builder()
                .objectId(objectId)
                .locationId(locationId)
                .conditions(ViewingConditions2.GOOD)
                .notes("Logged by the nightsky-sync-client example.")
                .build();
        LogObservationMutation.Data data =
                operations.execute(LogObservationMutation.builder().input(input).build()).assertNoErrors();
        LogObservationMutation.LogObservation observation = data.logObservation();
        System.out.printf(
                Locale.ROOT,
                "=== Logged observation %s ===%n  %s at %s, conditions %s%n%n",
                observation.id(),
                observation.subject().name(),
                observation.observedAt(),
                observation.conditions());
    }

    private void printObservationLog(String locationId) {
        GetObservationsQuery query = GetObservationsQuery.builder().locationId(locationId).build();
        GetObservationsQuery.Data data = operations.execute(query).assertNoErrors();

        System.out.println("=== Observation log for this location ===");
        for (GetObservationsQuery.Observation observation : data.observations()) {
            System.out.printf(
                    Locale.ROOT,
                    "  %s: %s, %s (%s)%n",
                    observation.observedAt(),
                    observation.subject().name(),
                    observation.conditions(),
                    observation.notes());
        }
    }

    private void watchVisibleSky(String locationId) {
        System.out.println("=== Three persisted sky updates ===");
        WatchVisibleSkySubscription subscription =
                WatchVisibleSkySubscription.builder().locationId(locationId).build();
        Flux<WatchVisibleSkySubscription.Data> updates = subscriptionOperations
                .executeSubscription(subscription)
                .map(result -> result.assertNoErrors());
        updates.take(3)
                .map(WatchVisibleSkySubscription.Data::visibleSky)
                .doOnNext(objects -> System.out.println("  " + objects.stream()
                        .map(SkyExplorerRunner::subscriptionObjectName)
                        .collect(Collectors.joining(", "))))
                .blockLast();
    }

    private static String subscriptionObjectName(WatchVisibleSkySubscription.CelestialObject object) {
        return switch (object) {
            case WatchVisibleSkySubscription.CelestialObjectStar star -> star.name();
            case WatchVisibleSkySubscription.CelestialObjectNebula nebula -> nebula.name();
            case WatchVisibleSkySubscription.CelestialObjectGalaxy galaxy -> galaxy.name();
            case WatchVisibleSkySubscription.CelestialObjectUnrecognized unrecognized -> unrecognized.name();
        };
    }

    private static String describeId(GetVisibleNowQuery.CelestialObject object) {
        return switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar star -> star.id();
            case GetVisibleNowQuery.CelestialObjectNebula nebula -> nebula.id();
            case GetVisibleNowQuery.CelestialObjectGalaxy galaxy -> galaxy.id();
            case GetVisibleNowQuery.CelestialObjectUnrecognized unrecognized -> unrecognized.id();
        };
    }

    private static String describe(GetVisibleNowQuery.CelestialObject object) {
        return switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar star -> String.format(
                    Locale.ROOT,
                    "%s (%s) - star, magnitude %.2f, spectral type %s",
                    star.name(),
                    star.constellation().name(),
                    star.magnitude(),
                    star.spectralType());
            case GetVisibleNowQuery.CelestialObjectNebula nebula -> String.format(
                    Locale.ROOT,
                    "%s (%s) - %s nebula, magnitude %.2f",
                    nebula.name(),
                    nebula.constellation().name(),
                    nebula.nebulaType(),
                    nebula.magnitude());
            case GetVisibleNowQuery.CelestialObjectGalaxy galaxy -> String.format(
                    Locale.ROOT,
                    "%s (%s) - %s galaxy, magnitude %.2f, %.1f light years away",
                    galaxy.name(),
                    galaxy.constellation().name(),
                    galaxy.galaxyType(),
                    galaxy.magnitude(),
                    galaxy.distanceLightYears());
            case GetVisibleNowQuery.CelestialObjectUnrecognized unrecognized -> String.format(
                    Locale.ROOT,
                    "%s (%s) - unrecognized type '%s'",
                    unrecognized.name(),
                    unrecognized.constellation().name(),
                    unrecognized.__typename());
        };
    }
}
