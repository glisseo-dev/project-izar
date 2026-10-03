package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.examples.nightsky.generated.GetObservationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetSkyCatalogQuery;
import dev.glisseo.izar.examples.nightsky.generated.GetVisibleNowQuery;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation;
import dev.glisseo.izar.examples.nightsky.generated.LogObservationMutation.ViewingConditions2;
import dev.glisseo.izar.examples.nightsky.generated.WatchVisibleSkySubscription;
import java.util.List;
import reactor.core.publisher.Flux;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes this client's own story, catalog browsing and observation logging, as plain JSON over
 * HTTP, so another process can drive the same {@link NightskyGraphQlFacade} calls {@link
 * SkyExplorerRunner} prints on startup without scraping stdout.
 */
@RestController
class SkyExplorerController {

    private final NightskyGraphQlFacade facade;

    SkyExplorerController(NightskyGraphQlFacade facade) {
        this.facade = facade;
    }

    /** A dependency-free liveness check, for Docker Compose and similar orchestrators. */
    @GetMapping("/health")
    String health() {
        return "OK";
    }

    @GetMapping("/catalog")
    List<ConstellationView> catalog() {
        return facade.catalog().constellations().stream().map(SkyExplorerController::toView).toList();
    }

    @GetMapping("/visible-now")
    VisibleNowView visibleNow(@RequestParam String locationId) {
        String locationName = facade.viewingLocation(locationId).name();
        List<VisibleObjectView> objects =
                facade.visibleNow(locationId).visibleNow().stream().map(SkyExplorerController::toView).toList();
        return new VisibleNowView(locationName, objects);
    }

/**
 * Streams the persisted {@code WatchVisibleSky} subscription as server-sent events, one JSON
 * array of object names per update, so another process can watch hash-only subscription
 * execution against an allowlisting server.
 */
@GetMapping(value = "/visible-sky/stream", produces = "text/event-stream")
Flux<List<String>> visibleSkyStream(@RequestParam String locationId) {
    return facade.watchVisibleSky(locationId)
            .map(data -> data.visibleSky().stream()
                    .map(WatchVisibleSkySubscription.CelestialObject::name)
                    .toList());
}

@PostMapping("/observations")
    ObservationView logObservation(@RequestBody LogObservationRequest request) {
        LogObservationMutation.LogObservation observation = facade.logObservation(
                request.locationId(),
                request.objectId(),
                ViewingConditions2.valueOf(request.conditions()),
                request.notes());
        return new ObservationView(
                observation.id(),
                observation.observedAt().toString(),
                observation.conditions().toString(),
                observation.notes(),
                observation.subject().name());
    }

    @GetMapping("/observations")
    List<ObservationView> observations(@RequestParam String locationId) {
        return facade.observations(locationId).observations().stream()
                .map(SkyExplorerController::toView)
                .toList();
    }

    private static ConstellationView toView(GetSkyCatalogQuery.Constellation constellation) {
        List<CatalogMemberView> members = constellation.members().stream()
                .map(member -> new CatalogMemberView(member.id(), member.name(), member.magnitude()))
                .toList();
        return new ConstellationView(
                constellation.id(),
                constellation.name(),
                constellation.abbreviation(),
                constellation.family().toString(),
                constellation.mythology(),
                members);
    }

    private static VisibleObjectView toView(GetVisibleNowQuery.CelestialObject object) {
        return new VisibleObjectView(
                object.id(),
                object.name(),
                object.magnitude(),
                object.constellation().name(),
                VisibleObjectPresentation.kind(object),
                VisibleObjectPresentation.detail(object));
    }

    private static ObservationView toView(GetObservationsQuery.Observation observation) {
        return new ObservationView(
                observation.id(),
                observation.observedAt().toString(),
                observation.conditions().toString(),
                observation.notes(),
                observation.subject().name());
    }

    record ConstellationView(
            String id,
            String name,
            String abbreviation,
            String family,
            String mythology,
            List<CatalogMemberView> members) {}

    record CatalogMemberView(String id, String name, double magnitude) {}

    record VisibleNowView(String locationName, List<VisibleObjectView> objects) {}

    record VisibleObjectView(
            String id, String name, double magnitude, String constellation, String kind, String detail) {}

    record ObservationView(
            String id, String observedAt, String conditions, String notes, String subjectName) {}

    record LogObservationRequest(String locationId, String objectId, String conditions, String notes) {}
}
