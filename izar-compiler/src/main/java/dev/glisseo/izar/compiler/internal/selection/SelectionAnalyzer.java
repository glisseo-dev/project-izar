package dev.glisseo.izar.compiler.internal.selection;

import dev.glisseo.izar.compiler.OperationGenerationException;
import dev.glisseo.izar.compiler.internal.naming.JavaIdentifiers;
import dev.glisseo.izar.compiler.internal.parse.OperationParser;
import dev.glisseo.izar.compiler.internal.scalar.BuiltinScalar;
import dev.glisseo.izar.compiler.internal.scalar.ScalarMappingRegistry;
import dev.glisseo.izar.compiler.internal.scalar.ScalarTypeResolver;
import dev.glisseo.izar.compiler.internal.scalar.UnmappedScalarException;
import dev.glisseo.izar.compiler.internal.schema.GraphQlTypeDescriptions;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.TypeName;
import graphql.schema.GraphQLCompositeType;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Walks a validated operation's selection set against the schema, building the {@link
 * ObjectSelection} tree {@code SourceGenerator} renders.
 *
 * <p>Named fragment spreads and inline fragments on the selection's own type are expanded in
 * place: their fields are merged with the surrounding selection's own fields, one generated field
 * per distinct response key. A response key selected only behind {@code @include}/{@code @skip} is
 * nullable even when every schema type involved is non-null, since it may be absent at runtime
 * regardless of what the schema promises; a key reachable through at least one unconditional path
 * stays exactly as nullable as the schema says. Field arguments are supported: their text is
 * authored verbatim into the executable document, so this class does not need to interpret them
 * itself.
 *
 * <p>A field whose output type is an interface or union is resolved through {@link
 * #resolvePolymorphicFieldShape}: a selection with no type-conditioned fragment decodes as one
 * plain {@link ObjectSelection} of shared fields (the same as an ordinary object field), and a
 * selection with at least one type-conditioned fragment becomes a {@link PolymorphicSelection}
 * with one branch per named concrete type plus an {@code Unrecognized} catch-all. A
 * type-conditioned fragment's condition must be the field's own interface/union type (shared
 * fields) or one of its concrete member types; a narrower interface condition is not supported.
 * {@link #discriminatorInjections} records, per affected selection set, the response key {@code
 * SourceGenerator}'s decode dispatch reads the concrete type name from, for {@code
 * dev.glisseo.izar.compiler.internal.document.DiscriminatorInjection} to add to the final executable
 * document.
 */
public final class SelectionAnalyzer {

    /**
     * @param discriminatorInjections original selection-set AST nodes that need a discriminator
     *     field added to the final executable document, mapped to the response key it should use;
     *     see {@link #discriminatorInjections}
     */
    public record Result(
            ObjectSelection root, List<OutputEnumType> enumTypes, Map<SelectionSet, String> discriminatorInjections) {}

    private final GraphQLSchema schema;
    private final Path operationFile;
    private final Map<String, FragmentDefinition> fragmentsByName;
    private final ScalarMappingRegistry scalarMappings;

    /**
     * Every leaf-only fragment eligible for {@link FragmentInterface} generation, keyed by GraphQL
     * fragment name, shared across every operation in this compiler invocation (see {@code
     * dev.glisseo.izar.compiler.OperationCompiler}). Used only to decide whether a direct,
     * unconditional spread of one of these fragments earns the merged record an {@code implements}
     * clause; the interface itself is generated once, independently of any operation, not by this
     * class.
     */
    private final Map<String, FragmentInterface> eligibleFragments;

    /**
     * Every generated record for this operation ends up a sibling directly inside the operation
     * class, not lexically nested inside the record that selected it (simpler to render). So the
     * generated-type namespace is shared across the whole operation, not scoped per selection
     * set. It is also shared with {@code
     * dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer}'s generated input types and
     * enums, so an input type that happens to want the same simple name as an output record cannot
     * collide with it. The caller seeds this set with the root type's name (e.g. "Data") before
     * analysis.
     */
    private final Set<String> usedTypeNames;

    /** Keyed by GraphQL enum name: an output enum is generated once however many fields reach it. */
    private final Map<String, OutputEnumType> resolvedEnumTypes = new LinkedHashMap<>();

    /**
     * Original AST selection-set nodes that need a {@code __typename} discriminator added to the
     * final executable document, mapped to the response key decode reads the concrete type name
     * from. Keyed by identity: a graphql-java AST node has no value-based {@code equals}/{@code
     * hashCode} of its own, so an {@code IdentityHashMap} and a plain {@code HashMap} would behave
     * the same here; {@code IdentityHashMap} is used anyway to say so explicitly.
     */
    private final Map<SelectionSet, String> discriminatorInjections = new IdentityHashMap<>();

    private SelectionAnalyzer(
            GraphQLSchema schema,
            Path operationFile,
            Map<String, FragmentDefinition> fragmentsByName,
            Set<String> usedTypeNames,
            ScalarMappingRegistry scalarMappings,
            Map<String, FragmentInterface> eligibleFragments) {
        this.schema = schema;
        this.operationFile = operationFile;
        this.fragmentsByName = fragmentsByName;
        this.usedTypeNames = usedTypeNames;
        this.scalarMappings = scalarMappings;
        this.eligibleFragments = eligibleFragments;
    }

    public static Result analyze(
            GraphQLSchema schema,
            Path operationFile,
            OperationParser.ParsedOperation parsed,
            GraphQLObjectType rootType,
            Set<String> usedTypeNames,
            ScalarMappingRegistry scalarMappings,
            Map<String, FragmentInterface> eligibleFragments) {
        SelectionAnalyzer analyzer = new SelectionAnalyzer(
                schema, operationFile, parsed.fragmentsByName(), usedTypeNames, scalarMappings, eligibleFragments);
        ObjectSelection root =
                analyzer.analyzeObject(
                        "Data", rootType, wildcardSelections(parsed.definition().getSelectionSet().getSelections()));
        return new Result(
                root,
                List.copyOf(analyzer.resolvedEnumTypes.values()),
                Map.copyOf(analyzer.discriminatorInjections));
    }

    /**
     * graphql-java's {@code Selection} AST node is a self-bounded generic ({@code Selection<T
     * extends Selection<T>>}), but every accessor that returns one of its lists ({@link
     * SelectionSet#getSelections()} among them) is itself declared as the raw {@code
     * List<Selection>}. This copies such a list into a properly parameterized one, once, so the
     * rest of this class never has to spell out that raw type itself.
     */
    @SuppressWarnings("rawtypes")
    private static List<Selection<?>> wildcardSelections(List<Selection> selections) {
        List<Selection<?>> copy = new ArrayList<>(selections.size());
        for (Selection<?> selection : selections) {
            copy.add(selection);
        }
        return copy;
    }

    private ObjectSelection analyzeObject(String javaTypeName, GraphQLCompositeType type, List<Selection<?>> selections) {
        List<Occurrence> occurrences = new ArrayList<>();
        Set<String> directFragments = new LinkedHashSet<>();
        collect(type, selections, false, occurrences, directFragments);
        return new ObjectSelection(
                javaTypeName, List.copyOf(mergeOccurrences(type, occurrences)), List.copyOf(directFragments));
    }

    private List<FieldSelection> mergeOccurrences(GraphQLCompositeType type, List<Occurrence> occurrences) {
        Map<String, List<Occurrence>> byResponseName = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            byResponseName.computeIfAbsent(occurrence.responseName(), key -> new ArrayList<>()).add(occurrence);
        }
        List<FieldSelection> fields = new ArrayList<>();
        for (List<Occurrence> group : byResponseName.values()) {
            fields.add(analyzeMergedField(type, group));
        }
        return fields;
    }

    /**
     * Flattens {@code selections} into one field occurrence per {@link Field}, expanding every
     * fragment spread and inline fragment in place. {@code conditional} tracks whether any
     * enclosing fragment spread, inline fragment, or the field itself carries {@code
     * @include}/{@code @skip}: it only ever turns true on the way down, since a field reached
     * through even one unconditional path is guaranteed present whenever another path to the same
     * response key is conditional.
     *
     * @param directFragments accumulates the name of every {@link #eligibleFragments} member
     *     spread here (or through nested inline fragments) with no conditionality anywhere along
     *     the path, for {@link #analyzeObject} to turn into {@code implements} clauses; a fragment
     *     spread reached only through a conditional path, or through another (non-eligible)
     *     fragment spread, is never added
     */
    private void collect(
            GraphQLCompositeType type,
            List<Selection<?>> selections,
            boolean conditional,
            List<Occurrence> out,
            Set<String> directFragments) {
        for (Selection<?> selection : selections) {
            if (selection instanceof Field field) {
                out.add(new Occurrence(responseName(field), field, conditional || isConditional(field.getDirectives())));
            } else if (selection instanceof InlineFragment inline) {
                GraphQLCompositeType conditionType = resolveFragmentType(inline.getTypeCondition(), type);
                boolean nestedConditional = conditional || isConditional(inline.getDirectives());
                collect(
                        conditionType,
                        wildcardSelections(inline.getSelectionSet().getSelections()),
                        nestedConditional,
                        out,
                        nestedConditional ? new LinkedHashSet<>() : directFragments);
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition definition = lookupFragment(spread.getName());
                GraphQLCompositeType conditionType = resolveFragmentType(definition.getTypeCondition(), type);
                boolean nestedConditional = conditional || isConditional(spread.getDirectives());
                if (!nestedConditional && eligibleFragments.containsKey(spread.getName())) {
                    directFragments.add(spread.getName());
                }
                collect(
                        conditionType,
                        wildcardSelections(definition.getSelectionSet().getSelections()),
                        nestedConditional,
                        out,
                        new LinkedHashSet<>());
            }
        }
    }

    private FragmentDefinition lookupFragment(String name) {
        FragmentDefinition definition = fragmentsByName.get(name);
        if (definition == null) {
            // Already caught by schema validation (KnownFragmentNames) in OperationParser;
            // guarded here too so this class never depends on that ordering.
            throw error("references undefined fragment '" + name + "'.");
        }
        return definition;
    }

    /**
     * Resolves a fragment's type condition to a schema type, trusting graphql-java's own validator
     * (already run in {@link OperationParser}) to have rejected a condition incompatible with
     * where the fragment is spread. An inline fragment with no type condition selects against its
     * enclosing type unchanged.
     */
    private GraphQLCompositeType resolveFragmentType(@Nullable TypeName typeCondition, GraphQLCompositeType enclosingType) {
        if (typeCondition == null) {
            return enclosingType;
        }
        GraphQLType type = schema.getType(typeCondition.getName());
        if (!(type instanceof GraphQLCompositeType compositeType)) {
            throw error(
                    "uses a fragment on '"
                            + typeCondition.getName()
                            + "', which is not an object, interface, or union type.");
        }
        return compositeType;
    }

    private FieldSelection analyzeMergedField(GraphQLCompositeType parentType, List<Occurrence> group) {
        Field representative = group.get(0).field();
        String schemaFieldName = representative.getName();
        String responseName = group.get(0).responseName();
        boolean guaranteedPresent = group.stream().anyMatch(occurrence -> !occurrence.conditional());

        if ("__typename".equals(schemaFieldName)) {
            return new FieldSelection(
                    responseName, new FieldShape.ScalarField(BuiltinScalar.STRING, !guaranteedPresent));
        }

        GraphQLFieldDefinition fieldDefinition = fieldDefinition(parentType, schemaFieldName);
        if (fieldDefinition == null) {
            // Already caught by schema validation in OperationParser; guarded here too so this
            // class never depends on that ordering.
            throw error(
                    "field '"
                            + schemaFieldName
                            + "' does not exist on type '"
                            + GraphQlTypeDescriptions.describe(parentType)
                            + "'.");
        }

        FieldShape schemaShape = resolveFieldShape(schemaFieldName, fieldDefinition.getType(), true, false, group);
        FieldShape shape = withNullable(schemaShape, schemaShape.nullable() || !guaranteedPresent);

        return new FieldSelection(responseName, shape);
    }

    private static @Nullable GraphQLFieldDefinition fieldDefinition(GraphQLCompositeType type, String fieldName) {
        return type instanceof GraphQLFieldsContainer fieldsContainer ? fieldsContainer.getFieldDefinition(fieldName) : null;
    }

    /**
     * {@code insideList} is true once resolution has unwrapped at least one {@link GraphQLList}
     * on the way here; see {@link #nestedTypeBaseName} for what that changes about naming.
     */
    private FieldShape resolveFieldShape(
            String schemaFieldName, GraphQLOutputType type, boolean nullable, boolean insideList, List<Occurrence> group) {
        if (type instanceof GraphQLNonNull nonNull) {
            return resolveFieldShape(
                    schemaFieldName, (GraphQLOutputType) nonNull.getWrappedType(), false, insideList, group);
        }
        if (type instanceof GraphQLList list) {
            FieldShape element =
                    resolveFieldShape(schemaFieldName, (GraphQLOutputType) list.getWrappedType(), true, true, group);
            return new FieldShape.ListField(element, nullable);
        }

        if (type instanceof GraphQLObjectType objectType) {
            String nestedTypeName = JavaIdentifiers.uniqueTypeName(
                    usedTypeNames,
                    nestedTypeBaseName(insideList, objectType.getName(), schemaFieldName, group.get(0).responseName()));
            ObjectSelection nested = analyzeObject(nestedTypeName, objectType, mergedNestedSelections(schemaFieldName, group));
            return new FieldShape.ObjectField(nested, nullable);
        }
        if (type instanceof GraphQLInterfaceType || type instanceof GraphQLUnionType) {
            return resolvePolymorphicFieldShape(
                    schemaFieldName, (GraphQLCompositeType) type, nullable, insideList, group);
        }
        if (type instanceof GraphQLEnumType enumType) {
            return new FieldShape.EnumField(resolveOutputEnumType(enumType), nullable);
        }
        if (type instanceof GraphQLScalarType scalarType) {
            if (group.get(0).field().getSelectionSet() != null) {
                throw error("field '" + schemaFieldName + "' is a scalar but has a sub-selection.");
            }
            try {
                return new FieldShape.ScalarField(ScalarTypeResolver.resolve(scalarType, scalarMappings), nullable);
            } catch (UnmappedScalarException e) {
                throw error(
                        "field '"
                                + schemaFieldName
                                + "' uses custom scalar '"
                                + e.graphqlScalarName()
                                + "', which has no configured scalar mapping. Configure a <scalarMapping> for '"
                                + e.graphqlScalarName()
                                + "' with its Java type and codec class before generating this field.");
            }
        }
        throw error(
                "field '"
                        + schemaFieldName
                        + "' has an unsupported output type ('"
                        + GraphQlTypeDescriptions.describe(type)
                        + "').");
    }

    /**
     * An unaliased list element is named after its own schema type ({@code schemaTypeName}), per
     * ADR 0037: the field name is typically plural there (e.g. {@code books: [Book!]!}) and
     * singularizing it back would need real English inflection this codebase deliberately avoids.
     * Every other field is named after its response name instead ({@code responseName}, which
     * equals {@code schemaFieldName} when the field carries no alias) so that two same-typed
     * fields on one selection (e.g. {@code author}/{@code illustrator}, both {@code Author}) keep
     * getting distinct names, and so an explicit alias lets a query author pick the generated name
     * directly (e.g. aliasing two same-typed list fields, {@code work: addresses(...)} /
     * {@code postal: addresses(...)}, to get {@code Work}/{@code Postal} instead of colliding on
     * {@code Address} and falling to {@link JavaIdentifiers#uniqueTypeName}'s numbered fallback).
     */
    private static String nestedTypeBaseName(
            boolean insideList, String schemaTypeName, String schemaFieldName, String responseName) {
        boolean aliased = !responseName.equals(schemaFieldName);
        return insideList && !aliased ? schemaTypeName : JavaIdentifiers.capitalize(responseName);
    }

    private List<Selection<?>> mergedNestedSelections(String schemaFieldName, List<Occurrence> group) {
        List<Selection<?>> mergedSelections = new ArrayList<>();
        for (Occurrence occurrence : group) {
            SelectionSet nestedSelectionSet = occurrence.field().getSelectionSet();
            if (nestedSelectionSet == null) {
                throw error("field '" + schemaFieldName + "' selects an object type but has no sub-selection.");
            }
            mergedSelections.addAll(wildcardSelections(nestedSelectionSet.getSelections()));
        }
        return mergedSelections;
    }

    private OutputEnumType resolveOutputEnumType(GraphQLEnumType enumType) {
        OutputEnumType cached = resolvedEnumTypes.get(enumType.getName());
        if (cached != null) {
            return cached;
        }
        String javaTypeName = JavaIdentifiers.uniqueTypeName(usedTypeNames, enumType.getName());
        List<String> values = enumType.getValues().stream().map(GraphQLEnumValueDefinition::getName).toList();
        OutputEnumType result = new OutputEnumType(javaTypeName, enumType.getName(), values);
        resolvedEnumTypes.put(enumType.getName(), result);
        return result;
    }

    private FieldShape resolvePolymorphicFieldShape(
            String schemaFieldName,
            GraphQLCompositeType polymorphicType,
            boolean nullable,
            boolean insideList,
            List<Occurrence> group) {
        List<Selection<?>> mergedSelections = mergedNestedSelections(schemaFieldName, group);

        PolymorphicOccurrences occurrences = new PolymorphicOccurrences(new ArrayList<>(), new LinkedHashMap<>());
        collectPolymorphic(polymorphicType, null, mergedSelections, false, occurrences);

        String polymorphicTypeName = JavaIdentifiers.uniqueTypeName(
                usedTypeNames,
                nestedTypeBaseName(insideList, polymorphicType.getName(), schemaFieldName, group.get(0).responseName()));

        if (occurrences.branches().isEmpty()) {
            ObjectSelection sharedOnly = new ObjectSelection(
                    polymorphicTypeName, List.copyOf(mergeOccurrences(polymorphicType, occurrences.shared())), List.of());
            return new FieldShape.ObjectField(sharedOnly, nullable);
        }

        List<FieldSelection> sharedFieldSelections = mergeOccurrences(polymorphicType, occurrences.shared());

        boolean needsInjection = !typenameAlreadyGuaranteed(occurrences);
        String discriminatorKey = needsInjection ? freshDiscriminatorKey(allResponseKeys(occurrences)) : "__typename";

        List<PolymorphicBranch> branches = new ArrayList<>();
        for (Map.Entry<String, List<Occurrence>> entry : occurrences.branches().entrySet()) {
            String concreteTypeName = entry.getKey();
            GraphQLObjectType concreteType = (GraphQLObjectType) schema.getType(concreteTypeName);
            List<Occurrence> combined = new ArrayList<>(occurrences.shared());
            combined.addAll(entry.getValue());
            String branchTypeName = JavaIdentifiers.uniqueTypeName(usedTypeNames, polymorphicTypeName + concreteTypeName);
            ObjectSelection branchSelection =
                    new ObjectSelection(branchTypeName, List.copyOf(mergeOccurrences(concreteType, combined)), List.of());
            branches.add(new PolymorphicBranch(concreteTypeName, branchSelection));
        }

        List<FieldSelection> unrecognizedFields = new ArrayList<>(sharedFieldSelections);
        if (needsInjection) {
            unrecognizedFields.add(new FieldSelection(discriminatorKey, new FieldShape.ScalarField(BuiltinScalar.STRING, false)));
        }
        String unrecognizedTypeName = JavaIdentifiers.uniqueTypeName(usedTypeNames, polymorphicTypeName + "Unrecognized");
        ObjectSelection unrecognizedBranch =
                new ObjectSelection(unrecognizedTypeName, List.copyOf(unrecognizedFields), List.of());

        if (needsInjection) {
            discriminatorInjections.put(group.get(0).field().getSelectionSet(), discriminatorKey);
        }

        PolymorphicSelection polymorphicSelection =
                new PolymorphicSelection(polymorphicTypeName, List.copyOf(branches), unrecognizedBranch, discriminatorKey);
        return new FieldShape.PolymorphicField(polymorphicSelection, nullable);
    }

    /**
     * Occurrences collected while walking one polymorphic field's selections, split by whether
     * they apply regardless of concrete type ({@link #shared}) or only within one named concrete
     * type's fragment ({@link #branches}, keyed by that type's schema name). Both collections are
     * mutated in place by {@link #collectPolymorphic} as it walks; the record only bundles them so
     * the walk's own methods take one parameter instead of two that always travel together.
     */
    private record PolymorphicOccurrences(List<Occurrence> shared, Map<String, List<Occurrence>> branches) {}

    /**
     * Splits a polymorphic field's (possibly fragment-expanded) selections into shared occurrences
     * (contributed regardless of concrete type) and per-concrete-type branch occurrences,
     * following fragment type conditions the same way {@link #collect} does. {@code
     * currentConcreteTypeName} is {@code null} while walking shared selections, and set once a
     * type-conditioned fragment narrows to a concrete type; it never resets back to {@code null}
     * on the way down, since a field selected inside a concrete-type fragment stays specific to
     * that type regardless of further nesting.
     */
    private void collectPolymorphic(
            GraphQLCompositeType polymorphicType,
            @Nullable String currentConcreteTypeName,
            List<Selection<?>> selections,
            boolean conditional,
            PolymorphicOccurrences occurrences) {
        for (Selection<?> selection : selections) {
            if (selection instanceof Field field) {
                Occurrence occurrence =
                        new Occurrence(responseName(field), field, conditional || isConditional(field.getDirectives()));
                if (currentConcreteTypeName == null) {
                    occurrences.shared().add(occurrence);
                } else {
                    occurrences.branches().computeIfAbsent(currentConcreteTypeName, key -> new ArrayList<>()).add(occurrence);
                }
            } else if (selection instanceof InlineFragment inline) {
                collectPolymorphicNested(
                        polymorphicType,
                        currentConcreteTypeName,
                        inline.getTypeCondition(),
                        wildcardSelections(inline.getSelectionSet().getSelections()),
                        conditional || isConditional(inline.getDirectives()),
                        occurrences);
            } else if (selection instanceof FragmentSpread spread) {
                FragmentDefinition definition = lookupFragment(spread.getName());
                collectPolymorphicNested(
                        polymorphicType,
                        currentConcreteTypeName,
                        definition.getTypeCondition(),
                        wildcardSelections(definition.getSelectionSet().getSelections()),
                        conditional || isConditional(spread.getDirectives()),
                        occurrences);
            }
        }
    }

    private void collectPolymorphicNested(
            GraphQLCompositeType polymorphicType,
            @Nullable String currentConcreteTypeName,
            @Nullable TypeName typeCondition,
            List<Selection<?>> nestedSelections,
            boolean conditional,
            PolymorphicOccurrences occurrences) {
        if (typeCondition == null || typeCondition.getName().equals(graphqlTypeName(polymorphicType))) {
            collectPolymorphic(polymorphicType, currentConcreteTypeName, nestedSelections, conditional, occurrences);
            return;
        }
        GraphQLType resolved = schema.getType(typeCondition.getName());
        if (resolved instanceof GraphQLObjectType objectType) {
            String concreteTypeName = currentConcreteTypeName != null ? currentConcreteTypeName : objectType.getName();
            collectPolymorphic(polymorphicType, concreteTypeName, nestedSelections, conditional, occurrences);
            return;
        }
        throw error(
                "uses a fragment on '"
                        + typeCondition.getName()
                        + "' inside a selection of '"
                        + graphqlTypeName(polymorphicType)
                        + "'; only a fragment on '"
                        + graphqlTypeName(polymorphicType)
                        + "' itself or on one of its concrete member types is supported.");
    }

    private static String graphqlTypeName(GraphQLCompositeType type) {
        return ((GraphQLNamedType) type).getName();
    }

    /**
     * {@code true} if the merged response is guaranteed to already carry a usable, unaliased,
     * unconditional {@code __typename} under the plain key {@code "__typename"}: either every
     * branch selects it itself, or it is selected once, shared across every branch. Decode's
     * dispatch reads the discriminator once, before it knows which branch matched, so a {@code
     * __typename} selected inside only *some* branches does not count: the response would be
     * missing the key whenever a different branch's data came back.
     */
    private static boolean typenameAlreadyGuaranteed(PolymorphicOccurrences occurrences) {
        if (containsBareUnconditionalTypename(occurrences.shared())) {
            return true;
        }
        if (occurrences.branches().isEmpty()) {
            return false;
        }
        for (List<Occurrence> branch : occurrences.branches().values()) {
            if (!containsBareUnconditionalTypename(branch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsBareUnconditionalTypename(List<Occurrence> occurrences) {
        for (Occurrence occurrence : occurrences) {
            Field field = occurrence.field();
            if ("__typename".equals(field.getName()) && field.getAlias() == null && !occurrence.conditional()) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> allResponseKeys(PolymorphicOccurrences occurrences) {
        Set<String> keys = new LinkedHashSet<>();
        for (Occurrence occurrence : occurrences.shared()) {
            keys.add(occurrence.responseName());
        }
        for (List<Occurrence> branch : occurrences.branches().values()) {
            for (Occurrence occurrence : branch) {
                keys.add(occurrence.responseName());
            }
        }
        return keys;
    }

    /**
     * {@code __typename}, or a fallback name if that response key is already used for something
     * else. The fallback avoids a leading double underscore on purpose: the GraphQL specification
     * reserves {@code __}-prefixed names for introspection, so a compliant validator may reject an
     * alias that starts with one.
     */
    private static String freshDiscriminatorKey(Set<String> usedResponseKeys) {
        if (!usedResponseKeys.contains("__typename")) {
            return "__typename";
        }
        String candidate = "izarTypename";
        int suffix = 2;
        while (usedResponseKeys.contains(candidate)) {
            candidate = "izarTypename" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static FieldShape withNullable(FieldShape shape, boolean nullable) {
        return switch (shape) {
            case FieldShape.ScalarField scalar -> new FieldShape.ScalarField(scalar.scalar(), nullable);
            case FieldShape.EnumField enumField -> new FieldShape.EnumField(enumField.enumType(), nullable);
            case FieldShape.ObjectField object -> new FieldShape.ObjectField(object.selection(), nullable);
            case FieldShape.PolymorphicField polymorphic -> new FieldShape.PolymorphicField(polymorphic.selection(), nullable);
            case FieldShape.ListField list -> new FieldShape.ListField(list.elementShape(), nullable);
        };
    }

    private static String responseName(Field field) {
        return field.getAlias() != null ? field.getAlias() : field.getName();
    }

    static boolean isConditional(List<Directive> directives) {
        for (Directive directive : directives) {
            if ("include".equals(directive.getName()) || "skip".equals(directive.getName())) {
                return true;
            }
        }
        return false;
    }

    private OperationGenerationException error(String detail) {
        return new OperationGenerationException(operationFile + ": " + detail);
    }

    /**
     * One field selection reached while walking the (possibly fragment-expanded) selection tree.
     *
     * @param conditional whether this specific path to the field is subject to {@code
     *     @include}/{@code @skip}, from the field itself or any fragment it was reached through
     */
    private record Occurrence(String responseName, Field field, boolean conditional) {}
}
