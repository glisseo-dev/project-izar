package dev.glisseo.izar.maven;

import dev.glisseo.izar.compiler.OperationCompiler;
import dev.glisseo.izar.compiler.OperationGenerationException;
import dev.glisseo.izar.compiler.ScalarMapping;
import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generates Java models, operation metadata, executable documents, and a manifest from a local
 * schema and the project's GraphQL operation files.
 *
 * <p>Bound to {@code generate-sources} so generated types are available to normal compilation
 * without a manual preparation step.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** A checked-in schema file or an unpacked pinned schema artifact. Never introspected. */
    @Parameter(property = "izar.schema", defaultValue = "${project.basedir}/src/main/graphql/schema.graphqls")
    private File schema;

    /** Directory scanned recursively for {@code .graphql} operation files. */
    @Parameter(property = "izar.operations", defaultValue = "${project.basedir}/src/main/graphql")
    private File operationDirectory;

    /** Existing Apollo-compatible manifest used instead of the operation directory. */
    @Parameter(property = "izar.manifestInput")
    private File manifestInput;

    /** Where generated sources are written and registered as a compile source root. */
    @Parameter(property = "izar.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/izar")
    private File outputDirectory;

    /**
     * Where the Apollo-compatible operation manifest is written. Not a compile source root or a
     * resource directory: {@link PublishMojo}'s default {@code manifestFile} reads this exact path.
     */
    @Parameter(property = "izar.manifestFile", defaultValue = "${project.build.directory}/izar/manifest.json")
    private File manifestFile;

    /** Package prefix for generated types. */
    @Parameter(property = "izar.basePackage", required = true)
    private String basePackage;

    @Parameter(property = "izar.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Explicit Java type and codec pairings for custom scalars this project's operations use. A
     * GraphQL built-in needs no entry; a custom scalar with none configured fails generation.
     */
    @Parameter
    private List<ScalarMappingParameter> scalarMappings = new ArrayList<>();

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Izar generation skipped.");
            return;
        }

        if (!schema.isFile()) {
            throw new MojoExecutionException("No schema file at " + schema);
        }
        List<ScalarMapping> scalarMappings =
                this.scalarMappings.stream()
                        .map(
                                p ->
                                        new ScalarMapping(
                                                p.getGraphqlScalarName(), p.getJavaTypeName(), p.getCodecClassName()))
                        .toList();

        if (manifestInput != null) {
            if (!manifestInput.isFile()) {
                throw new MojoExecutionException("No operation manifest at " + manifestInput);
            }
            project.addCompileSourceRoot(outputDirectory.getPath());
            try {
                new OperationCompiler()
                        .generateFromManifest(
                                List.of(schema.toPath()),
                                manifestInput.toPath(),
                                outputDirectory.toPath(),
                                basePackage,
                                scalarMappings);
            } catch (OperationGenerationException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (InvalidManifestException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            getLog().info("Generated Java sources from operation manifest " + manifestInput);
            return;
        }

        Path operations = operationDirectory.toPath();
        if (!Files.isDirectory(operations)) {
            throw new MojoExecutionException("No operation directory at " + operations);
        }
        List<Path> operationFiles = graphqlFilesUnder(operations);
        if (operationFiles.isEmpty()) {
            getLog().warn("No .graphql operation files under " + operations + "; nothing to generate.");
            return;
        }

        // Registered before generation so a generation failure still leaves the source root
        // configured, keeping IDE import stable across a failed build.
        project.addCompileSourceRoot(outputDirectory.getPath());

        OperationManifest manifest;
        try {
            manifest =
                    new OperationCompiler()
                            .generate(
                                    List.of(schema.toPath()),
                                    operationFiles,
                                    outputDirectory.toPath(),
                                    basePackage,
                                    scalarMappings);
        } catch (OperationGenerationException e) {
            // Rethrown as a MojoExecutionException so Maven prints the diagnostic cleanly
            // instead of this tool's own stack trace.
            throw new MojoExecutionException(e.getMessage(), e);
        }

        writeManifest(manifest);
    }

    private void writeManifest(OperationManifest manifest) throws MojoExecutionException {
        Path manifestPath = manifestFile.toPath();
        try {
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath, manifest.toJson());
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write operation manifest " + manifestPath, e);
        }
        getLog().info(
                "Wrote operation manifest with " + manifest.operations().size() + " operation(s) to " + manifestPath);
    }

    /** Sorted so identical inputs reach the compiler in an identical order. */
    private static List<Path> graphqlFilesUnder(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".graphql"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read operation files under " + directory, e);
        }
    }
}
