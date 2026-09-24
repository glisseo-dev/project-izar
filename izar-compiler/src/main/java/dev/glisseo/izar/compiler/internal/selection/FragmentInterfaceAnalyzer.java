package dev.glisseo.izar.compiler.internal.selection;

import dev.glisseo.izar.compiler.internal.scalar.BuiltinScalar;
import dev.glisseo.izar.compiler.internal.scalar.ScalarMappingRegistry;
import dev.glisseo.izar.compiler.internal.scalar.ScalarTypeResolver;
import dev.glisseo.izar.compiler.internal.scalar.UnmappedScalarException;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.Selection;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether a named fragment is eligible for {@link FragmentInterface} generation: its own
 * selection set must be nothing but leaf fields (scalars, {@code __typename}, and lists of either),
 * each selected unconditionally, with no nested fragment spread or inline fragment. This is
 * deliberately narrower than {@link SelectionAnalyzer}'s general merge:
 *
 * <ul>
 *   <li>An object, interface, union, or enum-typed field disqualifies the fragment. An object field
 *       would need its own recursively-generated nested interface to keep its accessor's return type
 *       stable across every spread site; an enum field can't be shared today because {@link
 *       SelectionAnalyzer} generates a fresh {@link OutputEnumType} per operation, so two operations
 *       spreading the same fragment would get two incompatible enum types for one accessor. Both are
 *       real follow-ups, not correctness gaps.
 *   <li>A {@code @include}/{@code @skip} directive on any field disqualifies the fragment outright,
 *       so the interface's own declared nullability is always exactly the schema's, independent of
 *       any spread site.
 *   <li>A nested fragment spread or inline fragment inside the fragment disqualifies it: supporting
 *       fragment composition would mean re-deriving this same eligibility recursively.
 * </ul>
 *
 * <p>An unmapped custom scalar (see {@link ScalarTypeResolver}) also disqualifies the fragment,
 * silently: this analyzer never throws, it only decides yes or no. Any operation that actually
 * selects that field still fails generation normally, through the ordinary per-operation path.
 */
public final class FragmentInterfaceAnalyzer {

    private FragmentInterfaceAnalyzer() {}

    public static Optional<FragmentInterface> analyze(
            GraphQLSchema schema, FragmentDefinition fragment, ScalarMappingRegistry scalarMappings) {
        GraphQLType conditionType = schema.getType(fragment.getTypeCondition().getName());
        if (!(conditionType instanceof GraphQLFieldsContainer fieldsContainer)) {
            // A fragment on a union can only ever select __typename; not worth a special case here.
            return Optional.empty();
        }

        List<FieldSelection> fields = new ArrayList<>();
        Set<String> seenResponseNames = new LinkedHashSet<>();
        for (Selection<?> selection : fragment.getSelectionSet().getSelections()) {
            if (!(selection instanceof Field field)) {
                return Optional.empty();
            }
            if (SelectionAnalyzer.isConditional(field.getDirectives())) {
                return Optional.empty();
            }
            String responseName = field.getAlias() != null ? field.getAlias() : field.getName();
            if (!seenResponseNames.add(responseName)) {
                return Optional.empty();
            }

            if ("__typename".equals(field.getName())) {
                if (field.getSelectionSet() != null) {
                    return Optional.empty();
                }
                fields.add(new FieldSelection(responseName, new FieldShape.ScalarField(BuiltinScalar.STRING, false)));
                continue;
            }

            GraphQLFieldDefinition fieldDefinition = fieldsContainer.getFieldDefinition(field.getName());
            if (fieldDefinition == null || field.getSelectionSet() != null) {
                return Optional.empty();
            }
            FieldShape shape = resolveLeafShape(fieldDefinition.getType(), true, scalarMappings);
            if (shape == null) {
                return Optional.empty();
            }
            fields.add(new FieldSelection(responseName, shape));
        }

        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FragmentInterface(fragment.getName(), List.copyOf(fields)));
    }

    private static @Nullable FieldShape resolveLeafShape(
            GraphQLOutputType type, boolean nullable, ScalarMappingRegistry scalarMappings) {
        if (type instanceof GraphQLNonNull nonNull) {
            return resolveLeafShape((GraphQLOutputType) nonNull.getWrappedType(), false, scalarMappings);
        }
        if (type instanceof GraphQLList list) {
            FieldShape element = resolveLeafShape((GraphQLOutputType) list.getWrappedType(), true, scalarMappings);
            return element == null ? null : new FieldShape.ListField(element, nullable);
        }
        if (type instanceof GraphQLScalarType scalarType) {
            try {
                return new FieldShape.ScalarField(ScalarTypeResolver.resolve(scalarType, scalarMappings), nullable);
            } catch (UnmappedScalarException e) {
                return null;
            }
        }
        return null;
    }
}
