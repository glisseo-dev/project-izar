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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * The one place this application calls the generated Nightsky operations, shared by {@link
 * SkyExplorerRunner}'s startup story and {@link SkyExplorerController}'s REST endpoints, so both
 * present the same {@link SynchronousGraphQlOperations}/{@link ReactiveGraphQlOperations} calls
 * instead of each building its own.
 */
@Component
class NightskyGraphQlFacade {

    private final SynchronousGraphQlOperations operations;
    private final ReactiveGraphQlOperations subscriptionOperations;

    NightskyGraphQlFacade(
            SynchronousGraphQlOperations operations, ReactiveGraphQlOperations subscriptionOperations) {
        this.operations = operations;
        this.subscriptionOperations = subscriptionOperations;
    }

    GetSkyCatalogQuery.Data catalog() {
        return operations.execute(new GetSkyCatalogQuery()).assertNoErrors();
    }

    GetViewingLocationsQuery.ViewingLocation viewingLocation(String locationId) {
        return operations.execute(new GetViewingLocationsQuery()).assertNoErrors().viewingLocations().stream()
                .filter(location -> location.id().equals(locationId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No viewing location '" + locationId + "'."));
    }

    GetVisibleNowQuery.Data visibleNow(String locationId) {
        GetVisibleNowQuery query = GetVisibleNowQuery.builder().locationId(locationId).build();
        return operations.execute(query).assertNoErrors();
    }

    LogObservationMutation.LogObservation logObservation(
            String locationId, String objectId, ViewingConditions2 conditions, String notes) {
        LogObservationInput input = LogObservationInput.builder()
                .objectId(objectId)
                .locationId(locationId)
                .conditions(conditions)
                .notes(notes)
                .build();
        LogObservationMutation.Data data =
                operations.execute(LogObservationMutation.builder().input(input).build()).assertNoErrors();
        return data.logObservation();
    }

    GetObservationsQuery.Data observations(String locationId) {
        GetObservationsQuery query = GetObservationsQuery.builder().locationId(locationId).build();
        return operations.execute(query).assertNoErrors();
    }

    Flux<WatchVisibleSkySubscription.Data> watchVisibleSky(String locationId) {
        WatchVisibleSkySubscription subscription =
                WatchVisibleSkySubscription.builder().locationId(locationId).build();
        return subscriptionOperations.executeSubscription(subscription).map(result -> result.assertNoErrors());
    }
}
