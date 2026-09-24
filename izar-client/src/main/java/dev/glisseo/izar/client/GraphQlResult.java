package dev.glisseo.izar.client;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.ResponseError;

/**
 * The outcome of executing one operation: decoded data (if any), the GraphQL errors reported
 * alongside it, and the response's extensions, kept together rather than collapsed into a single
 * success-or-failure outcome.
 *
 * <p>{@code data} is {@code null} exactly when the response carried no usable {@code data} value:
 * a request-level failure, or a field error that propagated all the way to the response root
 * because no ancestor field was nullable. A {@code null} caused by error propagation into a
 * nullable ancestor field is not this case: it appears inside {@code data} as an ordinary nullable
 * value, alongside the error that explains it.
 *
 * @param <TResponse> the operation's decoded response type
 * @param operationName the operation this result belongs to, used in {@link #assertNoErrors()}'s
 *     failure message
 * @param data the decoded response, or {@code null} if the response carried no data
 * @param errors the GraphQL errors reported alongside {@code data}, empty if there were none
 * @param extensions the response's {@code extensions} map, empty if it had none
 */
public record GraphQlResult<TResponse>(
        String operationName,
        @Nullable TResponse data,
        List<ResponseError> errors,
        Map<String, Object> extensions) {

    public GraphQlResult {
        errors = List.copyOf(errors);
        extensions = Map.copyOf(extensions);
    }

    /** {@code true} if the response carried at least one GraphQL error. */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns {@code data}, failing first if the response carried any GraphQL error or no data at
     * all.
     *
     * <p>A caller that wants a clean response only, and treats any error as fatal, uses this
     * instead of inspecting {@link #errors()} and {@link #data()} directly. Partial data is never
     * returned silently: a response with both data and errors still fails here.
     *
     * @return the non-null decoded response
     * @throws GraphQlOperationException if {@link #hasErrors()} is {@code true}, or if {@link
     *     #data()} is {@code null}
     */
    public TResponse assertNoErrors() {
        if (hasErrors() || data == null) {
            throw new GraphQlOperationException(operationName, errors);
        }
        return data;
    }
}
