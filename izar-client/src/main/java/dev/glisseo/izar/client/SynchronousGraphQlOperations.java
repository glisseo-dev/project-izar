package dev.glisseo.izar.client;

import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import java.util.function.Consumer;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.web.client.RestClient;

/**
 * Executes generated operations through an application-configured, blocking {@link GraphQlClient}.
 *
 * <p>{@code GraphQlClient.RequestSpec.executeSync()} blocks and returns the response directly, so
 * calling this class never touches {@code Mono}/{@code Flux}: a synchronous Spring service method
 * stays synchronous end to end.
 *
 * <p>{@code decodingPolicy} governs how every operation executed through this instance handles an
 * unfamiliar output enum value or polymorphic concrete type: it is supplied once, at construction,
 * so one client's choice never affects another's, even when both execute the same generated
 * operation. {@code executionMode} governs the same way whether every request also carries the
 * {@code persistedQuery} extension.
 *
 * <p>{@code executionMode} only controls that extension, never what the underlying {@code
 * GraphQlClient} puts on the wire: a full document reaches the wire unless {@code client} was
 * itself built over a transport that omits it (see {@link PersistedIdGraphQlClient}). Build an
 * instance from one of the two named factories below, matching how {@code client} was itself
 * built, rather than the general constructor: {@link #fullDocument(GraphQlClient)} wraps a
 * {@code GraphQlClient} the application already built however it likes, and {@link
 * #persistedQuery(RestClient)} builds the client and pairs it with {@link
 * GraphQlExecutionMode#PERSISTED_ID} in the same call, so the transport and the mode can't drift
 * apart. The general constructor stays available for a caller that already holds a {@code
 * GraphQlClient} built over a persisted-ID transport by some other means and needs to pair it with
 * {@link GraphQlExecutionMode#PERSISTED_ID} explicitly.
 */
public final class SynchronousGraphQlOperations {

    private final GraphQlClient client;
    private final DecodingPolicy decodingPolicy;
    private final GraphQlExecutionMode executionMode;

    public SynchronousGraphQlOperations(
            GraphQlClient client, DecodingPolicy decodingPolicy, GraphQlExecutionMode executionMode) {
        this.client = client;
        this.decodingPolicy = decodingPolicy;
        this.executionMode = executionMode;
    }

    /**
     * Wraps an application-built {@code GraphQlClient} for full-document execution: every request
     * carries {@code operation}'s document as-is, with no {@code persistedQuery} extension. Use
     * this for any {@code GraphQlClient} the application already built itself, however it built
     * it; it places no requirement on the underlying transport.
     */
    public static SynchronousGraphQlOperations fullDocument(GraphQlClient client) {
        return fullDocument(client, DecodingPolicy.LENIENT);
    }

    /** As {@link #fullDocument(GraphQlClient)}, with an explicit {@code decodingPolicy}. */
    public static SynchronousGraphQlOperations fullDocument(GraphQlClient client, DecodingPolicy decodingPolicy) {
        return new SynchronousGraphQlOperations(client, decodingPolicy, GraphQlExecutionMode.FULL_DOCUMENT);
    }

    /**
     * Builds persisted-ID execution over an application-supplied {@code RestClient}. This factory
     * wraps {@code restClient} in the transport that never puts the document on the wire and sets {@link
     * GraphQlExecutionMode#PERSISTED_ID} to match, in one call rather than two that would
     * otherwise have to agree by hand.
     *
     * @param restClient scoped to the target endpoint's URL, as {@link GraphQlExecutionMode#FULL_DOCUMENT}
     *     mode would use (see ADR 0010); carries whatever auth, headers, or interceptors the
     *     application needs
     */
    public static SynchronousGraphQlOperations persistedQuery(RestClient restClient) {
        return persistedQuery(restClient, DecodingPolicy.LENIENT);
    }

    /** As {@link #persistedQuery(RestClient)}, with an explicit {@code decodingPolicy}. */
    public static SynchronousGraphQlOperations persistedQuery(RestClient restClient, DecodingPolicy decodingPolicy) {
        return persistedQuery(restClient, decodingPolicy, builder -> {});
    }

    /**
     * As {@link #persistedQuery(RestClient, DecodingPolicy)}, applying {@code customizer} to the
     * underlying {@link GraphQlClient.Builder} before it builds the client — for a caller that
     * needs a {@code GraphQlClientInterceptor}, a {@code documentSource}, or a {@code
     * blockingTimeout} on persisted-ID execution. {@code customizer} can configure the builder but
     * not replace the transport, so the pairing with {@link GraphQlExecutionMode#PERSISTED_ID}
     * can't drift apart the way it could by calling {@link PersistedIdGraphQlClient#syncBuilder}
     * and the general constructor separately.
     */
    public static SynchronousGraphQlOperations persistedQuery(
            RestClient restClient, DecodingPolicy decodingPolicy, Consumer<GraphQlClient.Builder<?>> customizer) {
        GraphQlClient.Builder<?> builder = PersistedIdGraphQlClient.syncBuilder(restClient);
        customizer.accept(builder);
        return new SynchronousGraphQlOperations(builder.build(), decodingPolicy, GraphQlExecutionMode.PERSISTED_ID);
    }

    /**
     * Sends {@code operation}'s document, blocks for the response, and returns its result.
     *
     * <p>A response with GraphQL errors, partial data, or no data at all is still returned as a
     * {@link GraphQlResult} rather than thrown: call {@link GraphQlResult#assertNoErrors()} for
     * fail-fast behavior. A transport failure (connection refused, non-2xx status, timeout) is not
     * represented as a result at all; Spring's {@code GraphQlClientException} propagates
     * unchanged, so it stays distinguishable from a GraphQL execution error.
     *
     * @throws GraphQlDecodingException if the response has usable data but {@code operation}
     *     cannot decode it
     */
    public <TResponse> GraphQlResult<TResponse> execute(GraphQlOperation<TResponse> operation) {
        if (operation.operationKind() == GraphQlOperationKind.SUBSCRIPTION) {
            throw new IllegalArgumentException(
                    "Operation '" + operation.operationName() + "' is a subscription; use reactive streaming execution.");
        }
        GraphQlClient.RequestSpec request = client.document(operation.document())
                .operationName(operation.operationName())
                .variables(operation.variables());
        GraphQlRequestExtensions.apply(request, operation, executionMode);

        ClientGraphQlResponse response = request.executeSync();
        return GraphQlResponseMapping.toResult(operation, response, decodingPolicy);
    }
}
