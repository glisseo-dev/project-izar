package dev.glisseo.izar.compiler;

import java.util.List;

/**
 * Thrown when a schema or operation file cannot be generated: invalid GraphQL, or a feature
 * outside this issue's supported subset. Carries every diagnostic found, not just the first, so
 * one build reports every problem at once.
 */
public final class OperationGenerationException extends RuntimeException {

    private final List<String> diagnostics;

    public OperationGenerationException(String diagnostic) {
        this(List.of(diagnostic));
    }

    public OperationGenerationException(List<String> diagnostics) {
        super(formatMessage(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    /** Every diagnostic message collected, in the order they were found. */
    public List<String> diagnostics() {
        return diagnostics;
    }

    private static String formatMessage(List<String> diagnostics) {
        if (diagnostics.isEmpty()) {
            throw new IllegalArgumentException("At least one diagnostic is required.");
        }
        if (diagnostics.size() == 1) {
            return diagnostics.get(0);
        }
        StringBuilder message = new StringBuilder().append(diagnostics.size()).append(" problems found:");
        for (String diagnostic : diagnostics) {
            message.append("\n  - ").append(diagnostic);
        }
        return message.toString();
    }
}
