package dev.glisseo.izar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.PersistedQueryExtension;
import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.GraphQlTransportException;

class SynchronousGraphQlOperationsTest {

    @Test
    void executesAndDecodesACompleteResponse() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of("id", "42"), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(operation, true, rawData, List.of(), Map.of());

        GraphQlResult<String> result =
                SynchronousGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        assertThat(result.data()).isEqualTo("decoded");
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.extensions()).isEmpty();
        assertThat(result.assertNoErrors()).isEqualTo("decoded");
    }

    @Test
    void returnsPartialDataAlongsideErrorsWhenTheResponseIsStillValid() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        ResponseError error = mock(ResponseError.class);
        when(error.getMessage()).thenReturn("nested field failed");
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(operation, true, rawData, List.of(error), Map.of("trace", "abc"));

        GraphQlResult<String> result =
                SynchronousGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        assertThat(result.data()).isEqualTo("decoded");
        assertThat(result.errors()).containsExactly(error);
        assertThat(result.extensions()).containsEntry("trace", "abc");
        assertThatThrownBy(result::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
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
        ClientGraphQlResponse response = stubResponse(operation, false, null, List.of(error), Map.of());

        GraphQlResult<String> result =
                SynchronousGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation);

        assertThat(result.data()).isNull();
        assertThat(result.errors()).containsExactly(error);
        verify(operation, never()).decode(any(), any());
        assertThatThrownBy(result::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
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
        ClientGraphQlResponse response = stubResponse(operation, true, rawData, List.of(), Map.of());

        assertThatThrownBy(() ->
                        SynchronousGraphQlOperations.fullDocument(clientFor(operation, response)).execute(operation))
                .isInstanceOf(GraphQlDecodingException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GetThing");
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
        ClientGraphQlResponse response = stubResponse(operation, true, rawData, List.of(), Map.of());

        GraphQlResult<String> result = SynchronousGraphQlOperations.fullDocument(
                        clientFor(operation, response), DecodingPolicy.STRICT)
                .execute(operation);

        assertThat(result.data()).isEqualTo("decoded");
        verify(operation, never()).decode(any(), eq(DecodingPolicy.LENIENT));
    }

    @Test
    void defaultExecutionModeNeverAddsThePersistedQueryExtension() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(operation, true, rawData, List.of(), Map.of());
        GraphQlClient client = clientFor(operation, response);

        SynchronousGraphQlOperations.fullDocument(client).execute(operation);

        GraphQlClient.RequestSpec request = client.document(operation.document());
        verify(request, never()).extension(any(), any());
    }

    @Test
    void persistedIdExecutionModeAddsTheApolloCompatibleExtensionDerivedFromTheDocument() {
        Map<String, Object> rawData = Map.of("thing", "raw");
        String document = "query GetThing { thing }";
        GraphQlOperation<String> operation = fakeOperation("GetThing", document, Map.of(), rawData, "decoded");
        ClientGraphQlResponse response = stubResponse(operation, true, rawData, List.of(), Map.of());
        GraphQlClient client = clientFor(operation, response);

        new SynchronousGraphQlOperations(client, DecodingPolicy.LENIENT, GraphQlExecutionMode.PERSISTED_ID)
                .execute(operation);

        GraphQlClient.RequestSpec request = client.document(operation.document());
        verify(request)
                .extension(
                        PersistedQueryExtension.EXTENSION_NAME,
                        PersistedQueryExtension.of(document).toExtensionValue());
    }

    @Test
    void propagatesTransportFailuresWithoutWrappingThemIntoAResult() {
        GraphQlOperation<String> operation =
                fakeOperation("GetThing", "query GetThing { thing }", Map.of(), Map.of(), "decoded");
        GraphQlClient client = mock(GraphQlClient.class);
        GraphQlClient.RequestSpec request = mock(GraphQlClient.RequestSpec.class);
        when(client.document(operation.document())).thenReturn(request);
        when(request.operationName(any())).thenReturn(request);
        when(request.variables(any())).thenReturn(request);
        GraphQlTransportException transportFailure = mock(GraphQlTransportException.class);
        when(request.executeSync()).thenThrow(transportFailure);

        assertThatThrownBy(() -> SynchronousGraphQlOperations.fullDocument(client).execute(operation))
                .isSameAs(transportFailure);
    }

    private static ClientGraphQlResponse stubResponse(
            GraphQlOperation<?> operation,
            boolean valid,
            @Nullable Object data,
            List<ResponseError> errors,
            Map<String, Object> extensions) {
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
        when(request.executeSync()).thenReturn(response);
        return client;
    }

    private static GraphQlOperation<String> fakeOperation(
            String operationName,
            String document,
            Map<String, Object> variables,
            Map<String, Object> expectedData,
            String decoded) {
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
            public String decode(@Nullable Object data, DecodingPolicy policy) {
                assertThat(data).isEqualTo(expectedData);
                assertThat(policy).isEqualTo(DecodingPolicy.LENIENT);
                return decoded;
            }
        };
    }
}
