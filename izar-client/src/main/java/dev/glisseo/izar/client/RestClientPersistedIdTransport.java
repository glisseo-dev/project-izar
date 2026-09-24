package dev.glisseo.izar.client;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.GraphQlResponse;
import org.springframework.graphql.MediaTypes;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.GraphQlTransport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A {@link GraphQlTransport} that POSTs {@link PersistedIdRequestBody}'s hash-only shape through a
 * blocking {@link RestClient}, for {@link SynchronousGraphQlOperations} under {@link
 * GraphQlExecutionMode#PERSISTED_ID}.
 *
 * <p>{@code restClient} is expected to already be scoped to the same endpoint URL {@code
 * FULL_DOCUMENT} mode uses: a compatible server resolves this shape's document server-side on its
 * standard endpoint, so no separate URL is needed (see ADR 0010).
 */
final class RestClientPersistedIdTransport implements GraphQlTransport {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final MediaType contentType;

    RestClientPersistedIdTransport(RestClient restClient) {
        this.restClient = restClient;
        this.contentType = initContentType(restClient);
    }

    @Override
    public Mono<GraphQlResponse> execute(GraphQlRequest request) {
        return Mono.fromCallable(() -> GraphQlTransport.createResponse(post(request)));
    }

    @Override
    public Flux<GraphQlResponse> executeSubscription(GraphQlRequest request) {
        return Flux.error(new UnsupportedOperationException("Persisted-ID execution does not support subscriptions."));
    }

    private Map<String, Object> post(GraphQlRequest request) {
        Map<String, Object> response = restClient
                .post()
                .contentType(contentType)
                .accept(MediaType.APPLICATION_JSON, MediaTypes.APPLICATION_GRAPHQL_RESPONSE)
                .body(PersistedIdRequestBody.of(request))
                .attributes(attributes -> copyAttributes(request, attributes))
                .exchange((httpRequest, httpResponse) -> {
                    if (httpResponse.getStatusCode().equals(HttpStatus.OK)
                            || (httpResponse.getStatusCode().is4xxClientError() && isGraphQlResponse(httpResponse))) {
                        return httpResponse.bodyTo(MAP_TYPE);
                    }
                    if (httpResponse.getStatusCode().is4xxClientError()) {
                        throw HttpClientErrorException.create(
                                httpResponse.getStatusText(),
                                httpResponse.getStatusCode(),
                                httpResponse.getStatusText(),
                                httpResponse.getHeaders(),
                                getBody(httpResponse),
                                getCharset(httpResponse));
                    }
                    throw HttpServerErrorException.create(
                            httpResponse.getStatusText(),
                            httpResponse.getStatusCode(),
                            httpResponse.getStatusText(),
                            httpResponse.getHeaders(),
                            getBody(httpResponse),
                            getCharset(httpResponse));
                });
        return response != null ? response : Collections.emptyMap();
    }

    private static void copyAttributes(GraphQlRequest request, Map<String, Object> attributes) {
        if (request instanceof ClientGraphQlRequest clientRequest) {
            attributes.putAll(clientRequest.getAttributes());
        }
    }

    private static MediaType initContentType(RestClient restClient) {
        HttpHeaders headers = new HttpHeaders();
        restClient.mutate().defaultHeaders(headers::putAll);
        MediaType contentType = headers.getContentType();
        return contentType != null ? contentType : MediaType.APPLICATION_JSON;
    }

    private static boolean isGraphQlResponse(ClientHttpResponse response) {
        return MediaTypes.APPLICATION_GRAPHQL_RESPONSE.isCompatibleWith(response.getHeaders().getContentType());
    }

    private static byte[] getBody(HttpInputMessage message) {
        try {
            return FileCopyUtils.copyToByteArray(message.getBody());
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    private static @Nullable Charset getCharset(HttpInputMessage message) {
        MediaType contentType = message.getHeaders().getContentType();
        return contentType != null ? contentType.getCharset() : null;
    }
}
