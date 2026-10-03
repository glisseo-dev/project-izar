package dev.glisseo.izar.examples.nightsky.jackson;

import dev.glisseo.izar.examples.nightsky.jackson.generated.GetVisibleNowQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the same {@code toEntityList}-decoded, polymorphic {@code GetVisibleNow} result {@link
 * VisibleSkyRunner} prints on startup, as JSON.
 */
@RestController
class VisibleSkyController {

    private final NightskyGraphQlFacade facade;

    VisibleSkyController(NightskyGraphQlFacade facade) {
        this.facade = facade;
    }

    /** A dependency-free liveness check, for Docker Compose and similar orchestrators. */
    @GetMapping("/health")
    String health() {
        return "OK";
    }

    @GetMapping("/visible-now")
    List<VisibleObjectView> visibleNow(@RequestParam String locationId) {
        return facade.visibleNow(locationId).stream().map(VisibleSkyController::toView).toList();
    }

    private static VisibleObjectView toView(GetVisibleNowQuery.CelestialObject object) {
        String kind = switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar ignored -> "Star";
            case GetVisibleNowQuery.CelestialObjectNebula ignored -> "Nebula";
            case GetVisibleNowQuery.CelestialObjectGalaxy ignored -> "Galaxy";
            case GetVisibleNowQuery.CelestialObjectUnrecognized ignored -> "Unrecognized";
        };
        return new VisibleObjectView(object.name(), object.constellation().name(), object.magnitude(), kind);
    }

    record VisibleObjectView(String name, String constellation, double magnitude, String kind) {}
}
