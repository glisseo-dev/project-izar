package dev.glisseo.izar.client;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.client.GraphQlClient;

/**
 * The wire body a genuinely hash-only persisted-ID request sends: {@code operationName}, {@code
 * variables}, and {@code extensions} (carrying the {@code persistedQuery} extension), with no
 * {@code query} key at all.
 *
 * <p>{@code request.getDocument()} is deliberately never read here. {@link GraphQlClient} requires
 * every {@code RequestSpec} to start from {@link GraphQlClient#document(String)}, so {@code
 * request.getDocument()} always holds the real operation text by the time a transport sees it, but
 * a persisted-ID transport's entire purpose is to avoid ever putting that text on the wire: the
 * server resolves the real document itself, from the registry, by ID alone.
 */
final class PersistedIdRequestBody {

    private PersistedIdRequestBody() {}

    static Map<String, Object> of(GraphQlRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.getOperationName() != null) {
            body.put("operationName", request.getOperationName());
        }
        if (!request.getVariables().isEmpty()) {
            body.put("variables", request.getVariables());
        }
        body.put("extensions", request.getExtensions());
        return body;
    }
}
