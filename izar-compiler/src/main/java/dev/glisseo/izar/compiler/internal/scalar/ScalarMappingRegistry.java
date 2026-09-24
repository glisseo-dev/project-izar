package dev.glisseo.izar.compiler.internal.scalar;

import dev.glisseo.izar.compiler.OperationGenerationException;
import dev.glisseo.izar.compiler.ScalarMapping;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The custom scalar mappings configured for one generation run, keyed by GraphQL scalar name.
 * Built once from the caller-supplied {@link ScalarMapping} list and consulted by {@code
 * dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer} and {@code
 * dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer} whenever a schema scalar is not one of
 * {@link BuiltinScalar}'s five.
 */
public final class ScalarMappingRegistry {

    public static final ScalarMappingRegistry EMPTY = new ScalarMappingRegistry(Map.of());

    private final Map<String, ScalarMapping> byGraphqlName;

    private ScalarMappingRegistry(Map<String, ScalarMapping> byGraphqlName) {
        this.byGraphqlName = byGraphqlName;
    }

    /**
     * @throws OperationGenerationException if two mappings configure the same GraphQL scalar
     *     name: an invalid configuration this class rejects before it could silently pick one,
     *     reported the same way as any other generation failure rather than a raw runtime
     *     exception a build tool would print unhelpfully.
     */
    public static ScalarMappingRegistry of(List<ScalarMapping> mappings) {
        Map<String, ScalarMapping> byGraphqlName = new LinkedHashMap<>();
        for (ScalarMapping mapping : mappings) {
            ScalarMapping existing = byGraphqlName.putIfAbsent(mapping.graphqlScalarName(), mapping);
            if (existing != null) {
                throw new OperationGenerationException(
                        "Duplicate scalar mapping configured for GraphQL scalar '"
                                + mapping.graphqlScalarName()
                                + "'.");
            }
        }
        return new ScalarMappingRegistry(Map.copyOf(byGraphqlName));
    }

    @Nullable ScalarMapping forName(String graphqlScalarName) {
        return byGraphqlName.get(graphqlScalarName);
    }
}
