package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * The explicit, application-owned client configuration: one Spring-configured, reactive {@link
 * GraphQlClient}, wrapped in Izar's {@link ReactiveGraphQlOperations}. Izar never
 * constructs or owns the transport; this application does, the same way {@code
 * nightsky-sync-client}'s {@code GraphQlClientConfiguration} wires its synchronous counterpart.
 */
@Configuration
class ReactiveGraphQlClientConfiguration {

    @Bean
    GraphQlClient nightskyReactiveGraphQlClient(
            @Value("${nightsky.server-url:http://localhost:8083/graphql}") String serverUrl) {
        WebClient webClient = WebClient.builder()
                .baseUrl(serverUrl)
                .filter(LoggingExchangeFilterFunction.create())
                .build();
        return HttpGraphQlClient.builder(webClient).build();
    }

    @Bean
    ReactiveGraphQlOperations nightskyReactiveOperations(GraphQlClient nightskyReactiveGraphQlClient) {
        return ReactiveGraphQlOperations.fullDocument(nightskyReactiveGraphQlClient);
    }
}
