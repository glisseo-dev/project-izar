package dev.glisseo.izar.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code izar { publish { ... } }} block, the defaults for {@link IzarPublishTask}. There is
 * deliberately no password option: see {@link PublishCredentials}.
 */
public abstract class PublishOptions {

    /** A manifest written by {@code izarGenerate}, or collected by another Apollo-compatible pipeline. */
    public abstract RegularFileProperty getManifestFile();

    /** The controller's base URL. Required. */
    public abstract Property<String> getUrl();

    /** Required. */
    public abstract Property<String> getClientName();

    /** Required. */
    public abstract Property<String> getManifestVersion();

    /** Prefix of the {@code <prefix>Username} and {@code <prefix>Password} Gradle properties. */
    public abstract Property<String> getCredentialsId();

    public abstract Property<Boolean> getSkip();
}
