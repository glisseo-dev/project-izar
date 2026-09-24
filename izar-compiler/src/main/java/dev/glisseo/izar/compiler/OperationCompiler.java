package dev.glisseo.izar.compiler;

import dev.glisseo.izar.compiler.internal.document.DiscriminatorInjection;
import dev.glisseo.izar.compiler.internal.generate.SourceGenerator;
import dev.glisseo.izar.compiler.internal.parse.FragmentLibrary;
import dev.glisseo.izar.compiler.internal.parse.OperationParser;
import dev.glisseo.izar.compiler.internal.scalar.ScalarMappingRegistry;
import dev.glisseo.izar.compiler.internal.schema.SchemaLoader;
import dev.glisseo.izar.compiler.internal.selection.FragmentInterface;
import dev.glisseo.izar.compiler.internal.selection.FragmentInterfaceAnalyzer;
import dev.glisseo.izar.compiler.internal.selection.SelectionAnalyzer;
import dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer;
import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.OperationManifestInput;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import graphql.language.FragmentDefinition;
import graphql.language.OperationDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Entry point for generating Java sources from a schema and a set of operation files.
 *
 * <p>This is the seam that keeps compiler behavior out of the Maven lifecycle: {@code
 * izar-maven-plugin} binds parameters and phases, and calls this. A later Gradle adapter would
 * call the same method.
 *
 * <p>Every operation file is attempted, even after one fails, so a single {@code generate-sources}
 * run reports every problem at once rather than only the first file's.
 */
public final class OperationCompiler {

    /**
     * Generates sources into {@code outputDirectory} for every operation in {@code
     * operationFiles}, validated against {@code schemaFiles}, and returns the Apollo-compatible
     * manifest describing them.
     *
     * @param schemaFiles the checked-in or pinned schema; never fetched by introspection
     * @param operationFiles authored {@code .graphql} files, which are read and never rewritten;
     *     each is either exactly one named operation, or one or more reusable fragments referenced
     *     by name from operation files
     * @param outputDirectory root for generated sources, added as a compile source root by the
     *     calling build integration
     * @param basePackage package prefix for generated types
     * @return the manifest for this run's operations, in the order {@code operationFiles} named
     *     them; identical inputs always produce an identical manifest
     * @throws OperationGenerationException if the schema, or any operation file, is invalid or
     *     uses a feature not yet supported
     */
    public OperationManifest generate(
            List<Path> schemaFiles, List<Path> operationFiles, Path outputDirectory, String basePackage) {
        return generate(schemaFiles, operationFiles, outputDirectory, basePackage, List.of());
    }

    /**
     * Same as {@link #generate(List, List, Path, String)}, with an application's custom scalar
     * mappings. A custom scalar with no matching entry in {@code scalarMappings} fails generation
     * with the scalar's name and what to configure.
     *
     * @param scalarMappings explicit Java type and codec pairings for custom scalars this
     *     operation set uses; a GraphQL built-in needs no entry
     * @throws OperationGenerationException if the schema, or any operation file, is invalid or
     *     uses a feature not yet supported, or if {@code scalarMappings} configures the same
     *     GraphQL scalar name twice
     */
    public OperationManifest generate(
            List<Path> schemaFiles,
            List<Path> operationFiles,
            Path outputDirectory,
            String basePackage,
            List<ScalarMapping> scalarMappings) {
        GraphQLSchema schema = SchemaLoader.load(schemaFiles);
        FragmentLibrary fragmentLibrary = FragmentLibrary.parse(operationFiles);
        ScalarMappingRegistry scalarMappingRegistry = ScalarMappingRegistry.of(scalarMappings);

        Map<String, FragmentInterface> eligibleFragments = new LinkedHashMap<>();
        for (FragmentDefinition fragment : fragmentLibrary.fragmentsByName().values()) {
            FragmentInterfaceAnalyzer.analyze(schema, fragment, scalarMappingRegistry)
                    .ifPresent(fragmentInterface -> eligibleFragments.put(fragment.getName(), fragmentInterface));
        }
        for (FragmentInterface fragmentInterface : eligibleFragments.values()) {
            writeSource(
                    outputDirectory,
                    basePackage,
                    fragmentInterface.javaTypeName(),
                    SourceGenerator.generateFragmentInterface(basePackage, fragmentInterface));
        }

        List<String> diagnostics = new ArrayList<>();
        List<ManifestOperation> manifestOperations = new ArrayList<>();
        for (Path operationFile : fragmentLibrary.operationFiles()) {
            try {
                manifestOperations.add(
                        generateOne(
                                schema,
                                operationFile,
                                fragmentLibrary,
                                outputDirectory,
                                basePackage,
                                scalarMappingRegistry,
                                eligibleFragments));
            } catch (OperationGenerationException e) {
                diagnostics.addAll(e.diagnostics());
            }
        }

        if (!diagnostics.isEmpty()) {
            throw new OperationGenerationException(diagnostics);
        }

        return OperationManifest.of(manifestOperations);
    }

    /** Generates Java classes from an existing manifest without changing its IDs or documents. */
    public void generateFromManifest(
            List<Path> schemaFiles,
            Path manifestFile,
            Path outputDirectory,
            String basePackage,
            List<ScalarMapping> scalarMappings) {
        compileManifest(schemaFiles, manifestFile, outputDirectory, basePackage, scalarMappings);
    }

    /** Checks whether every operation in a manifest can generate Java, without writing source files. */
    public void validateManifest(
            List<Path> schemaFiles, Path manifestFile, String basePackage, List<ScalarMapping> scalarMappings) {
        compileManifest(schemaFiles, manifestFile, null, basePackage, scalarMappings);
    }

    /** Checks a manifest against an already loaded schema without writing source files. */
    public void validateManifest(
            GraphQLSchema schema, Path manifestFile, String basePackage, List<ScalarMapping> scalarMappings) {
        compileManifest(schema, manifestFile, null, basePackage, scalarMappings);
    }

    private void compileManifest(
            List<Path> schemaFiles,
            Path manifestFile,
            Path outputDirectory,
            String basePackage,
            List<ScalarMapping> scalarMappings) {
        compileManifest(SchemaLoader.load(schemaFiles), manifestFile, outputDirectory, basePackage, scalarMappings);
    }

    private void compileManifest(
            GraphQLSchema schema,
            Path manifestFile,
            @Nullable Path outputDirectory,
            String basePackage,
            List<ScalarMapping> scalarMappings) {
        OperationManifestInput manifest;
        try {
            manifest = OperationManifestInput.fromJson(Files.readString(manifestFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read operation manifest " + manifestFile, e);
        } catch (InvalidManifestException e) {
            throw new InvalidManifestException(manifestFile + ": " + e.getMessage(), e);
        }
        ScalarMappingRegistry mappings = ScalarMappingRegistry.of(scalarMappings);
        List<OperationParser.ParsedOperation> parsedOperations = new ArrayList<>();
        Map<String, FragmentDefinition> allFragments = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        for (OperationManifestInput.Operation entry : manifest.operations()) {
            try {
                OperationParser.ParsedOperation parsed =
                        OperationParser.parseManifestOperation(schema, manifestFile, entry);
                parsedOperations.add(parsed);
                for (FragmentDefinition fragment : parsed.fragmentsByName().values()) {
                    FragmentDefinition previous = allFragments.putIfAbsent(fragment.getName(), fragment);
                    if (previous != null
                            && !graphql.language.AstPrinter.printAstCompact(previous)
                                    .equals(graphql.language.AstPrinter.printAstCompact(fragment))) {
                        diagnostics.add(
                                manifestFile + ": operations use different definitions for fragment '" + fragment.getName() + "'.");
                    }
                }
            } catch (OperationGenerationException e) {
                diagnostics.addAll(e.diagnostics());
            }
        }
        if (!diagnostics.isEmpty()) {
            throw new OperationGenerationException(diagnostics);
        }

        Map<String, OperationManifestInput.Operation> operationEntriesByType = new LinkedHashMap<>();
        for (int i = 0; i < parsedOperations.size(); i++) {
            OperationParser.ParsedOperation parsed = parsedOperations.get(i);
            OperationManifestInput.Operation entry = manifest.operations().get(i);
            String generatedType = parsed.operationName() + operationKind(parsed.definition()).javaTypeSuffix();
            OperationManifestInput.Operation previous = operationEntriesByType.putIfAbsent(generatedType, entry);
            if (previous != null) {
                diagnostics.add(
                        manifestFile + ": operations '" + previous.name() + "' (id '" + previous.id() + "') and '"
                                + entry.name() + "' (id '" + entry.id() + "') both generate Java type '" + generatedType + "'.");
            }
        }
        if (!diagnostics.isEmpty()) {
            throw new OperationGenerationException(diagnostics);
        }

        Map<String, FragmentInterface> eligibleFragments = new LinkedHashMap<>();
        for (FragmentDefinition fragment : allFragments.values()) {
            FragmentInterfaceAnalyzer.analyze(schema, fragment, mappings)
                    .ifPresent(value -> eligibleFragments.put(fragment.getName(), value));
        }
        if (outputDirectory != null) {
            for (FragmentInterface fragmentInterface : eligibleFragments.values()) {
                writeSource(
                        outputDirectory,
                        basePackage,
                        fragmentInterface.javaTypeName(),
                        SourceGenerator.generateFragmentInterface(basePackage, fragmentInterface));
            }
        }

        List<String> generationDiagnostics = new ArrayList<>();
        for (int i = 0; i < parsedOperations.size(); i++) {
            OperationManifestInput.Operation entry = manifest.operations().get(i);
            OperationParser.ParsedOperation parsed = parsedOperations.get(i);
            try {
                generateManifestOperation(
                        schema, manifestFile, entry, parsed, outputDirectory, basePackage, mappings, eligibleFragments);
            } catch (OperationGenerationException e) {
                generationDiagnostics.addAll(e.diagnostics());
            }
        }
        if (!generationDiagnostics.isEmpty()) {
            throw new OperationGenerationException(generationDiagnostics);
        }
    }

    private void generateManifestOperation(
            GraphQLSchema schema,
            Path manifestFile,
            OperationManifestInput.Operation entry,
            OperationParser.ParsedOperation parsed,
            Path outputDirectory,
            String basePackage,
            ScalarMappingRegistry scalarMappings,
            Map<String, FragmentInterface> eligibleFragments) {
        generateParsedOperation(
                schema,
                manifestFile,
                parsed,
                outputDirectory,
                basePackage,
                scalarMappings,
                eligibleFragments,
                entry.id());
    }

    private ManifestOperation generateOne(
            GraphQLSchema schema,
            Path operationFile,
            FragmentLibrary fragmentLibrary,
            Path outputDirectory,
            String basePackage,
            ScalarMappingRegistry scalarMappings,
            Map<String, FragmentInterface> eligibleFragments) {
        OperationParser.ParsedOperation parsed = OperationParser.parse(schema, operationFile, fragmentLibrary);
        GeneratedOperation generated = generateParsedOperation(
                schema,
                operationFile,
                parsed,
                outputDirectory,
                basePackage,
                scalarMappings,
                eligibleFragments,
                null);
        return new ManifestOperation(
                generated.operationId(),
                generated.documentText(),
                parsed.operationName(),
                generated.operationKind().manifestType());
    }

    private GeneratedOperation generateParsedOperation(
            GraphQLSchema schema,
            Path sourceFile,
            OperationParser.ParsedOperation parsed,
            @Nullable Path outputDirectory,
            String basePackage,
            ScalarMappingRegistry scalarMappings,
            Map<String, FragmentInterface> eligibleFragments,
            @Nullable String suppliedOperationId) {
        GraphQlOperationKind operationKind = operationKind(parsed.definition());
        GraphQLObjectType rootType = switch (operationKind) {
            case QUERY -> schema.getQueryType();
            case MUTATION -> schema.getMutationType();
            case SUBSCRIPTION -> schema.getSubscriptionType();
        };
        if (rootType == null) {
            // Already effectively unreachable once a mutation field selection validates, but
            // guarded here too so this class never depends on that ordering.
            throw new OperationGenerationException(
                    sourceFile
                            + ": operation '"
                            + parsed.operationName()
                            + "' is a "
                            + operationKind.manifestType()
                            + " operation, but the schema declares no "
                            + operationKind.manifestType()
                            + " type.");
        }

        String className = parsed.operationName() + operationKind.javaTypeSuffix();
        if (eligibleFragments.containsKey(className)) {
            throw new OperationGenerationException(
                    sourceFile
                            + ": operation '"
                            + parsed.operationName()
                            + "' generates the Java type '"
                            + className
                            + "', which collides with the generated interface for a fragment named '"
                            + className
                            + "'. Rename the fragment or the operation.");
        }

        Set<String> usedTypeNames = new HashSet<>();
        usedTypeNames.add("Data");
        SelectionAnalyzer.Result selection = SelectionAnalyzer.analyze(
                schema, sourceFile, parsed, rootType, usedTypeNames, scalarMappings, eligibleFragments);
        if (suppliedOperationId != null && !selection.discriminatorInjections().isEmpty()) {
            throw new OperationGenerationException(
                    sourceFile + ": operation '" + parsed.operationName() + "' (id '" + suppliedOperationId
                            + "') needs an unconditional __typename selection to generate polymorphic Java types."
                            + " Add an unaliased __typename to the shared selection or to every concrete branch."
                            + " Manifest documents and IDs are preserved and will not be rewritten.");
        }
        VariableAnalyzer.Result variables =
                VariableAnalyzer.analyze(
                        schema,
                        sourceFile,
                        parsed.operationName(),
                        parsed.definition().getVariableDefinitions(),
                        usedTypeNames,
                        scalarMappings);

        String documentText =
                suppliedOperationId != null || selection.discriminatorInjections().isEmpty()
                        ? parsed.documentText()
                        : DiscriminatorInjection.buildDocumentText(
                                parsed.definition(), parsed.referencedFragments(), selection.discriminatorInjections());

        String operationId = suppliedOperationId != null ? suppliedOperationId : ManifestOperation.idFor(documentText);
        String source =
                SourceGenerator.generate(
                        basePackage,
                        className,
                        parsed.operationName(),
                        documentText,
                        operationId,
                        operationKind,
                        selection.root(),
                        selection.enumTypes(),
                        variables);

        if (outputDirectory != null) {
            writeSource(outputDirectory, basePackage, className, source);
        }
        return new GeneratedOperation(documentText, operationId, operationKind);
    }

    private record GeneratedOperation(String documentText, String operationId, GraphQlOperationKind operationKind) {}

    private static GraphQlOperationKind operationKind(OperationDefinition definition) {
        return switch (definition.getOperation()) {
            case QUERY -> GraphQlOperationKind.QUERY;
            case MUTATION -> GraphQlOperationKind.MUTATION;
            case SUBSCRIPTION -> GraphQlOperationKind.SUBSCRIPTION;
        };
    }

    private void writeSource(Path outputDirectory, String basePackage, String className, String source) {
        Path packageDirectory = outputDirectory;
        for (String segment : basePackage.split("\\.")) {
            packageDirectory = packageDirectory.resolve(segment);
        }
        Path javaFile = packageDirectory.resolve(className + ".java");
        try {
            Files.createDirectories(packageDirectory);
            Files.writeString(javaFile, source);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write generated source " + javaFile, e);
        }
    }
}
