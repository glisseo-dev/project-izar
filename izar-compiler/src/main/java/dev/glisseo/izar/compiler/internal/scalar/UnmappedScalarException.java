package dev.glisseo.izar.compiler.internal.scalar;

/**
 * Thrown by {@link ScalarTypeResolver} for a custom scalar with no configured {@code
 * dev.glisseo.izar.compiler.ScalarMapping}. Carries only the bare scalar name: {@code
 * dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer} and {@code
 * dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer} each catch this and add their own
 * field- or variable-specific context (and the operation file) to the diagnostic they report.
 */
public final class UnmappedScalarException extends RuntimeException {

    UnmappedScalarException(String graphqlScalarName) {
        super(graphqlScalarName);
    }

    public String graphqlScalarName() {
        return getMessage();
    }
}
