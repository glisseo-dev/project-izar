package dev.glisseo.izar.gradle;

import dev.glisseo.izar.compiler.GenerationMode;
import dev.glisseo.izar.compiler.OperationCompiler;
import dev.glisseo.izar.compiler.OperationGenerationException;
import dev.glisseo.izar.compiler.ScalarMapping;
import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Generates Java models, operation metadata, executable documents, and a manifest from a local
 * schema and GraphQL operation files. {@link IzarPlugin} registers it as {@code izarGenerate};
 * register more instances for a second GraphQL server, each with its own package, output
 * directory, and manifest file.
 *
 * <p>Every option defaults to the matching {@code izar { ... }} extension value, so an instance
 * only sets what differs.
 */
@DisableCachingByDefault(because = "Generation is fast and its inputs are not declared as relocatable")
public abstract class IzarGenerateTask extends DefaultTask {

    /** A checked-in schema file or an unpacked pinned schema artifact. Never introspected. */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSchema();

    /** Directory scanned recursively for {@code .graphql} operation files. */
    @Internal
    public abstract DirectoryProperty getOperationDirectory();

    /** Existing Apollo-compatible manifest used instead of the operation directory. */
    @Optional
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getManifestInput();

    /** Where generated sources are written; the plugin registers it as a Java source directory. */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** Where the Apollo-compatible operation manifest is written in operation-file mode. */
    @OutputFile
    public abstract RegularFileProperty getManifestFile();

    /** Package prefix for generated types. */
    @Input
    public abstract Property<String> getBasePackage();

    /** {@code IZAR} or {@code JACKSON}, case-insensitive. */
    @Input
    public abstract Property<String> getGenerationMode();

    @Input
    public abstract ListProperty<ScalarMappingEntry> getScalarMappings();

    @Input
    public abstract Property<Boolean> getSkip();

    @Inject
    protected abstract ObjectFactory getObjects();

    public IzarGenerateTask() {
        setGroup("izar");
        setDescription("Generates Java sources and an operation manifest from GraphQL operations.");
        SkipOption.apply(this, getSkip(), "izar.skip", "Izar generation skipped.");
    }

    /** The operation files, as an input so editing one reruns generation. */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    protected FileTree getOperationFiles() {
        return getObjects().fileTree().from(getOperationDirectory()).matching(p -> p.include("**/*.graphql"));
    }

    /** Adds one custom scalar mapping to this task, on top of any the extension supplies. */
    public void scalarMapping(String graphqlScalarName, String javaTypeName, String codecClassName) {
        getScalarMappings().add(new ScalarMappingEntry(graphqlScalarName, javaTypeName, codecClassName));
    }

    @TaskAction
    void generate() {
        Path schema = getSchema().get().getAsFile().toPath();
        Path outputDirectory = getOutputDirectory().get().getAsFile().toPath();
        String basePackage = getBasePackage().get();
        GenerationMode mode = parseMode(getGenerationMode().get());
        List<ScalarMapping> scalarMappings = getScalarMappings().get().stream()
                .map(e -> new ScalarMapping(e.graphqlScalarName(), e.javaTypeName(), e.codecClassName()))
                .toList();

        if (getManifestInput().isPresent()) {
            Path manifestInput = getManifestInput().get().getAsFile().toPath();
            try {
                new OperationCompiler()
                        .generateFromManifest(
                                List.of(schema), manifestInput, outputDirectory, basePackage, scalarMappings, mode);
            } catch (OperationGenerationException | InvalidManifestException e) {
                throw new GradleException(e.getMessage(), e);
            }
            getLogger().lifecycle("Generated Java sources from operation manifest " + manifestInput);
            return;
        }

        File operationDirectory = getOperationDirectory().get().getAsFile();
        if (!operationDirectory.isDirectory()) {
            throw new GradleException("No operation directory at " + operationDirectory);
        }
        List<Path> operationFiles = getOperationFiles().getFiles().stream()
                .map(File::toPath)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        if (operationFiles.isEmpty()) {
            getLogger().warn("No .graphql operation files under " + operationDirectory + "; nothing to generate.");
            return;
        }

        OperationManifest manifest;
        try {
            manifest = new OperationCompiler()
                    .generate(List.of(schema), operationFiles, outputDirectory, basePackage, scalarMappings, mode);
        } catch (OperationGenerationException e) {
            throw new GradleException(e.getMessage(), e);
        }
        writeManifest(manifest);
    }

    private void writeManifest(OperationManifest manifest) {
        Path manifestPath = getManifestFile().get().getAsFile().toPath();
        try {
            Files.createDirectories(manifestPath.getParent());
            Files.writeString(manifestPath, manifest.toJson());
        } catch (IOException e) {
            throw new GradleException("Could not write operation manifest " + manifestPath, e);
        }
        getLogger().lifecycle(
                "Wrote operation manifest with " + manifest.operations().size() + " operation(s) to " + manifestPath);
    }

    private static GenerationMode parseMode(String value) {
        try {
            return GenerationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new GradleException("izar.generationMode '" + value + "' is not one of "
                    + Arrays.toString(GenerationMode.values()) + ".");
        }
    }
}
