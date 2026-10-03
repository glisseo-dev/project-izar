package dev.glisseo.izar.maven;

import dev.glisseo.izar.manifest.assembly.ExactVersion;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Rejects a Maven version range or meta-version ({@code LATEST}, {@code RELEASE}) wherever this
 * plugin's Maven-artifact exchange needs one exact, pinned version.
 *
 * <p>{@link DeployMojo} and {@link AssembleMojo} both resolve or deploy under a caller-supplied
 * version string that Aether itself accepts as a range or a meta-version without complaint. This
 * check runs before either goal builds an {@code Artifact} or {@code ArtifactRequest}. The rule
 * itself lives in {@link ExactVersion}, shared with the other build-tool adapters; this class only
 * turns its failure into a failed build.
 */
final class ExactMavenVersion {

    private ExactMavenVersion() {}

    /**
     * @throws MojoExecutionException if {@code version} is a range or the meta-version {@code
     *     LATEST} or {@code RELEASE}
     */
    static void require(String version, String parameterName) throws MojoExecutionException {
        try {
            ExactVersion.require(version, parameterName);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
