package dev.glisseo.izar.client;

import static dev.glisseo.izar.client.ClientHttpTestFixtures.thingOperation;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.PersistedQueryExtension;
import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.client.ClientGraphQlRequest;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.GraphQlClientInterceptor;
import org.springframework.graphql.client.SubscriptionErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Proves that both persisted-ID factories omit the operation document on real HTTP requests. */
class PersistedIdExecutionHttpIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void aSynchronousPersistedIdRequestNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith(
                    "{\"data\":{\"thing\":\"ok\"},\"extensions\":{\"izar\":{\"mode\":\"ENFORCE\",\"registered\":true,\"revision\":\"rev-1\"}}}");

            GraphQlResult<String> result = synchronousOperations(server).execute(thingOperation());

            assertThat(result.assertNoErrors()).isEqualTo("ok");
            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.path()).isEqualTo("/graphql");
                assertThat(readTree(request.body()).has("query")).isFalse();
            });
        }
    }

    @Test
    void aSynchronousPersistedIdRequestCarriesTheExpectedOperationIdAndVariables() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            synchronousOperations(server).execute(thingOperation());

            String expectedId = ManifestOperation.idFor(thingOperation().document());
            assertThat(server.requests()).singleElement().satisfies(request -> {
                JsonNode body = readTree(request.body());
                assertThat(body.get("operationName").asText()).isEqualTo("GetThing");
                assertThat(body.get("extensions")
                                .get(PersistedQueryExtension.EXTENSION_NAME)
                                .get("sha256Hash")
                                .asText())
                        .isEqualTo(expectedId);
            });
        }
    }

    @Test
    void aSynchronousPersistedIdRequestPropagatesClientAttributesToRestClientInterceptors() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWith("{\"data\":{\"thing\":\"ok\"}}");
            AtomicReference<Object> observedAttribute = new AtomicReference<>();
            RestClient restClient = RestClient.builder()
                    .baseUrl(server.url())
                    .requestInterceptor((request, body, execution) -> {
                        observedAttribute.set(request.getAttributes().get("traceId"));
                        return execution.execute(request, body);
                    })
                    .build();
            ClientGraphQlRequest request = new ClientGraphQlRequest() {
                @Override
                public String getDocument() {
                    return "query GetThing { thing }";
                }

                @Override
                public String getOperationName() {
                    return "GetThing";
                }

                @Override
                public Map<String, Object> getVariables() {
                    return Map.of();
                }

                @Override
                public Map<String, Object> getExtensions() {
                    return Map.of();
                }

                @Override
                public Map<String, Object> toMap() {
                    return Map.of();
                }

                @Override
                public Map<String, Object> getAttributes() {
                    return Map.of("traceId", "trace-123");
                }
            };

            new RestClientPersistedIdTransport(restClient).execute(request).block();

            assertThat(observedAttribute).hasValue("trace-123");
        }
    }

    @Test
    void aSynchronousGraphQlErrorResponseWithAClientErrorStatusRemainsAGraphQlResult() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWithGraphQlError("{\"errors\":[{\"message\":\"invalid persisted query\"}]}");

            GraphQlResult<String> result = synchronousOperations(server).execute(thingOperation());

            assertThat(result.errors()).singleElement().satisfies(error ->
                    assertThat(error.getMessage()).isEqualTo("invalid persisted query"));
            assertThat(server.requests()).singleElement().satisfies(request ->
                    assertThat(request.headers().get("Accept")).anySatisfy(value ->
                            assertThat(value).contains("application/graphql-response+json")));
        }
    }

    @Test
    void aReactivePersistedIdRequestAlsoNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            StepVerifier.create(reactiveOperations(server).execute(thingOperation()))
                    .assertNext(result -> assertThat(result.assertNoErrors()).isEqualTo("ok"))
                    .verifyComplete();

            assertThat(server.requests()).singleElement().satisfies(request -> {
                assertThat(request.path()).isEqualTo("/graphql");
                assertThat(readTree(request.body()).has("query")).isFalse();
            });
        }
    }

    @Test
    void aReactiveGraphQlErrorResponseWithAClientErrorStatusRemainsAGraphQlResult() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWithGraphQlError("{\"errors\":[{\"message\":\"invalid persisted query\"}]}");

            StepVerifier.create(reactiveOperations(server).execute(thingOperation()))
                    .assertNext(result -> assertThat(result.errors()).singleElement().satisfies(error ->
                            assertThat(error.getMessage()).isEqualTo("invalid persisted query")))
                    .verifyComplete();

            assertThat(server.requests()).singleElement().satisfies(request ->
                    assertThat(request.headers().get("Accept")).anySatisfy(value ->
                            assertThat(value).contains("application/graphql-response+json")));
        }
    }

    @Test
    void aReactivePersistedSubscriptionSendsNoDocumentAndDecodesEverySseEvent() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWithSseAndKeepOpen(
                    "{\"data\":{\"thing\":\"first\"}}",
                    "{\"data\":{\"thing\":\"second\"}}");

            GraphQlOperation<String> operation = subscriptionOperation();

            StepVerifier.create(reactiveOperations(server).executeSubscription(operation))
                    .assertNext(result -> assertThat(result.assertNoErrors()).isEqualTo("first"))
                    .assertNext(result -> assertThat(result.assertNoErrors()).isEqualTo("second"))
                    .expectComplete()
                    .verify(Duration.ofSeconds(3));

            assertThat(server.requests()).singleElement().satisfies(request -> {
                JsonNode body = readTree(request.body());
                assertThat(body.has("query")).isFalse();
                assertThat(body.get("operationName").asText()).isEqualTo("WatchThing");
                assertThat(body.get("extensions")
                                .get(PersistedQueryExtension.EXTENSION_NAME)
                                .get("sha256Hash")
                                .asText())
                        .isEqualTo(ManifestOperation.idFor(operation.document()));
            });
        }
    }

    @Test
    void aReactivePersistedSubscriptionReportsAnSseErrorEvent() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            server.respondWithSseError("{\"errors\":[{\"message\":\"subscription failed\"}]}");

            StepVerifier.create(reactiveOperations(server).executeSubscription(subscriptionOperation()))
                    .verifyErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(SubscriptionErrorException.class)
                            .hasMessageContaining("subscription failed"));
        }
    }

    @Test
    void aSynchronousBuilderCustomizationRunsAndStillNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            RestClient restClient = RestClient.builder().baseUrl(server.url()).build();
            AtomicInteger interceptions = new AtomicInteger();
            GraphQlClient client = PersistedIdGraphQlClient.syncBuilder(restClient)
                    .interceptor(countingInterceptor(interceptions))
                    .build();
            SynchronousGraphQlOperations operations = new SynchronousGraphQlOperations(
                    client, DecodingPolicy.LENIENT, GraphQlExecutionMode.PERSISTED_ID);

            GraphQlResult<String> result = operations.execute(thingOperation());

            assertThat(result.assertNoErrors()).isEqualTo("ok");
            assertThat(interceptions).hasValue(1);
            assertThat(server.requests()).singleElement().satisfies(request ->
                    assertThat(readTree(request.body()).has("query")).isFalse());
        }
    }

    @Test
    void aReactiveBuilderCustomizationRunsAndStillNeverSendsAQueryField() {
        try (RecordingHttpServer server = new RecordingHttpServer()) {
            WebClient webClient = WebClient.builder().baseUrl(server.url()).build();
            AtomicInteger interceptions = new AtomicInteger();
            GraphQlClient client = PersistedIdGraphQlClient.reactiveBuilder(webClient)
                    .interceptor(countingInterceptor(interceptions))
                    .build();
            ReactiveGraphQlOperations operations = new ReactiveGraphQlOperations(
                    client, DecodingPolicy.LENIENT, GraphQlExecutionMode.PERSISTED_ID);

            StepVerifier.create(operations.execute(thingOperation()))
                    .assertNext(result -> assertThat(result.assertNoErrors()).isEqualTo("ok"))
                    .verifyComplete();

            assertThat(interceptions).hasValue(1);
            assertThat(server.requests()).singleElement().satisfies(request ->
                    assertThat(readTree(request.body()).has("query")).isFalse());
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

    private static SynchronousGraphQlOperations synchronousOperations(RecordingHttpServer server) {
        RestClient restClient = RestClient.builder().baseUrl(server.url()).build();
        return new SynchronousGraphQlOperations(
                PersistedIdGraphQlClient.sync(restClient), DecodingPolicy.LENIENT, GraphQlExecutionMode.PERSISTED_ID);
    }

    private static ReactiveGraphQlOperations reactiveOperations(RecordingHttpServer server) {
        WebClient webClient = WebClient.builder().baseUrl(server.url()).build();
        return new ReactiveGraphQlOperations(
                PersistedIdGraphQlClient.reactive(webClient),
                DecodingPolicy.LENIENT,
                GraphQlExecutionMode.PERSISTED_ID);
    }

    private static GraphQlOperation<String> subscriptionOperation() {
        return new GraphQlOperation<>() {
            @Override
            public GraphQlOperationKind operationKind() {
                return GraphQlOperationKind.SUBSCRIPTION;
            }

            @Override
            public String operationName() {
                return "WatchThing";
            }

            @Override
            public String document() {
                return "subscription WatchThing { thing }";
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
