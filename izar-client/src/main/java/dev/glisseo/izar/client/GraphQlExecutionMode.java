package dev.glisseo.izar.client;

/**
 * How {@link SynchronousGraphQlOperations} and {@link ReactiveGraphQlOperations} identify an
 * operation to the endpoint they execute against.
 */
public enum GraphQlExecutionMode {

    /**
     * Send only the operation's document, the way any ordinary GraphQL client does. The default,
     * so an application gets full-document execution without opting into anything.
     */
    FULL_DOCUMENT,

    /**
     * Send only the operation's Apollo-compatible {@code persistedQuery} extension ({@code
     * version} and {@code sha256Hash}, derived the same way manifest generation derives an
     * operation's ID) plus its variables and operation name: never the document itself.
     *
     * <p>{@link PersistedIdGraphQlClient} builds this mode's client over a {@code GraphQlTransport}
     * that never reads the request's document text, so the operation's real document never reaches
     * the wire. A compatible server endpoint resolves it from its own registry by ID alone. The
     * request goes to the same URL as {@link #FULL_DOCUMENT} mode's, so one standard {@code
     * /graphql} endpoint can accept both shapes, via {@code graphql-java}'s own {@code
     * PreparsedDocumentProvider} extension point rather than a dedicated endpoint.
     */
    PERSISTED_ID
}
