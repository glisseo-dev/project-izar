package dev.glisseo.izar.examples.nightsky.server;

import java.time.Duration;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

/**
 * The Nightsky schema's resolvers. Spring GraphQL's default type resolver matches each returned
 * {@link CelestialObject} to a GraphQL object type by its Java simple class name ({@link Star},
 * {@link Nebula}, {@link Galaxy}), so no explicit type resolver is registered here.
 */
@Controller
class NightskyGraphQlController {

    private final NightskyRepository repository;
    private final NightSky nightSky;

    NightskyGraphQlController(NightskyRepository repository, NightSky nightSky) {
        this.repository = repository;
        this.nightSky = nightSky;
    }

    @QueryMapping
    List<Constellation> constellations() {
        return repository.constellations();
    }

    @QueryMapping
    List<ViewingLocation> viewingLocations() {
        return repository.viewingLocations();
    }

    @QueryMapping
    List<CelestialObject> visibleNow(@Argument String locationId) {
        return visibleSkyAt(locationId);
    }

    @SubscriptionMapping
    Flux<List<CelestialObject>> visibleSky(@Argument String locationId) {
        return Flux.interval(Duration.ZERO, Duration.ofSeconds(3)).map(tick -> visibleSkyAt(locationId));
    }

    private List<CelestialObject> visibleSkyAt(String locationId) {
        ViewingLocation location = repository.locationById(locationId);
        double currentHour = nightSky.currentHourAt(location.coordinates());
        return repository.allObjects().stream()
                .filter(object -> nightSky.isVisible(object, currentHour))
                .toList();
    }

    @QueryMapping
    List<Observation> observations(@Argument String locationId) {
        return repository.observationsAt(locationId);
    }

    @MutationMapping
    Observation logObservation(@Argument LogObservationInput input) {
        return repository.logObservation(input);
    }

    /** Applies to every implementing type: {@code Constellation.members} has no stored field. */
    @SchemaMapping(typeName = "Constellation", field = "members")
    List<CelestialObject> members(Constellation constellation) {
        return repository.objectsInConstellation(constellation.id());
    }

    /** One resolver for the whole interface: {@link Star}, {@link Nebula}, and {@link Galaxy} all
     * reach it, since none stores a direct {@link Constellation} reference (see {@link Constellation}). */
    @SchemaMapping(typeName = "CelestialObject", field = "constellation")
    Constellation constellation(CelestialObject object) {
        return repository.constellationById(object.constellationId());
    }

    @SchemaMapping(typeName = "Observation", field = "subject")
    CelestialObject subject(Observation observation) {
        return repository.objectById(observation.objectId());
    }

    @SchemaMapping(typeName = "Observation", field = "location")
    ViewingLocation location(Observation observation) {
        return repository.locationById(observation.locationId());
    }
}
