package dev.glisseo.izar.compiler.internal.document;

import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentDefinition;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds an operation's final executable document text with {@code __typename} discriminator
 * fields added at the selection sets {@code dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer}
 * identified, without touching the authored {@code .graphql} files.
 *
 * <p>A discriminator is added directly into the selection set that needs it (a field's own, or a
 * referenced fragment's own), never inside a spread fragment: {@code
 * dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer} already chose a response key that cannot
 * collide with anything else selected there. A {@code FragmentSpread} is left untouched wherever it
 * appears; the fragment it names is rewritten once, independently, since it is printed once as its
 * own definition in the final document, minified the same way {@code
 * dev.glisseo.izar.compiler.internal.parse.OperationParser} minifies a document with no injections. Called
 * only when at least one discriminator is needed; an operation with none keeps using {@code
 * dev.glisseo.izar.compiler.internal.parse.OperationParser}'s own minified document, unchanged from before
 * this class existed.
 */
public final class DiscriminatorInjection {

    private DiscriminatorInjection() {}

    public static String buildDocumentText(
            OperationDefinition operation,
            List<FragmentDefinition> referencedFragments,
            Map<SelectionSet, String> injections) {
        List<FragmentDefinition> rewrittenFragments =
                referencedFragments.stream().map(fragment -> rewriteFragment(fragment, injections)).toList();
        Document document = DocumentAssembly.assemble(rewriteOperation(operation, injections), rewrittenFragments);
        return AstPrinter.printAstCompact(document);
    }

    private static OperationDefinition rewriteOperation(OperationDefinition operation, Map<SelectionSet, String> injections) {
        SelectionSet rewritten = rewrite(operation.getSelectionSet(), injections);
        return operation.transform(builder -> builder.selectionSet(rewritten));
    }

    private static FragmentDefinition rewriteFragment(FragmentDefinition fragment, Map<SelectionSet, String> injections) {
        SelectionSet rewritten = rewrite(fragment.getSelectionSet(), injections);
        return fragment.transform(builder -> builder.selectionSet(rewritten));
    }

    private static SelectionSet rewrite(SelectionSet original, Map<SelectionSet, String> injections) {
        List<Selection<?>> rewrittenSelections = new ArrayList<>();
        for (Selection<?> selection : original.getSelections()) {
            if (selection instanceof Field field) {
                rewrittenSelections.add(rewriteField(field, injections));
            } else if (selection instanceof InlineFragment inline) {
                rewrittenSelections.add(rewriteInlineFragment(inline, injections));
            } else {
                // A FragmentSpread: opaque here, rewritten independently as its own top-level
                // fragment definition by buildDocumentText's own loop.
                rewrittenSelections.add(selection);
            }
        }
        String discriminatorKey = injections.get(original);
        if (discriminatorKey != null) {
            rewrittenSelections.add(discriminatorField(discriminatorKey));
        }
        return original.transform(builder -> builder.selections(rewrittenSelections));
    }

    private static Field rewriteField(Field field, Map<SelectionSet, String> injections) {
        SelectionSet nested = field.getSelectionSet();
        if (nested == null) {
            return field;
        }
        SelectionSet rewrittenNested = rewrite(nested, injections);
        return field.transform(builder -> builder.selectionSet(rewrittenNested));
    }

    private static InlineFragment rewriteInlineFragment(InlineFragment inline, Map<SelectionSet, String> injections) {
        SelectionSet rewrittenNested = rewrite(inline.getSelectionSet(), injections);
        return inline.transform(builder -> builder.selectionSet(rewrittenNested));
    }

    private static Field discriminatorField(String responseKey) {
        Field.Builder builder = Field.newField("__typename");
        if (!"__typename".equals(responseKey)) {
            builder.alias(responseKey);
        }
        return builder.build();
    }
}
