package dev.glisseo.izar.client;

import static dev.glisseo.izar.client.ClientHttpTestFixtures.thingOperation;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link SynchronousGraphQlOperations#persistedQuery(RestClient)} and {@link
 * ReactiveGraphQlOperations#persistedQuery(WebClient)} are the entry point for an application that
 * builds its own {@code RestClient}/{@code WebClient} with its authentication, interceptors, and
 * other transport settings. The real HTTP fixture proves that an application-supplied transport
 * does not put the document on the wire.
 */
class PersistedQueryFactoryHttpIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aSynchronousHandWiredClientNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith("{\"data\":{\"thing\":\"ok\"}}");
            RestClient restClient = RestClient.builder().baseUrl(server.url()).build();

            GraphQlResult<String> result =
                    SynchronousGraphQlOperations.persistedQuery(restClient).execute(thingOperation());

            assertThat(result.assertNoErrors()).isEqualTo("ok");
            assertThat(server.requests())
                    .singleElement()
                    .satisfies(request -> assertThat(readTree(request.body()).has("query"))
                            .isFalse());
        }
    }

    @Test
    void aReactiveHandWiredClientNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith("{\"data\":{\"thing\":\"ok\"}}");
            WebClient webClient = WebClient.builder().baseUrl(server.url()).build();

            StepVerifier.create(ReactiveGraphQlOperations.persistedQuery(webClient).execute(thingOperation()))
                    .assertNext(result -> assertThat(result.assertNoErrors()).isEqualTo("ok"))
                    .verifyComplete();

            assertThat(server.requests())
                    .singleElement()
                    .satisfies(request -> assertThat(readTree(request.body()).has("query"))
                            .isFalse());
        }
    }

    @Test
    void theTwoArgOverloadPassesItsExplicitDecodingPolicyRatherThanTheDefault() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith("{\"data\":{\"thing\":\"ok\"}}");
            RestClient restClient = RestClient.builder().baseUrl(server.url()).build();

            GraphQlResult<String> result = SynchronousGraphQlOperations.persistedQuery(restClient, DecodingPolicy.STRICT)
                    .execute(policyAssertingOperation(DecodingPolicy.STRICT));

            assertThat(result.assertNoErrors()).isEqualTo("ok");
        }
    }

    @Test
    void theCustomizerOverloadAppliesToTheBuilderAndStillNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith("{\"data\":{\"thing\":\"ok\"}}");
            RestClient restClient = RestClient.builder().baseUrl(server.url()).build();
            AtomicInteger interceptions = new AtomicInteger();

            GraphQlClientInterceptor interceptor = countingInterceptor(interceptions);
            GraphQlResult<String> result = SynchronousGraphQlOperations.persistedQuery(
                            restClient, DecodingPolicy.LENIENT, builder -> builder.interceptor(interceptor))
                    .execute(thingOperation());

            assertThat(result.assertNoErrors()).isEqualTo("ok");
            assertThat(interceptions).hasValue(1);
            assertThat(server.requests())
                    .singleElement()
                    .satisfies(request -> assertThat(readTree(request.body()).has("query"))
                            .isFalse());
        }
    }

    @Test
    void theReactiveCustomizerOverloadAppliesToTheBuilderAndStillNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith("{\"data\":{\"thing\":\"ok\"}}");
            WebClient webClient = WebClient.builder().baseUrl(server.url()).build();
            AtomicInteger interceptions = new AtomicInteger();

            GraphQlClientInterceptor interceptor = countingInterceptor(interceptions);
            Mono<GraphQlResult<String>> result = ReactiveGraphQlOperations.persistedQuery(
                            webClient, DecodingPolicy.LENIENT, builder -> builder.interceptor(interceptor))
                    .execute(thingOperation());

            StepVerifier.create(result)
                    .assertNext(r -> assertThat(r.assertNoErrors()).isEqualTo("ok"))
                    .verifyComplete();

            assertThat(interceptions).hasValue(1);
            assertThat(server.requests())
                    .singleElement()
                    .satisfies(request -> assertThat(readTree(request.body()).has("query"))
                            .isFalse());
        }
    }

    private static GraphQlClientInterceptor countingInterceptor(AtomicInteger interceptions) {
        return new GraphQlClientInterceptor() {
            @Override
            public Mono<ClientGraphQlResponse> intercept(ClientGraphQlRequest request, Chain chain) {
                interceptions.incrementAndGet();
                return chain.next(request);
            }
        };
    }

    private static GraphQlOperation<String> policyAssertingOperation(DecodingPolicy expectedPolicy) {
        return new GraphQlOperation<>() {
            @Override
            public String operationName() {
                return "GetThing";
            }

            @Override
            public String document() {
                return "query GetThing { thing }";
            }

            @Override
            public String operationId() {
                return ManifestOperation.idFor(document());
            }

            @Override
            public Map<String, Object> variables() {
                return Map.of();
            }

            @SuppressWarnings("unchecked")
            @Override
            public String decode(@Nullable Object data, DecodingPolicy policy) {
                assertThat(policy).isEqualTo(expectedPolicy);
                return (String) ((Map<String, Object>) data).get("thing");
            }
        };
    }

    private static JsonNode readTree(String json) {
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
