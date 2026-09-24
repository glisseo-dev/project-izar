package dev.glisseo.izar.operation;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The contract a generated operation implements.
 *
 * <p>{@code document()} is the exact executable document sent on the wire. {@code decode}
 * receives the raw {@code data} tree of a GraphQL response ({@code Map<String, Object>}, nested
 * {@code List}s, and scalar values, or {@code null}) and turns it into the operation's immutable
 * response type.
 *
 * @param <TResponse> the operation's decoded response type
 */
public interface GraphQlOperation<TResponse> {

    /** The GraphQL operation kind, used to select synchronous or streaming execution. */
    default GraphQlOperationKind operationKind() {
        return GraphQlOperationKind.QUERY;
    }

    /** The operation's name, as declared in its executable document. */
    String operationName();

    /** The final executable document sent on the wire. */
    String document();

    /**
     * This operation's persisted-query ID: the lowercase hex SHA-256 digest of {@link #document()}.
     *
     * <p>A generated operation carries this as a build-time constant, derived from the exact same
     * document text a manifest entry hashes, so a client never has to re-derive it (or risk
     * disagreeing with the manifest) by hashing {@link #document()} itself at request time.
     */
    String operationId();

    /** Variables sent alongside the document. Empty until issue 02 adds variable support. */
    Map<String, Object> variables();

    /**
     * Decodes a response's raw {@code data} value into this operation's response type, honoring
     * {@code policy} wherever the operation selects an output enum or a polymorphic (interface or
     * union) type.
     */
    TResponse decode(@Nullable Object data, DecodingPolicy policy);

    /** {@link #decode(Object, DecodingPolicy)} with {@link DecodingPolicy#LENIENT}. */
    default TResponse decode(@Nullable Object data) {
        return decode(data, DecodingPolicy.LENIENT);
    }
}
