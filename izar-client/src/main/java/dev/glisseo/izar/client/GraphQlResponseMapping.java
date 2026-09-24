package dev.glisseo.izar.client;

import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import java.util.Map;
import org.springframework.graphql.client.ClientGraphQlResponse;

/**
 * Turns one {@link ClientGraphQlResponse} into a {@link GraphQlResult}, shared by {@link
 * SynchronousGraphQlOperations} and {@link ReactiveGraphQlOperations}: whichever adapter executes
 * an operation, an invalid response skips decoding, a decoding failure becomes a {@link
 * GraphQlDecodingException}, and both apply the same {@link DecodingPolicy}.
 */
final class GraphQlResponseMapping {

    private GraphQlResponseMapping() {}

    static <TResponse> GraphQlResult<TResponse> toResult(
            GraphQlOperation<TResponse> operation, ClientGraphQlResponse response, DecodingPolicy decodingPolicy) {
        TResponse data = response.isValid() ? decode(operation, response, decodingPolicy) : null;
        return new GraphQlResult<>(
                operation.operationName(), data, response.getErrors(), extensions(response));
    }

    private static <TResponse> TResponse decode(
            GraphQlOperation<TResponse> operation, ClientGraphQlResponse response, DecodingPolicy decodingPolicy) {
        try {
            return operation.decode(response.getData(), decodingPolicy);
        } catch (IllegalArgumentException e) {
            throw new GraphQlDecodingException(operation.operationName(), e);
        }
    }

    // GraphQlResponse#getExtensions() is declared Map<Object, Object>, but every implementation
    // builds it straight from a decoded JSON object, whose keys are always String.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extensions(ClientGraphQlResponse response) {
        return (Map<String, Object>) (Map<?, ?>) response.getExtensions();
    }
}
