package dev.glisseo.izar.examples.nightsky;

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
 * Nightsky server's compressed night cycle on startup. The bounded demo watches for just over one
 * full cycle, then, with {@code spring-boot-starter-web} now on the classpath, the process stays up
 * to serve {@link LiveSkyController}'s own streaming endpoint instead of exiting.
 */
@Component
class LiveSkyFeed implements CommandLineRunner {

    private static final Duration WATCH_DURATION = Duration.ofSeconds(72);

    private final NightskyGraphQlFacade facade;

    private Map<String, String> previouslyVisible = Map.of();

    LiveSkyFeed(NightskyGraphQlFacade facade) {
        this.facade = facade;
    }

    @Override
    public void run(String... args) {
        GetViewingLocationsQuery.ViewingLocation location = facade.firstViewingLocation().block();

        System.out.printf(
                Locale.ROOT,
                "Watching the sky rotate from %s through a subscription. One compressed night "
                        + "takes about 60 seconds; this feed runs for %d seconds.%n%n",
                location.name(),
                WATCH_DURATION.toSeconds());

        facade.watchVisibleSky(location.id())
                .doOnNext(this::printTick)
                .take(WATCH_DURATION)
                .blockLast();
    }

    private void printTick(List<WatchVisibleSkySubscription.CelestialObject> visible) {
        Map<String, String> namesById = new LinkedHashMap<>();
        for (WatchVisibleSkySubscription.CelestialObject object : visible) {
            namesById.put(object.id(), object.name());
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
}
