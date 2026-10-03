package dev.glisseo.izar.compiler.internal.generate;

import dev.glisseo.izar.compiler.internal.naming.JavaLiterals;
import dev.glisseo.izar.compiler.internal.scalar.CustomScalarType;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import java.util.List;

/** Renders request members shared by both generated operation modes. */
final class SourceGeneratorSupport {

    private SourceGeneratorSupport() {}

    static void appendOperationConstants(
            StringBuilder out,
            String operationName,
            String documentText,
            String operationId,
            GraphQlOperationKind operationKind) {
        out.append("    public static final String OPERATION_NAME = ")
                .append(JavaLiterals.quote(operationName)).append(";\n\n");
        out.append("    public static final String DOCUMENT = ")
                .append(JavaLiterals.quote(documentText)).append(";\n\n");
        out.append("    public static final String OPERATION_ID = ")
                .append(JavaLiterals.quote(operationId)).append(";\n\n");
        out.append("    public static final GraphQlOperationKind OPERATION_KIND = GraphQlOperationKind.")
                .append(operationKind.name()).append(";\n\n");
    }

    static void appendOperationMethods(StringBuilder out, boolean hasVariables) {
        out.append("    @Override\n    public String operationName() {\n        return OPERATION_NAME;\n    }\n\n");
        out.append("    @Override\n    public String document() {\n        return DOCUMENT;\n    }\n\n");
        out.append("    @Override\n    public String operationId() {\n        return OPERATION_ID;\n    }\n\n");
        out.append("    @Override\n    public GraphQlOperationKind operationKind() {\n        return OPERATION_KIND;\n    }\n\n");
        if (hasVariables) {
            out.append("    @Override\n    public Map<String, Object> variables() {\n        return this.variables;\n    }\n\n");
        } else {
            out.append("    @Override\n    public Map<String, Object> variables() {\n        return Map.of();\n    }\n\n");
        }
    }

    static void appendScalarCodecFields(StringBuilder out, List<CustomScalarType> customScalars) {
        for (CustomScalarType scalar : customScalars) {
            out.append("    private static final ScalarCodec<").append(scalar.javaTypeName()).append("> ")
                    .append(scalar.codecFieldName()).append(" = new ").append(scalar.codecClassName()).append("();\n\n");
        }
    }
}
