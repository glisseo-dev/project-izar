package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.examples.nightsky.generated.WatchVisibleSkySubscription;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Streams the same {@code WatchVisibleSky} subscription {@link LiveSkyFeed} prints on startup as
 * server-sent events, through the shared {@link NightskyGraphQlFacade}, so another process can
 * watch the sky rotate live instead of reading this application's stdout.
 */
@RestController
class LiveSkyController {

    private final NightskyGraphQlFacade facade;

    LiveSkyController(NightskyGraphQlFacade facade) {
        this.facade = facade;
    }

    /** A dependency-free liveness check, for Docker Compose and similar orchestrators. */
    @GetMapping("/health")
    String health() {
        return "OK";
    }

    @GetMapping(value = "/visible-sky/stream", produces = "text/event-stream")
    Flux<List<VisibleObjectView>> stream(@RequestParam String locationId) {
        return facade.watchVisibleSky(locationId)
                .map(objects -> objects.stream().map(LiveSkyController::toView).toList());
    }

    private static VisibleObjectView toView(WatchVisibleSkySubscription.CelestialObject object) {
        String kind = switch (object) {
            case WatchVisibleSkySubscription.CelestialObjectStar ignored -> "Star";
            case WatchVisibleSkySubscription.CelestialObjectNebula ignored -> "Nebula";
            case WatchVisibleSkySubscription.CelestialObjectGalaxy ignored -> "Galaxy";
            case WatchVisibleSkySubscription.CelestialObjectUnrecognized ignored -> "Unrecognized";
        };
        String detail = switch (object) {
            case WatchVisibleSkySubscription.CelestialObjectStar star -> "spectral type " + star.spectralType();
            case WatchVisibleSkySubscription.CelestialObjectNebula nebula -> nebula.nebulaType().toString();
            case WatchVisibleSkySubscription.CelestialObjectGalaxy galaxy ->
                    galaxy.distanceLightYears() + " light years away";
            case WatchVisibleSkySubscription.CelestialObjectUnrecognized unrecognized -> unrecognized.__typename();
        };
        return new VisibleObjectView(
                object.id(), object.name(), object.magnitude(), object.constellation().name(), kind, detail);
    }

    record VisibleObjectView(
            String id, String name, double magnitude, String constellation, String kind, String detail) {}
}
