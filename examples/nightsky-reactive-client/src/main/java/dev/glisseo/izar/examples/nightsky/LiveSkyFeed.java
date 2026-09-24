package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.examples.nightsky.generated.GetViewingLocationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.WatchVisibleSkySubscription;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code WatchVisibleSky} over HTTP SSE and prints the pushed sky snapshots from the
 * Nightsky server's compressed night cycle. The bounded demo watches for just over one full cycle.
 */
@Component
class LiveSkyFeed implements CommandLineRunner {

    private static final Duration WATCH_DURATION = Duration.ofSeconds(72);

    private final ReactiveGraphQlOperations operations;

    private Map<String, String> previouslyVisible = Map.of();

    LiveSkyFeed(ReactiveGraphQlOperations operations) {
        this.operations = operations;
    }

    @Override
    public void run(String... args) {
        GetViewingLocationsQuery.ViewingLocation location = operations
                .execute(new GetViewingLocationsQuery())
                .map(result -> result.assertNoErrors().viewingLocations().getFirst())
                .block();

        System.out.printf(
                Locale.ROOT,
                "Watching the sky rotate from %s through a subscription. One compressed night "
                        + "takes about 60 seconds; this feed runs for %d seconds.%n%n",
                location.name(),
                WATCH_DURATION.toSeconds());

        WatchVisibleSkySubscription subscription =
                WatchVisibleSkySubscription.builder().locationId(location.id()).build();
        operations.executeSubscription(subscription)
                .map(result -> result.assertNoErrors().visibleSky())
                .doOnNext(this::printTick)
                .take(WATCH_DURATION)
                .blockLast();
    }

    private void printTick(List<WatchVisibleSkySubscription.CelestialObject> visible) {
        Map<String, String> namesById = new LinkedHashMap<>();
        for (WatchVisibleSkySubscription.CelestialObject object : visible) {
            namesById.put(idOf(object), nameOf(object));
        }

        Set<String> rose = new HashSet<>(namesById.keySet());
        rose.removeAll(previouslyVisible.keySet());
        Set<String> set = new HashSet<>(previouslyVisible.keySet());
        set.removeAll(namesById.keySet());

        rose.forEach(id -> System.out.println("  rises: " + namesById.get(id)));
        set.forEach(id -> System.out.println("  sets: " + previouslyVisible.get(id)));
        System.out.println("  visible now: " + String.join(", ", namesById.values()));

        previouslyVisible = Map.copyOf(namesById);
    }

    private static String idOf(WatchVisibleSkySubscription.CelestialObject object) {
        return switch (object) {
            case WatchVisibleSkySubscription.CelestialObjectStar star -> star.id();
            case WatchVisibleSkySubscription.CelestialObjectNebula nebula -> nebula.id();
            case WatchVisibleSkySubscription.CelestialObjectGalaxy galaxy -> galaxy.id();
            case WatchVisibleSkySubscription.CelestialObjectUnrecognized unrecognized -> unrecognized.id();
        };
    }

    private static String nameOf(WatchVisibleSkySubscription.CelestialObject object) {
        return switch (object) {
            case WatchVisibleSkySubscription.CelestialObjectStar star -> star.name();
            case WatchVisibleSkySubscription.CelestialObjectNebula nebula -> nebula.name();
            case WatchVisibleSkySubscription.CelestialObjectGalaxy galaxy -> galaxy.name();
            case WatchVisibleSkySubscription.CelestialObjectUnrecognized unrecognized -> unrecognized.name();
        };
    }
}
