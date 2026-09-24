package dev.glisseo.izar.client;

import dev.glisseo.izar.manifest.PersistedQueryExtension;
import dev.glisseo.izar.operation.GraphQlOperation;
import org.springframework.graphql.client.GraphQlClient;

/**
 * Adds the Apollo-compatible {@code persistedQuery} extension to a request when {@link
 * GraphQlExecutionMode#PERSISTED_ID} is selected, shared by {@link SynchronousGraphQlOperations}
 * and {@link ReactiveGraphQlOperations} so both use the same ID the same way.
 *
 * <p>The ID itself comes from {@link GraphQlOperation#operationId()}, a build-time constant a
 * generated operation already carries: this never re-hashes {@link GraphQlOperation#document()} at
 * request time.
 */
final class GraphQlRequestExtensions {

    private GraphQlRequestExtensions() {}

    static void apply(GraphQlClient.RequestSpec request, GraphQlOperation<?> operation, GraphQlExecutionMode mode) {
        if (mode == GraphQlExecutionMode.PERSISTED_ID) {
            PersistedQueryExtension extension =
                    new PersistedQueryExtension(PersistedQueryExtension.CURRENT_VERSION, operation.operationId());
            request.extension(PersistedQueryExtension.EXTENSION_NAME, extension.toExtensionValue());
        }
    }
}
