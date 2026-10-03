package dev.glisseo.izar.client;

import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import java.util.function.Consumer;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Executes generated operations through an application-configured, reactive {@link
 * GraphQlClient}, composing with Reactor rather than blocking for a response.
 *
 * <p>{@code GraphQlClient.RequestSpec.execute()} returns a {@code Mono<ClientGraphQlResponse>}
 * backed by the underlying transport: cancelling the returned {@link Mono}, or composing it with
 * {@code Mono#timeout}, propagates to that transport exactly as it would for any other reactive
 * Spring GraphQL call. This class adds no buffering, caching, or retry stage that would change
 * that propagation.
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
 * #persistedQuery(WebClient)} builds the client and pairs it with {@link
 * GraphQlExecutionMode#PERSISTED_ID} in the same call, so the transport and the mode can't drift
 * apart. The general constructor stays available for a caller that already holds a {@code
 * GraphQlClient} built over a persisted-ID transport by some other means and needs to pair it with
 * {@link GraphQlExecutionMode#PERSISTED_ID} explicitly.
 */
public final class ReactiveGraphQlOperations {

    private final GraphQlClient client;
    private final DecodingPolicy decodingPolicy;
    private final GraphQlExecutionMode executionMode;

    public ReactiveGraphQlOperations(
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
    public static ReactiveGraphQlOperations fullDocument(GraphQlClient client) {
        return fullDocument(client, DecodingPolicy.LENIENT);
    }

    /** As {@link #fullDocument(GraphQlClient)}, with an explicit {@code decodingPolicy}. */
    public static ReactiveGraphQlOperations fullDocument(GraphQlClient client, DecodingPolicy decodingPolicy) {
        return new ReactiveGraphQlOperations(client, decodingPolicy, GraphQlExecutionMode.FULL_DOCUMENT);
    }

    /**
     * Builds persisted-ID execution over an application-supplied {@code WebClient}. This factory
     * wraps {@code webClient} in the transport that never puts the document on the wire and sets {@link
     * GraphQlExecutionMode#PERSISTED_ID} to match, in one call rather than two that would
     * otherwise have to agree by hand.
     *
     * @param webClient scoped to the target endpoint's URL, as {@link GraphQlExecutionMode#FULL_DOCUMENT}
     *     mode would use; carries whatever auth, headers, or filters the
     *     application needs
     */
    public static ReactiveGraphQlOperations persistedQuery(WebClient webClient) {
        return persistedQuery(webClient, DecodingPolicy.LENIENT);
    }

    /** As {@link #persistedQuery(WebClient)}, with an explicit {@code decodingPolicy}. */
    public static ReactiveGraphQlOperations persistedQuery(WebClient webClient, DecodingPolicy decodingPolicy) {
        return persistedQuery(webClient, decodingPolicy, builder -> {});
    }

    /**
     * As {@link #persistedQuery(WebClient, DecodingPolicy)}, applying {@code customizer} to the
     * underlying {@link GraphQlClient.Builder} before it builds the client — for a caller that
     * needs a {@code GraphQlClientInterceptor}, a {@code documentSource}, or a {@code
     * blockingTimeout} on persisted-ID execution. {@code customizer} can configure the builder but
     * not replace the transport, so the pairing with {@link GraphQlExecutionMode#PERSISTED_ID}
     * can't drift apart the way it could by calling {@link PersistedIdGraphQlClient#reactiveBuilder}
     * and the general constructor separately.
     */
    public static ReactiveGraphQlOperations persistedQuery(
            WebClient webClient, DecodingPolicy decodingPolicy, Consumer<GraphQlClient.Builder<?>> customizer) {
        GraphQlClient.Builder<?> builder = PersistedIdGraphQlClient.reactiveBuilder(webClient);
        customizer.accept(builder);
        return new ReactiveGraphQlOperations(builder.build(), decodingPolicy, GraphQlExecutionMode.PERSISTED_ID);
    }

    /**
     * Sends {@code operation}'s document and returns a {@link Mono} that emits its decoded result.
     *
     * <p>A response with GraphQL errors, partial data, or no data at all is still emitted as a
     * {@link GraphQlResult} rather than propagated as an error signal: call {@link
     * GraphQlResult#assertNoErrors()} for fail-fast behavior. A transport failure (connection
     * refused, non-2xx status, timeout) is not represented as a result at all; Spring's
     * {@code GraphQlClientException} terminates the {@link Mono} with an error signal unchanged,
     * so it stays distinguishable from a GraphQL execution error.
     *
     * <p>The returned {@link Mono} does not execute the operation until subscribed, matching
     * {@code GraphQlClient.RequestSpec.execute()}'s own deferred semantics.
     *
     * @throws GraphQlDecodingException as an error signal, not a thrown exception, if the response
     *     has usable data but {@code operation} cannot decode it
     */
    public <TResponse> Mono<GraphQlResult<TResponse>> execute(GraphQlOperation<TResponse> operation) {
        GraphQlClient.RequestSpec request = client.document(operation.document())
                .operationName(operation.operationName())
                .variables(operation.variables());
        GraphQlRequestExtensions.apply(request, operation, executionMode);

        return request.execute().map(response -> GraphQlResponseMapping.toResult(operation, response, decodingPolicy));
    }

    /**
     * Executes a subscription and maps each pushed GraphQL response independently.
     *
     * <p>GraphQL errors and extensions stay on the emitted {@link GraphQlResult}; connection,
     * HTTP, decoding, and other transport failures terminate the returned {@link Flux}. Completion
     * and cancellation are propagated by Spring GraphQL's configured subscription transport, and
     * downstream demand is passed through without buffering or retrying events.
     *
     * @throws IllegalArgumentException if {@code operation} is not a subscription
     */
    public <TResponse> Flux<GraphQlResult<TResponse>> executeSubscription(
            GraphQlOperation<TResponse> operation) {
        requireSubscription(operation);
        GraphQlClient.RequestSpec request = client.document(operation.document())
                .operationName(operation.operationName())
                .variables(operation.variables());
        GraphQlRequestExtensions.apply(request, operation, executionMode);

        return request.executeSubscription()
                .map(response -> GraphQlResponseMapping.toResult(operation, response, decodingPolicy));
    }

    private static void requireSubscription(GraphQlOperation<?> operation) {
        if (operation.operationKind() != GraphQlOperationKind.SUBSCRIPTION) {
            throw new IllegalArgumentException(
                    "Operation '" + operation.operationName() + "' is not a subscription.");
        }
    }
}
