package dev.glisseo.izar.examples.nightsky.gradle;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The application-owned client configuration: one Spring-configured {@code RestClient}, wrapped in
 * Izar's {@link SynchronousGraphQlOperations}. {@code persistedQuery} sends each operation's
 * persisted ID instead of its document, which an enforcing server accepts only for operations this
 * client published.
 */
@Configuration
class GraphQlClientConfiguration {

    @Bean
    SynchronousGraphQlOperations nightskyOperations(
            @Value("${nightsky.server-url:http://localhost:8083/graphql}") String serverUrl) {
        return SynchronousGraphQlOperations.persistedQuery(
                RestClient.builder().baseUrl(serverUrl).build());
    }
}
