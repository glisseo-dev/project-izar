package dev.glisseo.izar.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/**
 * The {@code izar { deploy { ... } }} block behind {@code izarDeploy}: uploads a manifest as a
 * versioned Maven artifact under coordinates independent of this project's own.
 *
 * <p>There is no credential option. For an {@code http} or {@code https} repository, Gradle reads
 * the {@code <repositoryId>Username} and {@code <repositoryId>Password} properties from {@code
 * ~/.gradle/gradle.properties}, {@code ORG_GRADLE_PROJECT_*} environment variables, or the command
 * line, like any other {@code maven-publish} repository.
 */
public abstract class DeployOptions {

    /** A manifest written by {@code izarGenerate}, or collected by another Apollo-compatible pipeline. */
    public abstract RegularFileProperty getManifestFile();

    /** Required. */
    public abstract Property<String> getGroupId();

    /** Typically the client name. Required. */
    public abstract Property<String> getArtifactId();

    /** Typically the manifest version. Required. */
    public abstract Property<String> getVersion();

    /** Defaults to {@code izar-manifest}. */
    public abstract Property<String> getClassifier();

    /** Defaults to {@code json}. */
    public abstract Property<String> getExtension();

    /** Names the repository and prefixes its credential properties. Required. */
    public abstract Property<String> getRepositoryId();

    /** Required. */
    public abstract Property<String> getRepositoryUrl();

    public abstract Property<Boolean> getSkip();
}
