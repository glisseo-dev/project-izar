package dev.glisseo.izar.gradle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code izar { ... }} block. Top-level options configure the default {@code izarGenerate}
 * task; the nested blocks configure {@code izarPublish}, {@code izarAttach}, {@code izarDeploy},
 * and {@code izarAssemble}. Every option has the same name and default as the Maven plugin's
 * parameter, and a Gradle property named {@code izar.<option>} (for example {@code
 * -Pizar.schema=...}) overrides the default the way {@code -Dizar.schema=...} does in Maven.
 */
public abstract class IzarExtension {

    private final NamedDomainObjectContainer<ScalarMappingSpec> scalarMappings;

    @Inject
    public IzarExtension(ObjectFactory objects) {
        this.scalarMappings = objects.domainObjectContainer(ScalarMappingSpec.class);
    }

    /** A checked-in schema file or an unpacked pinned schema artifact. Never introspected. */
    public abstract RegularFileProperty getSchema();

    /** Directory scanned recursively for {@code .graphql} operation files. */
    public abstract DirectoryProperty getOperationDirectory();

    /** Existing Apollo-compatible manifest used instead of the operation directory. */
    public abstract RegularFileProperty getManifestInput();

    /** Where generated sources are written and registered as a Java source directory. */
    public abstract DirectoryProperty getOutputDirectory();

    /** Where the Apollo-compatible operation manifest is written. */
    public abstract RegularFileProperty getManifestFile();

    /** Package prefix for generated types. Required. */
    public abstract Property<String> getBasePackage();

    /** {@code IZAR} (default) or {@code JACKSON}. */
    public abstract Property<String> getGenerationMode();

    public abstract Property<Boolean> getSkip();

    /**
     * Explicit Java type and codec pairings for custom scalars this project's operations use. A
     * GraphQL built-in needs no entry; a custom scalar with none configured fails generation.
     */
    public NamedDomainObjectContainer<ScalarMappingSpec> getScalarMappings() {
        return scalarMappings;
    }

    public void scalarMappings(Action<? super NamedDomainObjectContainer<ScalarMappingSpec>> action) {
        action.execute(scalarMappings);
    }

    @Nested
    public abstract PublishOptions getPublish();

    public void publish(Action<? super PublishOptions> action) {
        action.execute(getPublish());
    }

    @Nested
    public abstract AttachOptions getAttach();

    public void attach(Action<? super AttachOptions> action) {
        action.execute(getAttach());
    }

    @Nested
    public abstract DeployOptions getDeploy();

    public void deploy(Action<? super DeployOptions> action) {
        action.execute(getDeploy());
    }

    @Nested
    public abstract AssembleOptions getAssemble();

    public void assemble(Action<? super AssembleOptions> action) {
        action.execute(getAssemble());
    }
}
