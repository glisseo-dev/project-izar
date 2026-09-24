package dev.glisseo.izar.examples.onequeryconsumer;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpSyncGraphQlClient;

/**
 * The explicit, application-owned client configuration issue 01 calls for: one named endpoint,
 * one Spring-configured client. Izar supplies only {@link SynchronousGraphQlOperations}, the
 * bridge from a generated operation to this client; Boot-style named-client autoconfiguration is
 * a later issue.
 *
 * <p>The client bean and {@code bookServiceOperations}'s injection point are both {@link Lazy}:
 * the test overrides {@code book-service.graphql-endpoint} with {@code ${local.server.port}},
 * which the embedded server only publishes once it actually starts, during
 * {@code finishRefresh()} — after ordinary singletons are already created. {@code @Lazy} on the
 * bean definition keeps the container's eager singleton sweep from creating it directly;
 * {@code @Lazy} on the parameter that consumes it makes Spring inject a deferring proxy there too
 * (fine here: {@link GraphQlClient} is an interface). Either alone still resolves the endpoint
 * placeholder too early; both together defer it to first real use, once the port is known.
 */
@Configuration
class GraphQlClientConfiguration {

    @Bean
    @Lazy
    GraphQlClient bookServiceGraphQlClient(
            @Value("${book-service.graphql-endpoint:http://localhost:8080/graphql}") String endpoint) {
        return HttpSyncGraphQlClient.builder().url(endpoint).build();
    }

    @Bean
    SynchronousGraphQlOperations bookServiceOperations(
            @Lazy GraphQlClient bookServiceGraphQlClient) {
        return SynchronousGraphQlOperations.fullDocument(bookServiceGraphQlClient);
    }
}
