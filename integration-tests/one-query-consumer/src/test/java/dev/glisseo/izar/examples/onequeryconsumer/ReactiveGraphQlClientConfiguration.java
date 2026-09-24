package dev.glisseo.izar.examples.onequeryconsumer;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test-only: issue 07's reactive counterpart to {@link GraphQlClientConfiguration}, pointed at
 * the same fixture endpoint so {@code ExecuteOperationsReactivelyIntegrationTest} can prove the
 * synchronous and reactive adapters observe and decode the same operation identically. A real
 * application wires whichever adapter its own execution model needs, not necessarily both.
 *
 * <p>{@code @Lazy} on both beans defers endpoint resolution to first use, matching {@link
 * GraphQlClientConfiguration}'s reasoning: the test overrides the endpoint with
 * {@code ${local.server.port}}, published only once the embedded server actually starts.
 * {@code GraphQlClient} (an interface, like its synchronous counterpart) is also what makes a
 * {@code @Lazy} parameter work: Spring injects a deferring proxy there only because it can
 * implement the interface, not the concrete {@code HttpGraphQlClient}.
 */
@Configuration
class ReactiveGraphQlClientConfiguration {

    @Bean
    @Lazy
    GraphQlClient bookServiceReactiveGraphQlClient(
            @Value("${book-service.graphql-endpoint:http://localhost:8080/graphql}") String endpoint) {
        return HttpGraphQlClient.builder(WebClient.builder().baseUrl(endpoint)).build();
    }

    @Bean
    ReactiveGraphQlOperations bookServiceReactiveOperations(
            @Lazy GraphQlClient bookServiceReactiveGraphQlClient) {
        return ReactiveGraphQlOperations.fullDocument(bookServiceReactiveGraphQlClient);
    }
}
