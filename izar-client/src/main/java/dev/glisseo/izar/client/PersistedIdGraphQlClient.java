package dev.glisseo.izar.client;

import org.springframework.graphql.client.GraphQlClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Builds a {@link GraphQlClient} for {@link GraphQlExecutionMode#PERSISTED_ID} that genuinely
 * sends no document: unlike {@link GraphQlClient#document(String)}'s ordinary entry point, which
 * every other execution mode uses and which requires real document text, a client built here talks
 * over {@link RestClientPersistedIdTransport} or {@link WebClientPersistedIdTransport}, whose
 * {@code execute} never reads a request's document at all.
 *
 * <p>{@code restClient}/{@code webClient} must already be scoped to the same URL {@link
 * GraphQlExecutionMode#FULL_DOCUMENT} mode uses: a compatible server accepts both shapes on its
 * standard endpoint (see ADR 0010), so persisted-ID execution needs no URL of its own.
 *
 * <p>This is the low-level building block behind {@link SynchronousGraphQlOperations#persistedQuery}
 * and {@link ReactiveGraphQlOperations#persistedQuery}, which most callers should use instead: those
 * factories return the finished operations adapter with the transport and {@link
 * GraphQlExecutionMode#PERSISTED_ID} paired automatically, including their own {@code Consumer}
 * overload for adding a {@code GraphQlClientInterceptor}, a {@code documentSource}, or a {@code
 * blockingTimeout} without that pairing at risk. Use this class directly only when a caller wants
 * the raw {@link GraphQlClient} itself, decoupled from Izar's operations adapters — pairing the
 * result with {@link GraphQlExecutionMode#PERSISTED_ID} is then the caller's own responsibility.
 * {@link #sync(RestClient)}/{@link #reactive(WebClient)} build and return the client directly;
 * {@link #syncBuilder(RestClient)}/{@link #reactiveBuilder(WebClient)} return the {@link
 * GraphQlClient.Builder} before {@code build()} for the same kind of customization.
 */
public final class PersistedIdGraphQlClient {

    private PersistedIdGraphQlClient() {}

    public static GraphQlClient sync(RestClient restClient) {
        return syncBuilder(restClient).build();
    }

    public static GraphQlClient reactive(WebClient webClient) {
        return reactiveBuilder(webClient).build();
    }

    /** As {@link #sync(RestClient)}, returning the builder before {@code build()} is called. */
    public static GraphQlClient.Builder<?> syncBuilder(RestClient restClient) {
        return GraphQlClient.builder(new RestClientPersistedIdTransport(restClient));
    }

    /** As {@link #reactive(WebClient)}, returning the builder before {@code build()} is called. */
    public static GraphQlClient.Builder<?> reactiveBuilder(WebClient webClient) {
        return GraphQlClient.builder(new WebClientPersistedIdTransport(webClient));
    }
}
