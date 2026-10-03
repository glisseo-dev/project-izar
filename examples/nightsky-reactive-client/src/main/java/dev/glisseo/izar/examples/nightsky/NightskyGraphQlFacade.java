package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.examples.nightsky.generated.GetViewingLocationsQuery;
import dev.glisseo.izar.examples.nightsky.generated.WatchVisibleSkySubscription;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The one place this application calls the generated Nightsky operations, shared by {@link
 * LiveSkyFeed}'s startup story and {@link LiveSkyController}'s streaming endpoint.
 */
@Component
class NightskyGraphQlFacade {

    private final ReactiveGraphQlOperations operations;

    NightskyGraphQlFacade(ReactiveGraphQlOperations operations) {
        this.operations = operations;
    }

    Mono<GetViewingLocationsQuery.ViewingLocation> firstViewingLocation() {
        return operations
                .execute(new GetViewingLocationsQuery())
                .map(result -> result.assertNoErrors().viewingLocations().getFirst());
    }

    Flux<List<WatchVisibleSkySubscription.CelestialObject>> watchVisibleSky(String locationId) {
        WatchVisibleSkySubscription subscription =
                WatchVisibleSkySubscription.builder().locationId(locationId).build();
        return operations
                .executeSubscription(subscription)
                .map(result -> result.assertNoErrors().visibleSky());
    }
}
