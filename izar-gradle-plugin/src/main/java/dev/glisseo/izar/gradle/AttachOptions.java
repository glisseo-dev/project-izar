package dev.glisseo.izar.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code izar { attach { ... } }} block, the defaults for {@link IzarAttachTask}. Attaching is
 * opt-in, like declaring the {@code attach} execution in Maven: set {@code enabled = true}.
 */
public abstract class AttachOptions {

    /** Registers {@code izarAttach} and adds its manifest to every {@code maven-publish} publication. */
    public abstract Property<Boolean> getEnabled();

    public abstract RegularFileProperty getManifestFile();

    /** Defaults to {@code izar-manifest}. */
    public abstract Property<String> getClassifier();

    /** Defaults to {@code json}. */
    public abstract Property<String> getExtension();

    public abstract Property<Boolean> getSkip();
}
