package dev.glisseo.izar.client;

import java.util.Collections;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.graphql.client.SubscriptionErrorException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A {@link GraphQlTransport} that POSTs {@link PersistedIdRequestBody}'s hash-only shape through a
 * reactive {@link WebClient}, for {@link ReactiveGraphQlOperations} under {@link
 * GraphQlExecutionMode#PERSISTED_ID}.
 *
 * <p>{@code webClient} is expected to already be scoped to the same endpoint URL {@code
 * FULL_DOCUMENT} mode uses: a compatible server resolves this shape's document server-side on its
 * standard endpoint, so no separate URL is needed. Subscription responses use
 * HTTP SSE; an SSE {@code error} event terminates the stream with a {@link
 * SubscriptionErrorException}.
 */
final class WebClientPersistedIdTransport implements GraphQlTransport {

    private static final MediaType APPLICATION_GRAPHQL = new MediaType("application", "graphql+json");

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<ServerSentEvent<Map<String, Object>>> SSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final MediaType contentType;

    WebClientPersistedIdTransport(WebClient webClient) {
        this.webClient = webClient;
        this.contentType = initContentType(webClient);
    }

    @Override
    public Mono<GraphQlResponse> execute(GraphQlRequest request) {
        return webClient
                .post()
                .contentType(contentType)
                .accept(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE,
                        APPLICATION_GRAPHQL)
                .bodyValue(PersistedIdRequestBody.of(request))
                .attributes(attributes -> copyAttributes(request, attributes))
                .exchangeToMono(response -> {
                    if (response.statusCode().equals(HttpStatus.OK)
                            || (response.statusCode().is4xxClientError() && isGraphQlResponse(response))) {
                        return response.bodyToMono(MAP_TYPE);
                    }
                    return response.createError();
                })
                .map(GraphQlTransport::createResponse);
    }

    @Override
    public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
        return webClient
                .post()
                .contentType(contentType)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(PersistedIdRequestBody.of(request))
                .attributes(attributes -> copyAttributes(request, attributes))
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .takeUntil(event -> "complete".equals(event.event()))
                .concatMap(event -> {
                    if ("next".equals(event.event())) {
                        return Flux.just(GraphQlTransport.createResponse(
                                event.data() != null ? event.data() : Collections.emptyMap()));
                    }
                    if ("error".equals(event.event())) {
                        GraphQlResponse response = GraphQlTransport.createResponse(
                                event.data() != null ? event.data() : Collections.emptyMap());
                        return Flux.error(new SubscriptionErrorException(request, response.getErrors()));
                    }
                    return Flux.empty();
                });
    }

    private static void copyAttributes(GraphQlRequest request, Map<String, Object> attributes) {
        if (request instanceof ClientGraphQlRequest clientRequest) {
            attributes.putAll(clientRequest.getAttributes());
        }
    }

    private static MediaType initContentType(WebClient webClient) {
        HttpHeaders headers = new HttpHeaders();
        webClient.mutate().defaultHeaders(headers::putAll);
        MediaType contentType = headers.getContentType();
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }

    private static boolean isGraphQlResponse(ClientResponse response) {
        return MediaTypes.APPLICATION_GRAPHQL_RESPONSE
                .isCompatibleWith(response.headers().contentType().orElse(null));
    }
}
