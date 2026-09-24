package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.client.RestClient;

/**
 * The explicit, application-owned client configuration: one Spring-configured {@code RestClient},
 * wrapped in Izar's {@link SynchronousGraphQlOperations}. Izar never constructs or owns the
 * transport; this application does, the way any consumer would wire authentication, timeouts, or
 * interceptors onto its own client, demonstrated here by wiring {@link
 * LoggingClientHttpRequestInterceptor} onto the {@code RestClient}.
 *
 * <p>The persisted sky subscription uses a separate application-configured {@link WebClient} and
 * Izar's reactive adapter because subscriptions stream multiple responses.
 *
 * <p>{@link SynchronousGraphQlOperations#persistedQuery(RestClient)} builds the client over the
 * transport that omits the operation's document from the wire and pairs it with {@link
 * dev.glisseo.izar.client.GraphQlExecutionMode#PERSISTED_ID} in one call, rather than requiring
 * the caller to build a matching client and mode separately.
 */
@Configuration
class GraphQlClientConfiguration {

    @Bean
    ReactiveGraphQlOperations nightskyPersistedSubscriptionOperations(
            @Value("${nightsky.server-url:http://localhost:8083/graphql}") String serverUrl) {
        WebClient webClient = WebClient.builder().baseUrl(serverUrl).build();
        return ReactiveGraphQlOperations.persistedQuery(webClient);
    }

    @Bean
    SynchronousGraphQlOperations nightskyOperations(
            @Value("${nightsky.server-url:http://localhost:8083/graphql}") String serverUrl) {
        RestClient restClient = RestClient.builder()
                .baseUrl(serverUrl)
                .requestInterceptor(new LoggingClientHttpRequestInterceptor())
                .build();
        return SynchronousGraphQlOperations.persistedQuery(restClient);
    }
}
