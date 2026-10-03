package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.examples.nightsky.generated.GetObservationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetSkyCatalogQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetViewingLocationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetVisibleNowQuery;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation.ViewingConditions2;
import dev.glisseo.izar.examples.nightsky.generated.WatchVisibleSkySubscription;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Runs the Nightsky story against a live server on startup: browse the catalog, check what's
 * visible, log an observation, confirm it landed, then receive three persisted sky updates. Every
 * call goes through {@link NightskyGraphQlFacade}, the same operations {@link SkyExplorerController}
 * serves over REST. This runner keeps printing its story to stdout as before; a {@code
 * spring-boot-starter-web} dependency now also keeps the process alive afterward to serve that
 * controller, instead of exiting once the story finishes.
 */
@Component
class SkyExplorerRunner implements CommandLineRunner {

    private final NightskyGraphQlFacade facade;

    SkyExplorerRunner(NightskyGraphQlFacade facade) {
        this.facade = facade;
    }

    @Override
    public void run(String... args) {
        printCatalog();

        String locationId = "amsterdam";
        List<GetVisibleNowQuery.CelestialObject> visible = printVisibleNow(locationId);

        String observedObjectId =
                visible.isEmpty() ? "vega" : visible.getFirst().id();
        logObservation(locationId, observedObjectId);
        printObservationLog(locationId);
        watchVisibleSky(locationId);
    }

    private void printCatalog() {
        GetSkyCatalogQuery.Data data = facade.catalog();
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
        GetViewingLocationsQuery.ViewingLocation location = facade.viewingLocation(locationId);
        GetVisibleNowQuery.Data data = facade.visibleNow(locationId);

        System.out.printf(Locale.ROOT, "=== Visible now from %s ===%n", location.name());
        for (GetVisibleNowQuery.CelestialObject object : data.visibleNow()) {
            System.out.println("  " + VisibleObjectPresentation.describe(object));
        }
        System.out.println();
        return data.visibleNow();
    }

    private void logObservation(String locationId, String objectId) {
        LogObservationMutation.LogObservation observation = facade.logObservation(
                locationId,
                objectId,
                ViewingConditions2.GOOD,
                "Logged by the nightsky-sync-client example.");
        System.out.printf(
                Locale.ROOT,
                "=== Logged observation %s ===%n  %s at %s, conditions %s%n%n",
                observation.id(),
                observation.subject().name(),
                observation.observedAt(),
                observation.conditions());
    }

    private void printObservationLog(String locationId) {
        GetObservationsQuery.Data data = facade.observations(locationId);

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
        Flux<WatchVisibleSkySubscription.Data> updates = facade.watchVisibleSky(locationId);
        updates.take(3)
                .map(WatchVisibleSkySubscription.Data::visibleSky)
                .doOnNext(objects -> System.out.println("  " + objects.stream()
                        .map(WatchVisibleSkySubscription.CelestialObject::name)
                        .collect(Collectors.joining(", "))))
                .blockLast();
    }
}
