package dev.glisseo.izar.operation;

import java.util.Locale;

/** The GraphQL operation kind declared by an executable operation document. */
public enum GraphQlOperationKind {
    QUERY,
    MUTATION,
    SUBSCRIPTION;

    /** The lowercase operation kind written into an Apollo-compatible manifest. */
    public String manifestType() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The suffix used for the generated Java operation class. */
    public String javaTypeSuffix() {
        return switch (this) {
            case QUERY -> "Query";
            case MUTATION -> "Mutation";
            case SUBSCRIPTION -> "Subscription";
        };
    }
}
