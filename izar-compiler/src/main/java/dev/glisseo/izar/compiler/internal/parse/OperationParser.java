package dev.glisseo.izar.compiler.internal.parse;

import dev.glisseo.izar.compiler.OperationGenerationException;
import dev.glisseo.izar.compiler.internal.document.DocumentAssembly;
import dev.glisseo.izar.manifest.OperationManifestInput;
import graphql.language.Definition;
import graphql.language.AstPrinter;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses one {@code .graphql} operation file, resolves the fragments it references from the
 * shared {@link FragmentLibrary}, and validates the assembled document (operation plus referenced
 * fragments) against the schema.
 *
 * <p>Only single-operation files are supported: a file with a fragment definition or more than
 * one operation is rejected before parsing gets this far, by {@link FragmentLibrary}.
 */
public final class OperationParser {

    private OperationParser() {}

    /**
     * @param documentText the final executable document: the operation, followed by every fragment
     *     it transitively references, minified (insignificant whitespace collapsed, comments
     *     dropped) so it can be sent to a server, or persisted, at its smallest size without that
     *     server needing to resolve fragment names itself
     * @param fragmentsByName every fragment reachable from this operation, keyed by name, for
     *     {@code dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer} to resolve fragment spreads
     *     against
     * @param referencedFragments the same fragments, in first-referenced order, for {@code
     *     dev.glisseo.izar.compiler.internal.document.DiscriminatorInjection} to rewrite deterministically
     *     when the document needs a type-discriminator field added
     */
    public record ParsedOperation(
            String operationName,
            String documentText,
            OperationDefinition definition,
            Map<String, FragmentDefinition> fragmentsByName,
            List<FragmentDefinition> referencedFragments) {}

    public static ParsedOperation parse(GraphQLSchema schema, Path operationFile, FragmentLibrary fragmentLibrary) {
        String operationText = readTrimmed(operationFile);

        Document parsedFile;
        try {
            parsedFile = Parser.parse(operationText);
        } catch (InvalidSyntaxException e) {
            throw new OperationGenerationException(
                    operationFile + ": could not parse GraphQL document: " + e.getMessage());
        }

        if (parsedFile.getDefinitions().size() != 1
                || !(parsedFile.getDefinitions().get(0) instanceof OperationDefinition operation)) {
            // FragmentLibrary already rejects a file mixing an operation with fragment
            // definitions, or more than one operation; this guards this class against ever
            // depending on that ordering.
            throw new OperationGenerationException(
                    operationFile + ": expected exactly one operation definition in an operation file.");
        }

        if (operation.getOperation() != OperationDefinition.Operation.QUERY
                && operation.getOperation() != OperationDefinition.Operation.MUTATION
                && operation.getOperation() != OperationDefinition.Operation.SUBSCRIPTION) {
            throw new OperationGenerationException(
                    operationFile + ": only query, mutation, and subscription operations are supported.");
        }

        if (operation.getName() == null || operation.getName().isBlank()) {
            throw new OperationGenerationException(
                    operationFile + ": every operation must be named.");
        }

        List<FragmentDefinition> referencedFragments =
                FragmentReferences.resolve(operation.getSelectionSet(), fragmentLibrary.fragmentsByName());

        Document finalDocument = DocumentAssembly.assemble(operation, referencedFragments);
        List<ValidationError> validationErrors =
                new Validator().validateDocument(schema, finalDocument, Locale.ROOT);
        if (!validationErrors.isEmpty()) {
            throw new OperationGenerationException(
                    validationErrors.stream()
                            .map(error -> (operationFile + ": " + error.getMessage()))
                            .toList());
        }

        Map<String, FragmentDefinition> fragmentsByName = new LinkedHashMap<>();
        for (FragmentDefinition fragment : referencedFragments) {
            fragmentsByName.put(fragment.getName(), fragment);
        }

        return new ParsedOperation(
                operation.getName(),
                AstPrinter.printAstCompact(finalDocument),
                operation,
                Map.copyOf(fragmentsByName),
                referencedFragments);
    }

    /** Parses and validates one manifest body while retaining its exact text and supplied metadata. */
    public static ParsedOperation parseManifestOperation(
            GraphQLSchema schema, Path manifestFile, OperationManifestInput.Operation entry) {
        String source = manifestFile + ": operation '" + entry.name() + "' (id '" + entry.id() + "')";
        Document document;
        try {
            document = Parser.parse(entry.body());
        } catch (InvalidSyntaxException e) {
            throw new OperationGenerationException(source + ": could not parse GraphQL document: " + e.getMessage());
        }

        List<OperationDefinition> operations = new ArrayList<>();
        Map<String, FragmentDefinition> fragments = new LinkedHashMap<>();
        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition operation) {
                operations.add(operation);
            } else if (definition instanceof FragmentDefinition fragment) {
                FragmentDefinition previous = fragments.putIfAbsent(fragment.getName(), fragment);
                if (previous != null) {
                    throw new OperationGenerationException(source + ": fragment '" + fragment.getName() + "' is defined more than once.");
                }
            }
        }
        if (operations.size() != 1) {
            throw new OperationGenerationException(source + ": expected exactly one operation definition.");
        }
        OperationDefinition operation = operations.get(0);
        if (operation.getName() == null || !entry.name().equals(operation.getName())) {
            throw new OperationGenerationException(
                    source + ": manifest name must match the named operation in body ('" + operation.getName() + "').");
        }
        String actualType = switch (operation.getOperation()) {
            case QUERY -> "query";
            case MUTATION -> "mutation";
            case SUBSCRIPTION -> "subscription";
        };
        if (!entry.type().equals(actualType)) {
            throw new OperationGenerationException(
                    source + ": manifest type is '" + entry.type() + "' but body declares a " + actualType + ".");
        }

        List<FragmentDefinition> referencedFragments =
                FragmentReferences.resolve(operation.getSelectionSet(), fragments);
        List<ValidationError> validationErrors =
                new Validator().validateDocument(schema, document, Locale.ROOT);
        if (!validationErrors.isEmpty()) {
            throw new OperationGenerationException(
                    validationErrors.stream().map(error -> source + ": " + error.getMessage()).toList());
        }
        Map<String, FragmentDefinition> referencedByName = new LinkedHashMap<>();
        for (FragmentDefinition fragment : referencedFragments) {
            referencedByName.put(fragment.getName(), fragment);
        }
        return new ParsedOperation(
                operation.getName(), entry.body(), operation, Map.copyOf(referencedByName), referencedFragments);
    }

    /** Line endings are normalized so the generated document is stable across checkouts. */
    private static String readTrimmed(Path operationFile) {
        try {
            return Files.readString(operationFile).replace("\r\n", "\n").replace('\r', '\n').strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read operation file " + operationFile, e);
        }
    }
}
