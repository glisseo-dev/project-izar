package dev.glisseo.izar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.PersistedQueryExtension;
import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.GraphQlTransportException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Mirrors {@link SynchronousGraphQlOperationsTest}'s scenarios: the two adapters share
 * {@link GraphQlResult}, {@link GraphQlOperationException}, and {@link GraphQlDecodingException}
 * rather than each defining its own outcome and failure types, so the same request-to-result
 * mapping applies whichever adapter executes the operation.
 */
class ReactiveGraphQlOperationsTest {

    @Test
    void executesAndDecodesACompleteResponse() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of("id", "42"), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(true, rawData, List.of(), Map.of());

        Mono<GraphQlResult<String>> result =
                ReactiveGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertThat(r.data()).isEqualTo("decoded");
                    assertThat(r.hasErrors()).isFalse();
                    assertThat(r.extensions()).isEmpty();
                    assertThat(r.assertNoErrors()).isEqualTo("decoded");
                })
                .verifyComplete();
    }

    @Test
    void returnsPartialDataAlongsideErrorsWhenTheResponseIsStillValid() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        ResponseError error = mock(ResponseError.class);
        when(error.getMessage()).thenReturn("nested field failed");
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(true, rawData, List.of(error), Map.of("trace", "abc"));

        Mono<GraphQlResult<String>> result =
                ReactiveGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertThat(r.data()).isEqualTo("decoded");
                    assertThat(r.errors()).containsExactly(error);
                    assertThat(r.extensions()).containsEntry("trace", "abc");
                    assertThatThrownBy(r::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
                })
                .verifyComplete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void representsMissingDataWithoutCallingDecode() {
        ResponseError error = mock(ResponseError.class);
        when(error.getMessage()).thenReturn("boom");
        GraphQlOperation<String> operation = mock(GraphQlOperation.class);
        when(operation.operationName()).thenReturn("GetThing");
        when(operation.document()).thenReturn("query GetThing { thing }");
        when(operation.variables()).thenReturn(Map.of());
        ClientGraphQlResponse response = stubResponse(false, null, List.of(error), Map.of());

        Mono<GraphQlResult<String>> result =
                ReactiveGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        StepVerifier.create(result)
                .assertNext(r -> {
                    assertThat(r.data()).isNull();
                    assertThat(r.errors()).containsExactly(error);
                    assertThatThrownBy(r::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
                })
                .verifyComplete();
        verify(operation, never()).decode(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapsAMalformedResponseAsADecodingFailureDistinctFromGraphQlErrors() {
        Map<String, Object> rawData = Map.of("thing", "not-what-was-expected");
        GraphQlOperation<String> operation = mock(GraphQlOperation.class);
        when(operation.operationName()).thenReturn("GetThing");
        when(operation.document()).thenReturn("query GetThing { thing }");
        when(operation.variables()).thenReturn(Map.of());
        when(operation.decode(rawData, DecodingPolicy.LENIENT))
                .thenThrow(new IllegalArgumentException("Expected a GraphQL Int value"));
        ClientGraphQlResponse response = stubResponse(true, rawData, List.of(), Map.of());

        Mono<GraphQlResult<String>> result =
                ReactiveGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        StepVerifier.create(result)
                .verifyErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(GraphQlDecodingException.class)
                        .hasCauseInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("GetThing"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void passesItsConfiguredDecodingPolicyToTheOperationRatherThanTheDefault() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        GraphQlOperation<String> operation = mock(GraphQlOperation.class);
        when(operation.operationName()).thenReturn("GetThing");
        when(operation.document()).thenReturn("query GetThing { thing }");
        when(operation.variables()).thenReturn(Map.of());
        when(operation.decode(rawData, DecodingPolicy.STRICT)).thenReturn("decoded");
        ClientGraphQlResponse response = stubResponse(true, rawData, List.of(), Map.of());

        Mono<GraphQlResult<String>> result = ReactiveGraphQlOperations.fullDocument(
                        clientFor(operation, response), DecodingPolicy.STRICT)
                .execute(operation);

        StepVerifier.create(result)
                .assertNext(r -> assertThat(r.data()).isEqualTo("decoded"))
                .verifyComplete();
        verify(operation, never()).decode(any(), eq(DecodingPolicy.LENIENT));
    }

    @Test
    void defaultExecutionModeNeverAddsThePersistedQueryExtension() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(true, rawData, List.of(), Map.of());
        GraphQlClient client = clientFor(operation, response);

        StepVerifier.create(ReactiveGraphQlOperations.fullDocument(client).execute(operation))
                .expectNextCount(1)
                .verifyComplete();

        GraphQlClient.RequestSpec request = client.document(operation.document());
        verify(request, never()).extension(any(), any());
    }

    @Test
    void persistedIdExecutionModeAddsTheApolloCompatibleExtensionDerivedFromTheDocument() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        String document = "query GetThing { thing }";
        GraphQlOperation<String> operation = fakeOperation("GetThing", document, Map.of(), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(true, rawData, List.of(), Map.of());
        GraphQlClient client = clientFor(operation, response);

        StepVerifier.create(new ReactiveGraphQlOperations(client, DecodingPolicy.LENIENT, GraphQlExecutionMode.PERSISTED_ID)
                        .execute(operation))
                .expectNextCount(1)
                .verifyComplete();

        GraphQlClient.RequestSpec request = client.document(operation.document());
        verify(request)
                .extension(
                        PersistedQueryExtension.EXTENSION_NAME,
                        PersistedQueryExtension.of(document).toExtensionValue());
    }

    @Test
    void propagatesTransportFailuresAsAnErrorSignalWithoutWrappingThemIntoAResult() {
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), Map.of(), "decoded");
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(any())).thenReturn(request);
        when(request.variables(any())).thenReturn(request);
        GraphQlTransportException transportFailure = mock(GraphQlTransportException.class);
        when(request.execute()).thenReturn(Mono.error(transportFailure));

        Mono<GraphQlResult<String>> result = ReactiveGraphQlOperations.fullDocument(client).execute(operation);

        StepVerifier.create(result).verifyErrorSatisfies(e -> assertThat(e).isSameAs(transportFailure));
        // Confirms that the operation adds no automatic retry behavior: a stubbed execute()
        // observed exactly once past a failure proves nothing in this adapter re-invoked it.
        verify(request, times(1)).execute();
    }

    /**
     * Proves this adapter adds no stage (buffering, caching, retry) between the caller's
     * subscription and the transport's own {@code Mono}: cancelling downstream reaches the
     * transport's cancellation callback.
     */
    @Test
    void propagatesCancellationToTheUnderlyingExecution() {
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), Map.of(), "decoded");
        AtomicBoolean transportCancelled = new AtomicBoolean(false);
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(any())).thenReturn(request);
        when(request.variables(any())).thenReturn(request);
        when(request.execute())
                .thenReturn(Mono.<ClientGraphQlResponse>never().doOnCancel(() -> transportCancelled.set(true)));

        Mono<GraphQlResult<String>> result = ReactiveGraphQlOperations.fullDocument(client).execute(operation);

        result.subscribe().dispose();

        assertThat(transportCancelled).isTrue();
    }

    /**
     * A reactive timeout composed by the caller terminates the operation rather than hanging: this
     * adapter neither swallows nor delays the timeout signal.
     */
    @Test
    void aReactiveTimeoutTerminatesTheOperation() {
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), Map.of(), "decoded");
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(any())).thenReturn(request);
        when(request.variables(any())).thenReturn(request);
        when(request.execute()).thenReturn(Mono.never());

        Mono<GraphQlResult<String>> result =
                ReactiveGraphQlOperations.fullDocument(client).execute(operation).timeout(Duration.ofMillis(50));

        StepVerifier.create(result)
                .verifyErrorSatisfies(e -> assertThat(e).isInstanceOf(java.util.concurrent.TimeoutException.class));
    }

    @Test
    void executesASubscriptionAsDecodedResultsWithoutCollapsingGraphQlErrorsOrExtensions() {
        Map<String, Object> firstData = Map.of("thing", "first");
        Map<String, Object> secondData = firstData;
        GraphQlOperation<String> operation = fakeOperation(
                "WatchThing",
                "subscription WatchThing { thing }",
                Map.of(),
                firstData,
                "decoded-first",
                GraphQlOperationKind.SUBSCRIPTION);
        ResponseError error = mock(ResponseError.class);
        ClientGraphQlResponse first = stubResponse(true, firstData, List.of(), Map.of("sequence", 1));
        ClientGraphQlResponse second = stubResponse(true, secondData, List.of(error), Map.of("sequence", 2));
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(operation.operationName())).thenReturn(request);
        when(request.variables(operation.variables())).thenReturn(request);
        when(request.executeSubscription()).thenReturn(Flux.just(first, second));

        StepVerifier.create(ReactiveGraphQlOperations.fullDocument(client).executeSubscription(operation))
                .assertNext(result -> assertThat(result).satisfies(r -> {
                    assertThat(r.data()).isEqualTo("decoded-first");
                    assertThat(r.errors()).isEmpty();
                    assertThat(r.extensions()).containsEntry("sequence", 1);
                }))
                .assertNext(result -> assertThat(result).satisfies(r -> {
                    assertThat(r.data()).isEqualTo("decoded-first");
                    assertThat(r.errors()).containsExactly(error);
                    assertThat(r.extensions()).containsEntry("sequence", 2);
                }))
                .verifyComplete();
    }

    @Test
    void rejectsNonSubscriptionOperationsAtTheStreamingSeam() {
        GraphQlOperation<String> operation = fakeOperation(
                "GetThing", "query GetThing { thing }", Map.of(), Map.of("thing", "raw"), "decoded");

        assertThatThrownBy(() -> ReactiveGraphQlOperations.fullDocument(mock(GraphQlClient.class))
                        .executeSubscription(operation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GetThing")
                .hasMessageContaining("subscription");
    }

    @Test
    void propagatesSubscriptionTransportFailuresAsErrorSignals() {
        GraphQlOperation<String> operation = fakeOperation(
                "WatchThing",
                "subscription WatchThing { thing }",
                Map.of(),
                Map.of("thing", "raw"),
                "decoded",
                GraphQlOperationKind.SUBSCRIPTION);
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        GraphQlTransportException transportFailure = mock(GraphQlTransportException.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(operation.operationName())).thenReturn(request);
        when(request.variables(operation.variables())).thenReturn(request);
        when(request.executeSubscription()).thenReturn(Flux.error(transportFailure));

        StepVerifier.create(ReactiveGraphQlOperations.fullDocument(client).executeSubscription(operation))
                .verifyErrorSatisfies(error -> assertThat(error).isSameAs(transportFailure));
    }

    @Test
    void propagatesSubscriptionCancellationToTheUnderlyingExecution() {
        GraphQlOperation<String> operation = fakeOperation(
                "WatchThing",
                "subscription WatchThing { thing }",
                Map.of(),
                Map.of("thing", "raw"),
                "decoded",
                GraphQlOperationKind.SUBSCRIPTION);
        AtomicBoolean transportCancelled = new AtomicBoolean(false);
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(operation.operationName())).thenReturn(request);
        when(request.variables(operation.variables())).thenReturn(request);
        when(request.executeSubscription())
                .thenReturn(Flux.<ClientGraphQlResponse>never().doOnCancel(() -> transportCancelled.set(true)));

        ReactiveGraphQlOperations.fullDocument(client).executeSubscription(operation).subscribe().dispose();

        assertThat(transportCancelled).isTrue();
    }

    private static ClientGraphQlResponse stubResponse(
            boolean valid, @Nullable Object data, List<ResponseError> errors, Map<String, Object> extensions) {
        ClientGraphQlResponse response = mock(ClientGraphQlResponse.class);
        when(response.isValid()).thenReturn(valid);
        doReturn(data).when(response).getData();
        when(response.getErrors()).thenReturn(errors);
        doReturn(extensions).when(response).getExtensions();
        return response;
    }

    private static GraphQlClient clientFor(GraphQlOperation<?> operation, ClientGraphQlResponse response) {
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(operation.operationName())).thenReturn(request);
        when(request.variables(operation.variables())).thenReturn(request);
        when(request.execute()).thenReturn(Mono.just(response));
        return client;
    }

    private static GraphQlOperation<String> fakeOperation(
            String operationName,
            String document,
            Map<String, Object> variables,
            Map<String, Object> expectedData,
            String decoded) {
        return fakeOperation(operationName, document, variables, expectedData, decoded, GraphQlOperationKind.QUERY);
    }

    private static GraphQlOperation<String> fakeOperation(
            String operationName,
            String document,
            Map<String, Object> variables,
            Map<String, Object> expectedData,
            String decoded,
            GraphQlOperationKind operationKind) {
        return new GraphQlOperation<>() {
            @Override
            public String operationName() {
                return operationName;
            }

            @Override
            public String document() {
                return document;
            }

            @Override
            public String operationId() {
                return ManifestOperation.idFor(document);
            }

            @Override
            public Map<String, Object> variables() {
                return variables;
            }

            @Override
            public GraphQlOperationKind operationKind() {
                return operationKind;
            }

            @Override
            public String decode(@Nullable Object data, DecodingPolicy policy) {
                assertThat(data).isEqualTo(expectedData);
                assertThat(policy).isEqualTo(DecodingPolicy.LENIENT);
                return decoded;
            }
        };
    }
}
