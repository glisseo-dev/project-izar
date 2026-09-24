package dev.glisseo.izar.compiler.internal.parse;

import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the fragments a selection set transitively references (through nested fields, inline
 * fragments, and fragments referencing further fragments), in first-referenced order.
 *
 * <p>A spread naming a fragment absent from the library is skipped rather than failing here: the
 * validator's {@code KnownFragmentNames} rule reports that more usefully once it runs against the
 * assembled validation document, which is where {@link OperationParser} sends this method's
 * result next.
 */
final class FragmentReferences {

    private FragmentReferences() {}

    static List<FragmentDefinition> resolve(
            SelectionSet root, Map<String, FragmentDefinition> fragmentLibrary) {
        LinkedHashMap<String, FragmentDefinition> resolved = new LinkedHashMap<>();
        collect(root, fragmentLibrary, resolved);
        return List.copyOf(resolved.values());
    }

    private static void collect(
            SelectionSet selectionSet,
            Map<String, FragmentDefinition> fragmentLibrary,
            LinkedHashMap<String, FragmentDefinition> resolved) {
        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                if (field.getSelectionSet() != null) {
                    collect(field.getSelectionSet(), fragmentLibrary, resolved);
                }
            } else if (selection instanceof InlineFragment inline) {
                collect(inline.getSelectionSet(), fragmentLibrary, resolved);
            } else if (selection instanceof FragmentSpread spread) {
                if (resolved.containsKey(spread.getName())) {
                    continue;
                }
                FragmentDefinition definition = fragmentLibrary.get(spread.getName());
                if (definition == null) {
                    continue;
                }
                resolved.put(spread.getName(), definition);
                collect(definition.getSelectionSet(), fragmentLibrary, resolved);
            }
        }
    }
}
