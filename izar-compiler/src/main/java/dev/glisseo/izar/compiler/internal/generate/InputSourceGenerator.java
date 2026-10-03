package dev.glisseo.izar.compiler.internal.generate;

import dev.glisseo.izar.compiler.internal.naming.JavaIdentifiers;
import dev.glisseo.izar.compiler.internal.naming.JavaLiterals;
import dev.glisseo.izar.compiler.internal.scalar.BuiltinScalar;
import dev.glisseo.izar.compiler.internal.scalar.CustomScalarType;
import dev.glisseo.izar.compiler.internal.scalar.ScalarType;
import dev.glisseo.izar.compiler.internal.variable.InputEnumType;
import dev.glisseo.izar.compiler.internal.variable.InputFieldDefinition;
import dev.glisseo.izar.compiler.internal.variable.InputObjectType;
import dev.glisseo.izar.compiler.internal.variable.InputShape;
import dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the input side of a generated operation: enum types, input-object types with their
 * builders, and the operation class's own variable-holding builder.
 *
 * <p>An untouched builder property is omitted from the encoded map so the server applies the
 * operation's or schema's own default. Explicitly setting a nullable property to {@code null}
 * encodes {@code null}. A non-null property with neither a supplied value nor a usable default
 * fails at {@code build()}, before any request is sent. List-typed properties are defensively
 * copied on the way in (tolerating {@code null} elements) and re-encoded element by element on
 * the way out, so a caller mutating a source list afterward cannot change a built value.
 */
final class InputSourceGenerator {

    private InputSourceGenerator() {}

    static List<CustomScalarType> collectCustomScalars(VariableAnalyzer.Result variables) {
        Map<String, CustomScalarType> byGraphqlName = new LinkedHashMap<>();
        for (InputFieldDefinition variable : variables.variables()) {
            collectFromInputShape(variable.shape(), byGraphqlName);
        }
        for (InputObjectType inputObjectType : variables.inputObjectTypes()) {
            for (InputFieldDefinition field : inputObjectType.fields()) {
                collectFromInputShape(field.shape(), byGraphqlName);
            }
        }
        return List.copyOf(byGraphqlName.values());
    }

    private static void collectFromInputShape(InputShape shape, Map<String, CustomScalarType> byGraphqlName) {
        switch (shape) {
            case InputShape.ScalarShape scalar -> {
                if (scalar.scalar() instanceof CustomScalarType custom) {
                    byGraphqlName.putIfAbsent(custom.graphqlName(), custom);
                }
            }
            case InputShape.ListShape list -> collectFromInputShape(list.elementShape(), byGraphqlName);
            case InputShape.EnumShape ignored -> {}
            case InputShape.InputObjectShape ignored -> {}
        }
    }

    static void appendEnumType(StringBuilder out, InputEnumType type) {
        out.append("    public enum ").append(type.javaTypeName()).append(" {\n");
        List<String> values = type.values();
        for (int i = 0; i < values.size(); i++) {
            out.append("        ").append(values.get(i));
            out.append(i < values.size() - 1 ? ",\n" : "\n");
        }
        out.append("    }\n\n");
    }

    static void appendInputObjectType(StringBuilder out, InputObjectType type) {
        List<InputFieldDefinition> fields = type.fields();
        String javaTypeName = type.javaTypeName();

        out.append("    /** Generated input builder for GraphQL input type '")
                .append(javaTypeName)
                .append("'. Do not edit. */\n");
        out.append("    public static final class ").append(javaTypeName).append(" {\n\n");

        for (InputFieldDefinition field : fields) {
            out.append("        private final ")
                    .append(nullablePrefixedType(field.shape()))
                    .append(' ')
                    .append(fieldIdentifier(field))
                    .append(";\n");
        }
        out.append("        private final Map<String, Object> encoded;\n\n");

        out.append("        private ").append(javaTypeName).append('(');
        for (InputFieldDefinition field : fields) {
            out.append(nullablePrefixedType(field.shape())).append(' ').append(fieldIdentifier(field)).append(", ");
        }
        out.append("Map<String, Object> encoded) {\n");
        for (InputFieldDefinition field : fields) {
            String ident = fieldIdentifier(field);
            out.append("            this.").append(ident).append(" = ").append(ident).append(";\n");
        }
        out.append("            this.encoded = encoded;\n");
        out.append("        }\n\n");

        for (InputFieldDefinition field : fields) {
            String ident = fieldIdentifier(field);
            out.append("        public ")
                    .append(nullablePrefixedType(field.shape()))
                    .append(' ')
                    .append(ident)
                    .append("() {\n            return this.")
                    .append(ident)
                    .append(";\n        }\n\n");
        }

        out.append("        Map<String, Object> encode() {\n            return this.encoded;\n        }\n\n");

        out.append("        public static Builder builder() {\n            return new Builder();\n        }\n\n");

        appendBuilder(out, javaTypeName, fields, javaTypeName, "        ", true);

        out.append("    }\n\n");
    }

    /**
     * Appends the operation class's own variable-holding members: the {@code variables} field, a
     * private constructor, the {@code builder()} factory, and the {@code Builder} itself. Called
     * only when the operation declares at least one variable; a zero-variable operation keeps its
     * original no-arg-constructible shape.
     */
    static void appendOperationVariableMembers(
            StringBuilder out, String operationClassName, List<InputFieldDefinition> variables) {
        out.append("    private final Map<String, Object> variables;\n\n");
        out.append("    private ")
                .append(operationClassName)
                .append("(Map<String, Object> variables) {\n        this.variables = variables;\n    }\n\n");
        out.append("    public static Builder builder() {\n        return new Builder();\n    }\n\n");

        appendBuilder(out, operationClassName, variables, "variables", "    ", false);
    }

    /**
     * @param constructorTakesFields {@code true} for an {@code InputObjectType}, whose
     *     constructor stores each raw field alongside the encoded map so its own accessors can
     *     read them back; {@code false} for the operation class itself, whose constructor stores
     *     only the encoded {@code variables} map, since a generated operation exposes no
     *     variable accessors.
     */
    private static void appendBuilder(
            StringBuilder out,
            String builtTypeName,
            List<InputFieldDefinition> fields,
            String errorContext,
            String indent,
            boolean constructorTakesFields) {
        out.append(indent).append("public static final class Builder {\n");
        for (InputFieldDefinition field : fields) {
            String ident = fieldIdentifier(field);
            out.append(indent)
                    .append("    private ")
                    .append(nullablePrefixedType(field.shape()))
                    .append(' ')
                    .append(ident)
                    .append(";\n");
            out.append(indent).append("    private boolean ").append(ident).append("Present;\n");
        }
        out.append('\n');

        for (InputFieldDefinition field : fields) {
            String ident = fieldIdentifier(field);
            out.append(indent)
                    .append("    public Builder ")
                    .append(ident)
                    .append('(')
                    .append(nullablePrefixedType(field.shape()))
                    .append(' ')
                    .append(ident)
                    .append(") {\n");
            if (field.shape() instanceof InputShape.ListShape) {
                out.append(indent)
                        .append("        this.")
                        .append(ident)
                        .append(" = GraphQlEncoding.copyList(")
                        .append(ident)
                        .append(");\n");
            } else {
                out.append(indent).append("        this.").append(ident).append(" = ").append(ident).append(";\n");
            }
            out.append(indent).append("        this.").append(ident).append("Present = true;\n");
            out.append(indent).append("        return this;\n");
            out.append(indent).append("    }\n\n");
        }

        out.append(indent).append("    public ").append(builtTypeName).append(" build() {\n");
        out.append(indent).append("        LinkedHashMap<String, Object> encoded = new LinkedHashMap<>();\n");
        appendValidationAndEncoding(out, fields, "encoded", errorContext, indent + "        ");
        out.append(indent).append("        return new ").append(builtTypeName).append('(');
        if (constructorTakesFields) {
            for (InputFieldDefinition field : fields) {
                out.append(fieldIdentifier(field)).append(", ");
            }
        }
        out.append("Collections.unmodifiableMap(encoded));\n");
        out.append(indent).append("    }\n");
        out.append(indent).append("}\n");
    }

    private static void appendValidationAndEncoding(
            StringBuilder out,
            List<InputFieldDefinition> fields,
            String mapVar,
            String errorContext,
            String indent) {
        for (InputFieldDefinition field : fields) {
            String ident = fieldIdentifier(field);
            String label = errorContext + "." + field.graphqlName();
            String putStatement =
                    indent
                            + "    "
                            + mapVar
                            + ".put("
                            + JavaLiterals.quote(field.graphqlName())
                            + ", "
                            + encodeExpression(field.shape(), ident, 0)
                            + ");\n";

            boolean required = !field.shape().nullable() && !field.hasDefault();
            if (required) {
                out.append(indent).append("if (!").append(ident).append("Present) {\n");
                out.append(indent)
                        .append("    throw new IllegalStateException(")
                        .append(JavaLiterals.quote(label + " is required and was not set."))
                        .append(");\n");
                out.append(indent).append("} else {\n");
                appendNullCheckIfNonNull(out, field, ident, label, indent + "    ");
                out.append(putStatement);
                out.append(indent).append("}\n");
            } else {
                out.append(indent).append("if (").append(ident).append("Present) {\n");
                appendNullCheckIfNonNull(out, field, ident, label, indent + "    ");
                out.append(putStatement);
                out.append(indent).append("}\n");
            }
        }
    }

    private static void appendNullCheckIfNonNull(
            StringBuilder out, InputFieldDefinition field, String ident, String label, String indent) {
        if (field.shape().nullable()) {
            return;
        }
        out.append(indent).append("if (").append(ident).append(" == null) {\n");
        out.append(indent)
                .append("    throw new IllegalStateException(")
                .append(JavaLiterals.quote(label + " is non-null and cannot be set to null."))
                .append(");\n");
        out.append(indent).append("}\n");
    }

    private static String encodeExpression(InputShape shape, String valueVar, int depth) {
        String nonNullExpression = nonNullEncodeExpression(shape, valueVar, depth);
        return shape.nullable() ? (valueVar + " == null ? null : " + nonNullExpression) : nonNullExpression;
    }

    /**
     * The encode expression for {@code valueVar} assuming it is already known non-null: used both
     * for a field whose shape is itself non-null, and for a list element, which the chosen {@code
     * GraphQlEncoding} list helper only ever passes to its encoder when non-null.
     */
    private static String nonNullEncodeExpression(InputShape shape, String valueVar, int depth) {
        return switch (shape) {
            case InputShape.ScalarShape scalarShape -> scalarEncodeExpression(scalarShape.scalar(), valueVar);
            case InputShape.EnumShape ignored -> valueVar + ".name()";
            case InputShape.InputObjectShape ignored -> valueVar + ".encode()";
            case InputShape.ListShape listShape -> {
                String elementVar = "element" + depth;
                // A null element is a value to preserve when the schema permits it, but a
                // validation failure when the schema's element type is non-null: encodeList and
                // encodeNonNullElementList give each meaning its own wire behavior.
                String encodeMethod =
                        listShape.elementShape().nullable() ? "encodeList" : "encodeNonNullElementList";
                // The lambda parameter is explicitly typed rather than inferred: a scalar element
                // encoded through a custom codec calls a second generic method (encodeScalar)
                // inside this one's body, and javac cannot always solve that nested inference from
                // an implicitly-typed lambda parameter alone.
                yield "GraphQlEncoding."
                        + encodeMethod
                        + "("
                        + valueVar
                        + ", ("
                        + javaTypeFor(listShape.elementShape())
                        + " "
                        + elementVar
                        + ") -> "
                        + nonNullEncodeExpression(listShape.elementShape(), elementVar, depth + 1)
                        + ")";
            }
        };
    }

    private static String scalarEncodeExpression(ScalarType scalar, String valueVar) {
        return switch (scalar) {
            case BuiltinScalar ignored -> valueVar;
            case CustomScalarType custom ->
                    "GraphQlEncoding.encodeScalar("
                            + valueVar
                            + ", "
                            + custom.codecFieldName()
                            + ", "
                            + JavaLiterals.quote(custom.graphqlName())
                            + ")";
        };
    }

    private static String fieldIdentifier(InputFieldDefinition field) {
        return JavaIdentifiers.fieldIdentifier(field.graphqlName());
    }

    private static String nullablePrefixedType(InputShape shape) {
        return JavaIdentifiers.nullableTypeReference(javaTypeFor(shape), shape.nullable());
    }

    private static String javaTypeFor(InputShape shape) {
        return switch (shape) {
            case InputShape.ScalarShape scalarShape -> scalarShape.scalar().javaTypeName();
            case InputShape.EnumShape enumShape -> enumShape.enumType().javaTypeName();
            case InputShape.InputObjectShape objectShape -> objectShape.objectType().javaTypeName();
            case InputShape.ListShape listShape -> "List<" + nullablePrefixedType(listShape.elementShape()) + ">";
        };
    }
}
