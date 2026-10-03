package dev.glisseo.izar.operation;

import java.util.Map;

/** Shared operation metadata used to send a GraphQL request. */
public interface GraphQlRequest {

    /** The GraphQL operation kind, used to select synchronous or streaming execution. */
    default GraphQlOperationKind operationKind() {
        return GraphQlOperationKind.QUERY;
    }

    /** The operation's name, as declared in its executable document. */
    String operationName();

    /** The final executable document sent on the wire. */
    String document();

    /** The lowercase hex SHA-256 digest of {@link #document()}. */
    String operationId();

    /** Variables sent alongside the document. */
    Map<String, Object> variables();
}
