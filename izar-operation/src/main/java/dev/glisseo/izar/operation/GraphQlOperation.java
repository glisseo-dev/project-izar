package dev.glisseo.izar.operation;

import org.jspecify.annotations.Nullable;

/**
 * The contract a generated Izar operation implements.
 *
 * <p>{@code document()} is the exact executable document sent on the wire. {@code decode}
 * receives the raw {@code data} tree of a GraphQL response ({@code Map<String, Object>}, nested
 * {@code List}s, and scalar values, or {@code null}) and turns it into the operation's immutable
 * response type.
 *
 * @param <TResponse> the operation's decoded response type
 */
public interface GraphQlOperation<TResponse> extends GraphQlRequest {

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
