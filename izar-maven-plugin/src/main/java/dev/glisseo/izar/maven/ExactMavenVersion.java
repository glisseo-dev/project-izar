package dev.glisseo.izar.maven;

import java.util.Locale;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Rejects a Maven version range or meta-version ({@code LATEST}, {@code RELEASE}) wherever this
 * plugin's Maven-artifact exchange needs one exact, pinned version.
 *
 * <p>{@link DeployMojo} and {@link AssembleMojo} both resolve or deploy under a caller-supplied
 * version string that Aether itself accepts as a range or a meta-version without complaint,
 * silently choosing whichever member currently satisfies it. That is exactly the "no selected
 * release is skipped or replaced by a newer version" failure this project's release-selection
 * decisions rule out (see ADR 0027, decision 4): a client release's identity is one exact manifest
 * version, per ADR 0011, never a moving target. This check runs before either goal ever builds an
 * {@code Artifact} or {@code ArtifactRequest}, so a misconfigured version fails the build with a
 * clear diagnostic instead of resolving to an unpinned, unpredictable artifact.
 */
final class ExactMavenVersion {

    private ExactMavenVersion() {}

    /**
     * @throws MojoExecutionException if {@code version} is a range (contains any of {@code
     *     [](),}) or the case-insensitive meta-version {@code LATEST} or {@code RELEASE}
     */
    static void require(String version, String parameterName) throws MojoExecutionException {
        String upperCase = version.toUpperCase(Locale.ROOT);
        boolean isMetaVersion = upperCase.equals("LATEST") || upperCase.equals("RELEASE");
        boolean isRange = version.chars().anyMatch(c -> "[](),".indexOf(c) >= 0);
        if (isMetaVersion || isRange) {
            throw new MojoExecutionException(parameterName + " '" + version + "' is not an exact Maven version. "
                    + "A client release's manifest version must be pinned exactly, never a range or a "
                    + "meta-version like LATEST or RELEASE, so a resolved release can never be silently "
                    + "replaced by a newer one.");
        }
    }
}
