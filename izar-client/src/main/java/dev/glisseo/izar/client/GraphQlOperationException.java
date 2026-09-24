package dev.glisseo.izar.client;

import java.util.List;
import org.springframework.graphql.ResponseError;

/**
 * Thrown by {@link GraphQlResult#assertNoErrors()} when a response carried GraphQL errors, or no
 * data at all: the caller chose not to tolerate either, rather than inspecting {@link
 * GraphQlResult} directly.
 */
public final class GraphQlOperationException extends RuntimeException {

    private final List<ResponseError> errors;

    public GraphQlOperationException(String operationName, List<ResponseError> errors) {
        super(formatMessage(operationName, errors));
        this.errors = List.copyOf(errors);
    }

    /** The GraphQL errors reported in the response, if any. */
    public List<ResponseError> errors() {
        return errors;
    }

    private static String formatMessage(String operationName, List<ResponseError> errors) {
        StringBuilder message =
                new StringBuilder("Operation '").append(operationName).append("' failed");
        if (errors.isEmpty()) {
            return message.append(" with no data.").toString();
        }
        message.append(":");
        for (ResponseError error : errors) {
            message.append("\n  - ").append(error.getMessage());
        }
        return message.toString();
    }
}
