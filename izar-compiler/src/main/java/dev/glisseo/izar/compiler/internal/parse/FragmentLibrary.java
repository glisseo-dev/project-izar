package dev.glisseo.izar.compiler.internal.parse;

import dev.glisseo.izar.compiler.OperationGenerationException;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies every file the build points at as either an operation file (exactly one operation
 * definition, no fragments) or a fragment file (one or more reusable fragment definitions, no
 * operation), and collects every fragment into one name-keyed library shared across operations.
 *
 * <p>Keeping operations and fragments in separate files means an operation file's raw text is
 * always exactly the operation's authored document, unaffected by which fragments it references:
 * {@link OperationParser} appends referenced fragments' printed text afterward, rather than
 * needing to slice one file's text apart.
 */
public final class FragmentLibrary {

    private final Map<String, FragmentDefinition> fragmentsByName;
    private final List<Path> operationFiles;

    private FragmentLibrary(Map<String, FragmentDefinition> fragmentsByName, List<Path> operationFiles) {
        this.fragmentsByName = fragmentsByName;
        this.operationFiles = operationFiles;
    }

    public Map<String, FragmentDefinition> fragmentsByName() {
        return fragmentsByName;
    }

    /** Files containing exactly one operation definition, in the order they were supplied. */
    public List<Path> operationFiles() {
        return operationFiles;
    }

    public static FragmentLibrary parse(List<Path> files) {
        Map<String, FragmentDefinition> fragments = new LinkedHashMap<>();
        Map<String, Path> definedIn = new LinkedHashMap<>();
        List<Path> operationFiles = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        for (Path file : files) {
            Document document;
            try {
                document = Parser.parse(readTrimmed(file));
            } catch (InvalidSyntaxException e) {
                diagnostics.add(file + ": could not parse GraphQL document: " + e.getMessage());
                continue;
            }

            List<FragmentDefinition> fileFragments = new ArrayList<>();
            List<OperationDefinition> fileOperations = new ArrayList<>();
            for (Definition<?> definition : document.getDefinitions()) {
                if (definition instanceof FragmentDefinition fragment) {
                    fileFragments.add(fragment);
                } else if (definition instanceof OperationDefinition operation) {
                    fileOperations.add(operation);
                }
            }

            if (!fileFragments.isEmpty() && !fileOperations.isEmpty()) {
                diagnostics.add(
                        file
                                + ": a file may contain either one operation or one or more fragments, not"
                                + " both.");
            } else if (fileOperations.size() > 1) {
                diagnostics.add(file + ": a file may contain at most one operation definition.");
            } else if (!fileOperations.isEmpty()) {
                operationFiles.add(file);
            } else if (!fileFragments.isEmpty()) {
                for (FragmentDefinition fragment : fileFragments) {
                    Path existing = definedIn.get(fragment.getName());
                    if (existing != null) {
                        diagnostics.add(
                                file
                                        + ": fragment '"
                                        + fragment.getName()
                                        + "' is already defined in "
                                        + existing
                                        + ".");
                        continue;
                    }
                    fragments.put(fragment.getName(), fragment);
                    definedIn.put(fragment.getName(), file);
                }
            } else {
                diagnostics.add(file + ": contains no operation or fragment definitions.");
            }
        }

        if (!diagnostics.isEmpty()) {
            throw new OperationGenerationException(diagnostics);
        }
        return new FragmentLibrary(Map.copyOf(fragments), List.copyOf(operationFiles));
    }

    /** Line endings are normalized so parsing is stable across checkouts. */
    private static String readTrimmed(Path file) {
        try {
            return Files.readString(file).replace("\r\n", "\n").replace('\r', '\n').strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read GraphQL file " + file, e);
        }
    }
}
