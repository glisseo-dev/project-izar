package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.OperationProvenance;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorStub;
import graphql.execution.CoercedVariables;
import graphql.execution.TypeFromAST;
import graphql.language.ArrayValue;
import graphql.language.Document;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.OperationDefinition;
import graphql.language.Value;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Which contributing client release's registered operations reference each type, field, and
 * argument of one schema, computed by walking every distinct operation document once with GraphQL
 * Java's own type-info traversal ({@link QueryTraverser}), never a hand-rolled selection-set
 * walker. Shared by the schema feature's coverage and impact operations so both report
 * the same notion of "used": an operation uses a type when it returns that type or supplies it as
 * an argument type, and uses a field when it selects it.
 *
 * <p>{@code manifest} is the release-scoped union exposed by {@code ReleaseOperations} already
 * deduplicated by operation ID; {@code provenance} names every contributing release for each one
 * (from {@code izar-manifest}'s {@link OperationProvenance}, the same provenance shape
 * {@code ReleaseAssembler} uses for build-time assembly). One operation document is therefore
 * visited exactly once even when several releases share it verbatim, and every attributed
 * {@link OperationRef} in the result still names the exact release it came from. {@code operationRefs}
 * retains all active operations, including documents that have no non-introspection field usage.
 *
 * <p>{@code inputFieldUsage} is {@code fieldUsage}'s counterpart for input object types: it records
 * which contributing releases supply a value reaching each classifiable input object leaf
 * field, resolved by walking each supplied argument's AST value rather than only recording that an
 * argument was supplied by name. A variable's declared input type contributes every field that
 * type can carry, since a stored document's structure alone never reveals which optional fields a
 * real caller actually populates; an inline object literal contributes only the field paths
 * written in the document itself. The two maps never collide on the same {@code (typeName,
 * fieldName)} key: a GraphQL type name is either an input type or not, never both, in one schema.
 */
public record UsageIndex(
        Map<String, Set<OperationRef>> typeUsage,
        Map<String, Map<String, Set<OperationRef>>> fieldUsage,
        Map<String, Map<String, Set<OperationRef>>> inputFieldUsage,
        Map<String, Map<String, Map<String, Set<OperationRef>>>> argumentUsage,
        List<IncompleteOperation> incomplete,
        List<OperationRef> operationRefs) {

    /** Keeps source compatibility for callers that construct a usage index from partial fixtures. */
    public UsageIndex(
            Map<String, Set<OperationRef>> typeUsage,
            Map<String, Map<String, Set<OperationRef>>> fieldUsage,
            Map<String, Map<String, Map<String, Set<OperationRef>>>> argumentUsage,
            List<IncompleteOperation> incomplete) {
        this(typeUsage, fieldUsage, Map.of(), argumentUsage, incomplete, List.of());
    }

    /** Keeps source compatibility for callers that construct a usage index from partial fixtures. */
    public UsageIndex(
            Map<String, Set<OperationRef>> typeUsage,
            Map<String, Map<String, Set<OperationRef>>> fieldUsage,
            Map<String, Map<String, Map<String, Set<OperationRef>>>> argumentUsage,
            List<IncompleteOperation> incomplete,
            List<OperationRef> operationRefs) {
        this(typeUsage, fieldUsage, Map.of(), argumentUsage, incomplete, operationRefs);
    }

    public UsageIndex {
        incomplete = List.copyOf(incomplete);
        operationRefs = List.copyOf(operationRefs);
    }

    public static UsageIndex empty() {
        return new UsageIndex(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of());
    }

    public static UsageIndex of(GraphQLSchema schema, OperationManifest manifest, List<OperationProvenance> provenance) {
        Map<String, OperationProvenance> provenanceById = provenance.stream()
                .collect(Collectors.toMap(OperationProvenance::operationId, Function.identity()));
        Map<String, Set<OperationRef>> typeUsage = new HashMap<>();
        Map<String, Map<String, Set<OperationRef>>> fieldUsage = new HashMap<>();
        Map<String, Map<String, Set<OperationRef>>> inputFieldUsage = new HashMap<>();
        Map<String, Map<String, Map<String, Set<OperationRef>>>> argumentUsage = new HashMap<>();
        List<IncompleteOperation> incomplete = new ArrayList<>();
        List<OperationRef> operationRefs = new ArrayList<>();
        for (ManifestOperation operation : manifest.operations()) {
            List<OperationRef> refs = OperationRef.refsFor(operation, provenanceById);
            operationRefs.addAll(refs);
            visit(schema, operation, refs, typeUsage, fieldUsage, inputFieldUsage, argumentUsage, incomplete);
        }
        return new UsageIndex(typeUsage, fieldUsage, inputFieldUsage, argumentUsage, incomplete, operationRefs);
    }

    public boolean isComplete() {
        return incomplete.isEmpty();
    }

    public List<OperationRef> forType(String typeName) {
        return sorted(typeUsage.getOrDefault(typeName, Set.of()));
    }

    public List<OperationRef> forField(String typeName, String fieldName) {
        return sorted(fieldUsage.getOrDefault(typeName, Map.of()).getOrDefault(fieldName, Set.of()));
    }

    /**
     * Which contributing releases' stored operations actually supply this specific argument by name
     * This is finer than {@link #forField}, which only tells you a release calls the
     * field at all. A newly added argument necessarily has no usage here yet (nobody could have
     * supplied an argument that did not exist), so callers classifying an argument addition should
     * use {@link #forField} instead: "affected" there means "will need to add this argument," not
     * "already uses it."
     */
    public List<OperationRef> forArgument(String typeName, String fieldName, String argumentName) {
        return sorted(argumentUsage.getOrDefault(typeName, Map.of())
                .getOrDefault(fieldName, Map.of())
                .getOrDefault(argumentName, Set.of()));
    }

    /**
     * Which contributing releases' stored operations supply a value reaching this classifiable
     * input object leaf field, resolved from the argument value(s) that carry it: an inline object
     * literal contributes only when the document itself writes this exact field path, while a
     * variable contributes every field its declared input type can carry.
     */
    public List<OperationRef> forInputField(String typeName, String fieldName) {
        return sorted(inputFieldUsage.getOrDefault(typeName, Map.of()).getOrDefault(fieldName, Set.of()));
    }

    private static List<OperationRef> sorted(Set<OperationRef> usage) {
        return usage.stream().sorted().toList();
    }

    /**
     * Walks one stored operation document's selection set against {@code schema}, attributing every
     * selected field, its declaring type, its return type, and every argument it actually supplies
     * (both the argument's own name and the type of the value it supplies) to every release in
     * {@code refs}. A document that no longer parses, cannot be prepared for traversal, or no longer
     * matches {@code schema} (a stale client's operation against a newer or older schema) still
     * contributes whatever usage it recorded before the failure, never less than before this
     * change, but is now also recorded in {@code incomplete}, so a caller cannot mistake the
     * resulting zero or partial usage for a confirmed absence of dependency.
     */
    private static void visit(GraphQLSchema schema, ManifestOperation operation, List<OperationRef> refs,
            Map<String, Set<OperationRef>> typeUsage,
            Map<String, Map<String, Set<OperationRef>>> fieldUsage,
            Map<String, Map<String, Set<OperationRef>>> inputFieldUsage,
            Map<String, Map<String, Map<String, Set<OperationRef>>>> argumentUsage,
            List<IncompleteOperation> incomplete) {
        Document document;
        try {
            document = Parser.parse(operation.body());
        } catch (InvalidSyntaxException e) {
            incomplete.add(new IncompleteOperation(operation.id(), refs, "Operation document failed to parse: " + e.getMessage()));
            return;
        }
        QueryTraverser traverser;
        Map<String, GraphQLType> variableTypes;
        try {
            OperationDefinition operationDefinition = operationDefinition(document, operation.name());
            variableTypes = operationDefinition == null ? Map.of() : variableTypes(schema, operationDefinition);
            traverser = QueryTraverser.newQueryTraverser()
                    .schema(schema)
                    .document(document)
                    .operationName(operation.name())
                    .coercedVariables(CoercedVariables.of(placeholderVariables(variableTypes)))
                    .build();
        } catch (RuntimeException e) {
            incomplete.add(new IncompleteOperation(operation.id(), refs,
                    "Operation document could not be prepared for traversal against this schema: " + e.getMessage()));
            return;
        }
        try {
            traverser.visitPreOrder(new QueryVisitorStub() {
                @Override
                public void visitField(QueryVisitorFieldEnvironment environment) {
                    if (environment.isTypeNameIntrospectionField()) return;
                    String typeName = environment.getFieldsContainer().getName();
                    String fieldName = environment.getFieldDefinition().getName();
                    fieldUsage.computeIfAbsent(typeName, key -> new HashMap<>())
                            .computeIfAbsent(fieldName, key -> new HashSet<>())
                            .addAll(refs);
                    typeUsage.computeIfAbsent(typeName, key -> new HashSet<>()).addAll(refs);

                    String returnType = GraphQLTypeUtil.unwrapAll(environment.getFieldDefinition().getType()).getName();
                    typeUsage.computeIfAbsent(returnType, key -> new HashSet<>()).addAll(refs);

                    for (var argument : environment.getField().getArguments()) {
                        GraphQLArgument definition = environment.getFieldDefinition().getArgument(argument.getName());
                        if (definition != null) {
                            var unwrappedArgumentType = GraphQLTypeUtil.unwrapAll(definition.getType());
                            typeUsage.computeIfAbsent(unwrappedArgumentType.getName(), key -> new HashSet<>()).addAll(refs);
                            argumentUsage.computeIfAbsent(typeName, key -> new HashMap<>())
                                    .computeIfAbsent(fieldName, key -> new HashMap<>())
                                    .computeIfAbsent(argument.getName(), key -> new HashSet<>())
                                    .addAll(refs);
                            if (unwrappedArgumentType instanceof GraphQLInputObjectType) {
                                recordInputUsage((Value<?>) argument.getValue(), definition.getType(), variableTypes, inputFieldUsage, refs);
                            }
                        }
                    }
                }
            });
        } catch (RuntimeException e) {
            // A stored document that no longer validates against this schema version (a renamed or
            // removed field) contributes whatever usage it recorded before traversal failed, and no
            // more: the report reflects the schema author's decision to change the field, not a
            // reason to hide older, still-registered releases from it. It is recorded below as
            // incomplete rather than silently accepted as a confirmed, exhaustive result.
            incomplete.add(new IncompleteOperation(operation.id(), refs,
                    "Operation document no longer matches this schema; traversal stopped partway through: " + e.getMessage()));
        }
    }

    /** Finds {@code operationName}'s own definition among {@code document}'s top-level definitions. */
    private static @Nullable OperationDefinition operationDefinition(Document document, String operationName) {
        return document.getDefinitions().stream()
                .filter(OperationDefinition.class::isInstance)
                .map(OperationDefinition.class::cast)
                .filter(candidate -> operationName.equals(candidate.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves every variable {@code operationDefinition} declares to its schema type, shared by
     * {@link #placeholderVariables} (building dummy execution-time values) and {@link
     * #recordInputUsage} (resolving a {@link VariableReference}'s declared input type so every
     * field it can carry is attributed, not only the fields a placeholder value happens to set).
     */
    private static Map<String, GraphQLType> variableTypes(GraphQLSchema schema, OperationDefinition operationDefinition) {
        Map<String, GraphQLType> types = new HashMap<>();
        for (VariableDefinition variableDefinition : operationDefinition.getVariableDefinitions()) {
            GraphQLType variableType = TypeFromAST.getTypeFromAST(schema, variableDefinition.getType());
            if (variableType == null) {
                // The variable names a type this schema no longer declares (a removed or renamed
                // custom scalar, enum, or input type): genuine drift, not the false positive
                // placeholderVariables exists to avoid. Surfacing it lets the surrounding try/catch
                // in visit() record it as incomplete instead of silently guessing a value, or a set
                // of carried input fields, for a type that no longer exists.
                throw new IllegalStateException("Variable '" + variableDefinition.getName()
                        + "' declares a type this schema no longer has.");
            }
            types.put(variableDefinition.getName(), variableType);
        }
        return types;
    }

    /**
     * Synthesizes a placeholder value for every variable in {@code variableTypes}, so {@link
     * QueryTraverser.Builder#coercedVariables} can be satisfied without real execution-time input.
     * This is static usage analysis over a stored document, not execution: no value is ever read,
     * only the document's structure is walked, so any value that merely satisfies the schema's
     * non-null rules is as good as a real one. Going through {@code coercedVariables} rather than
     * {@link QueryTraverser.Builder#variables} matters precisely because it skips graphql-java's
     * normal execution-time coercion ({@code ValuesResolver}), which would otherwise reject an
     * empty map for any operation declaring a non-null (e.g. {@code ID!}) variable - the false
     * positive this method exists to avoid.
     */
    private static Map<String, Object> placeholderVariables(Map<String, GraphQLType> variableTypes) {
        Map<String, Object> variables = new HashMap<>();
        for (var entry : variableTypes.entrySet()) {
            variables.put(entry.getKey(), placeholderValue(entry.getValue(), new HashSet<>()));
        }
        return variables;
    }

    /**
     * A representative value for {@code type}, recursing through list/non-null wrappers and input
     * object fields so a required nested field never coerces to null either. The value's contents
     * are arbitrary (usage attribution only cares which arguments/fields a document references, not
     * what a real execution would resolve them to). Only its shape, non-null, and a list where a
     * list is declared, needs to hold up.
     *
     * <p>{@code inputObjectsInProgress} names every input object type already being expanded on the
     * current path, so a self-referential required input (for example {@code input Filter { and:
     * [Filter!]! } }) stops at one level instead of recursing until the stack overflows.
     */
    private static Object placeholderValue(GraphQLType type, Set<String> inputObjectsInProgress) {
        if (GraphQLTypeUtil.isList(type)) {
            return List.of(placeholderValue(GraphQLTypeUtil.unwrapOne(type), inputObjectsInProgress));
        }
        if (GraphQLTypeUtil.isNonNull(type)) {
            return placeholderValue(GraphQLTypeUtil.unwrapOne(type), inputObjectsInProgress);
        }
        if (type instanceof GraphQLEnumType enumType && !enumType.getValues().isEmpty()) {
            return enumType.getValues().get(0).getName();
        }
        if (type instanceof GraphQLInputObjectType inputObjectType) {
            if (!inputObjectsInProgress.add(inputObjectType.getName())) return Map.of();
            try {
                Map<String, Object> fields = new HashMap<>();
                for (GraphQLInputObjectField field : inputObjectType.getFields()) {
                    if (GraphQLTypeUtil.isNonNull(field.getType())) {
                        fields.put(field.getName(), placeholderValue(field.getType(), inputObjectsInProgress));
                    }
                }
                return fields;
            } finally {
                inputObjectsInProgress.remove(inputObjectType.getName());
            }
        }
        if (type instanceof GraphQLScalarType scalarType) {
            return switch (scalarType.getName()) {
                case "Int" -> 0;
                case "Float" -> 0.0;
                case "Boolean" -> Boolean.TRUE;
                default -> "placeholder";
            };
        }
        return "placeholder";
    }

    /**
     * Walks one supplied argument value's AST, attributing every classifiable input object leaf
     * field it reaches to {@code refs} in {@code inputFieldUsage}. {@code declaredType}
     * is the schema type this value is declared against at this position (the argument's own type
     * at the top call, then each input field's own declared type on recursion), unwrapped as
     * needed by the branch below that consults it.
     *
     * <ul>
     *   <li>A {@link VariableReference} contributes every field its own declared input type can
     *       carry ({@link #expandAllFields}), since a stored document's structure alone never
     *       reveals which of that type's optional fields a real execution actually populates.
     *   <li>An {@link ArrayValue} (a list argument or list-typed input field) recurses into each
     *       element against the same {@code declaredType}, graphql-java's list wrapper already
     *       stripped by the caller's {@code unwrapAll}.
     *   <li>An {@link ObjectValue} (an inline input object literal) contributes only the field
     *       paths the literal itself writes, recursing into each one's own value; a nested
     *       variable inside the literal switches back to the first rule.
     *   <li>Any other value (a scalar or enum literal reachable only through recursion into a
     *       list) needs no further attribution: the field carrying it was already recorded by the
     *       caller before recursing.
     * </ul>
     */
    private static void recordInputUsage(
            Value<?> value,
            GraphQLType declaredType,
            Map<String, GraphQLType> variableTypes,
            Map<String, Map<String, Set<OperationRef>>> inputFieldUsage,
            List<OperationRef> refs) {
        if (value instanceof VariableReference variableReference) {
            GraphQLType variableType = variableTypes.get(variableReference.getName());
            if (variableType != null) {
                expandAllFields(variableType, inputFieldUsage, refs, new HashSet<>());
            }
            return;
        }
        if (value instanceof ArrayValue arrayValue) {
            for (Object element : arrayValue.getValues()) {
                recordInputUsage((Value<?>) element, declaredType, variableTypes, inputFieldUsage, refs);
            }
            return;
        }
        if (value instanceof ObjectValue objectValue
                && GraphQLTypeUtil.unwrapAll(declaredType) instanceof GraphQLInputObjectType inputObjectType) {
            for (ObjectField field : objectValue.getObjectFields()) {
                GraphQLInputObjectField fieldDefinition = inputObjectType.getField(field.getName());
                if (fieldDefinition == null) continue;
                inputFieldUsage.computeIfAbsent(inputObjectType.getName(), key -> new HashMap<>())
                        .computeIfAbsent(field.getName(), key -> new HashSet<>())
                        .addAll(refs);
                recordInputUsage((Value<?>) field.getValue(), fieldDefinition.getType(), variableTypes, inputFieldUsage, refs);
            }
        }
    }

    /**
     * Every classifiable field {@code type}'s unwrapped input object type declares, recursively,
     * attributed to {@code refs}. Used only for a variable's declared type ({@link
     * #recordInputUsage}): every field the type can carry counts, not only fields a placeholder
     * value happens to set, since {@link #placeholderValue} only populates non-null fields while
     * this walk must also attribute optional ones. {@code inputObjectsInProgress} guards a
     * self-referential input type the same way {@link
     * #placeholderValue} does.
     */
    private static void expandAllFields(
            GraphQLType type,
            Map<String, Map<String, Set<OperationRef>>> inputFieldUsage,
            List<OperationRef> refs,
            Set<String> inputObjectsInProgress) {
        if (!(GraphQLTypeUtil.unwrapAll(type) instanceof GraphQLInputObjectType inputObjectType)) return;
        if (!inputObjectsInProgress.add(inputObjectType.getName())) return;
        try {
            for (GraphQLInputObjectField field : inputObjectType.getFieldDefinitions()) {
                inputFieldUsage.computeIfAbsent(inputObjectType.getName(), key -> new HashMap<>())
                        .computeIfAbsent(field.getName(), key -> new HashSet<>())
                        .addAll(refs);
                expandAllFields(field.getType(), inputFieldUsage, refs, inputObjectsInProgress);
            }
        } finally {
            inputObjectsInProgress.remove(inputObjectType.getName());
        }
    }
}
