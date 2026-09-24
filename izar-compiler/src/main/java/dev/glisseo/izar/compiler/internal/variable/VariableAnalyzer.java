package dev.glisseo.izar.compiler.internal.variable;

import dev.glisseo.izar.compiler.OperationGenerationException;
import dev.glisseo.izar.compiler.internal.naming.JavaIdentifiers;
import dev.glisseo.izar.compiler.internal.scalar.ScalarMappingRegistry;
import dev.glisseo.izar.compiler.internal.scalar.ScalarTypeResolver;
import dev.glisseo.izar.compiler.internal.scalar.UnmappedScalarException;
import dev.glisseo.izar.compiler.internal.schema.GraphQlTypeDescriptions;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks an operation's declared variables against the schema, building the {@link InputShape}
 * tree for each and recursively resolving every reachable input object and enum type into
 * generated Java shapes.
 *
 * <p>Field arguments themselves need no analysis here: an authored operation's argument text is
 * preserved verbatim in the executable document, and GraphQL Java's validator (already run in
 * {@code dev.glisseo.izar.compiler.internal.parse.OperationParser}) confirms every variable reference is
 * declared, used at a compatible position, and that required arguments are supplied. This class
 * only turns variable declarations into the shapes {@code SourceGenerator} renders as builders.
 */
public final class VariableAnalyzer {

    public record Result(
            List<InputFieldDefinition> variables,
            List<InputObjectType> inputObjectTypes,
            List<InputEnumType> enumTypes) {}

    private final GraphQLSchema schema;
    private final Path operationFile;
    private final String operationName;
    private final Set<String> usedTypeNames;
    private final ScalarMappingRegistry scalarMappings;

    /**
     * Keyed by GraphQL type name: an input object type is generated once however many variables
     * or nested fields reach it.
     */
    private final Map<String, InputObjectType> resolvedInputObjectTypes = new LinkedHashMap<>();

    private final Map<String, InputEnumType> resolvedEnumTypes = new LinkedHashMap<>();

    /**
     * GraphQL type name to its not-yet-fully-resolved {@link InputObjectType}, for exactly as long
     * as that type's own fields are being resolved. A field reached while its declaring type is
     * still in here is a self-reference or a mutual cycle: it gets this same in-progress instance
     * rather than triggering another resolution pass, which is what lets a genuinely recursive
     * input type resolve at all instead of recursing until {@code StackOverflowError}.
     */
    private final Map<String, InputObjectType> inProgressInputObjectTypes = new LinkedHashMap<>();

    private VariableAnalyzer(
            GraphQLSchema schema,
            Path operationFile,
            String operationName,
            Set<String> usedTypeNames,
            ScalarMappingRegistry scalarMappings) {
        this.schema = schema;
        this.operationFile = operationFile;
        this.operationName = operationName;
        this.usedTypeNames = usedTypeNames;
        this.scalarMappings = scalarMappings;
    }

    /**
     * @param usedTypeNames the Java type names already claimed by this operation's output
     *     selection tree; shared so a generated input type or enum cannot collide with an output
     *     record that happens to want the same simple name
     */
    public static Result analyze(
            GraphQLSchema schema,
            Path operationFile,
            String operationName,
            List<VariableDefinition> variableDefinitions,
            Set<String> usedTypeNames,
            ScalarMappingRegistry scalarMappings) {
        VariableAnalyzer analyzer =
                new VariableAnalyzer(schema, operationFile, operationName, usedTypeNames, scalarMappings);

        List<InputFieldDefinition> variables = new ArrayList<>();
        for (VariableDefinition variableDefinition : variableDefinitions) {
            InputShape shape = analyzer.resolveAstType(variableDefinition.getType(), true);
            boolean hasDefault = variableDefinition.getDefaultValue() != null;
            variables.add(new InputFieldDefinition(variableDefinition.getName(), shape, hasDefault));
        }

        return new Result(
                List.copyOf(variables),
                List.copyOf(analyzer.resolvedInputObjectTypes.values()),
                List.copyOf(analyzer.resolvedEnumTypes.values()));
    }

    private InputShape resolveAstType(Type<?> astType, boolean nullable) {
        if (astType instanceof NonNullType nonNull) {
            return resolveAstType(nonNull.getType(), false);
        }
        if (astType instanceof ListType listType) {
            return new InputShape.ListShape(resolveAstType(listType.getType(), true), nullable);
        }
        if (astType instanceof TypeName typeName) {
            GraphQLType type = schema.getType(typeName.getName());
            if (type == null) {
                // Already caught by schema validation in OperationParser; guarded here too so
                // this class never depends on that ordering.
                throw error("uses unknown type '" + typeName.getName() + "'.");
            }
            return resolveResolvedType(type, nullable);
        }
        throw error("uses unrecognized GraphQL type syntax '" + astType + "'.");
    }

    private InputShape resolveSchemaType(GraphQLType type, boolean nullable) {
        if (type instanceof GraphQLNonNull nonNull) {
            return resolveSchemaType(nonNull.getWrappedType(), false);
        }
        if (type instanceof GraphQLList list) {
            return new InputShape.ListShape(resolveSchemaType(list.getWrappedType(), true), nullable);
        }
        return resolveResolvedType(type, nullable);
    }

    private InputShape resolveResolvedType(GraphQLType type, boolean nullable) {
        if (type instanceof GraphQLScalarType scalarType) {
            try {
                return new InputShape.ScalarShape(ScalarTypeResolver.resolve(scalarType, scalarMappings), nullable);
            } catch (UnmappedScalarException e) {
                throw error(
                        "uses custom scalar '"
                                + e.graphqlScalarName()
                                + "', which has no configured scalar mapping. Configure a <scalarMapping> for '"
                                + e.graphqlScalarName()
                                + "' with its Java type and codec class before generating this operation.");
            }
        }
        if (type instanceof GraphQLEnumType enumType) {
            return new InputShape.EnumShape(resolveEnumType(enumType), nullable);
        }
        if (type instanceof GraphQLInputObjectType inputObjectType) {
            return new InputShape.InputObjectShape(resolveInputObjectType(inputObjectType), nullable);
        }
        // Already caught by schema validation (VariablesAreInputTypes and the schema parser
        // itself for nested input-object fields); guarded here too so this class never depends
        // on that ordering.
        throw error(
                "uses '" + GraphQlTypeDescriptions.describe(type) + "', which is not a valid GraphQL input type.");
    }

    private InputEnumType resolveEnumType(GraphQLEnumType enumType) {
        InputEnumType cached = resolvedEnumTypes.get(enumType.getName());
        if (cached != null) {
            return cached;
        }
        String javaTypeName = JavaIdentifiers.uniqueTypeName(usedTypeNames, enumType.getName());
        List<String> values = enumType.getValues().stream().map(GraphQLEnumValueDefinition::getName).toList();
        InputEnumType result = new InputEnumType(javaTypeName, values);
        resolvedEnumTypes.put(enumType.getName(), result);
        return result;
    }

    private InputObjectType resolveInputObjectType(GraphQLInputObjectType inputObjectType) {
        String graphqlName = inputObjectType.getName();
        InputObjectType cached = resolvedInputObjectTypes.get(graphqlName);
        if (cached != null) {
            return cached;
        }
        InputObjectType inProgress = inProgressInputObjectTypes.get(graphqlName);
        if (inProgress != null) {
            return inProgress;
        }

        String javaTypeName = JavaIdentifiers.uniqueTypeName(usedTypeNames, graphqlName);
        InputObjectType result = new InputObjectType(javaTypeName);
        inProgressInputObjectTypes.put(graphqlName, result);

        List<InputFieldDefinition> fields = new ArrayList<>();
        for (GraphQLInputObjectField field : inputObjectType.getFields()) {
            InputShape shape = resolveSchemaType(field.getType(), true);
            fields.add(new InputFieldDefinition(field.getName(), shape, field.hasSetDefaultValue()));
        }
        result.setFields(fields);

        inProgressInputObjectTypes.remove(graphqlName);
        resolvedInputObjectTypes.put(graphqlName, result);
        return result;
    }

    private OperationGenerationException error(String detail) {
        return new OperationGenerationException(
                operationFile + ": operation '" + operationName + "' " + detail);
    }
}
