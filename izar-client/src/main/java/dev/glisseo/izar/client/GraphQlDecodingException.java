package dev.glisseo.izar.client;

/**
 * Thrown when a response's {@code data} is present, but does not match the shape an operation's
 * generated {@code decode} expects: a malformed response, or a field value incompatible with the
 * schema-derived Java type.
 *
 * <p>Distinct from {@link GraphQlOperationException}, which reports a well-formed response that
 * simply disagreed with the operation (GraphQL errors, or missing data): this exception means the
 * transport and server both behaved, but the payload itself could not be decoded.
 */
public final class GraphQlDecodingException extends RuntimeException {

    public GraphQlDecodingException(String operationName, Throwable cause) {
        super(
                "Operation '" + operationName + "' returned data that could not be decoded: " + cause.getMessage(),
                cause);
    }
}
