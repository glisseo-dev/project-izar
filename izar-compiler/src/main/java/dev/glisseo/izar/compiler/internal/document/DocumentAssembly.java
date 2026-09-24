package dev.glisseo.izar.compiler.internal.document;

import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import java.util.List;

/**
 * Assembles one operation and every fragment it references into a single {@link Document}: the
 * shape both {@code dev.glisseo.izar.compiler.internal.parse.OperationParser} (validating and printing an
 * authored operation) and {@link DiscriminatorInjection} (printing a rewritten one) need, so a
 * server sent this document can resolve every fragment spread without depending on a definition it
 * was never sent.
 */
public final class DocumentAssembly {

    private DocumentAssembly() {}

    public static Document assemble(Definition<?> operation, List<FragmentDefinition> referencedFragments) {
        Document.Builder builder = Document.newDocument().definition(operation);
        referencedFragments.forEach(builder::definition);
        return builder.build();
    }
}
