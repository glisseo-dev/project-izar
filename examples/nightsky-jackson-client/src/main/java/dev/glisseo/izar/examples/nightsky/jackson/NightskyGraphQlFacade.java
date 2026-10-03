package dev.glisseo.izar.examples.nightsky.jackson;

import dev.glisseo.izar.examples.nightsky.jackson.generated.GetVisibleNowQuery;
import java.util.List;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.stereotype.Component;

/**
 * The one place this application calls the generated {@code GetVisibleNow} operation, shared by
 * {@link VisibleSkyRunner}'s startup story and {@link VisibleSkyController}'s REST endpoint.
 */
@Component
class NightskyGraphQlFacade {

    private final GraphQlClient graphQlClient;

    NightskyGraphQlFacade(GraphQlClient graphQlClient) {
        this.graphQlClient = graphQlClient;
    }

    List<GetVisibleNowQuery.CelestialObject> visibleNow(String locationId) {
        GetVisibleNowQuery operation =
                GetVisibleNowQuery.builder().locationId(locationId).build();
        return graphQlClient.document(operation.document())
                .variables(operation.variables())
                .retrieve(GetVisibleNowQuery.VISIBLE_NOW)
                .toEntityList(GetVisibleNowQuery.CelestialObject.class)
                .block();
    }
}
