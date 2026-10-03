package dev.glisseo.izar.manifest.assembly;

import java.util.Locale;

/**
 * Rejects a version range or meta-version ({@code LATEST}, {@code RELEASE}) wherever a build-tool
 * adapter needs one exact, pinned version for a client release's manifest artifact.
 *
 * <p>Maven and Gradle both accept a range or a meta-version wherever they accept a version, and
 * silently choose whichever member currently satisfies it. Each client release has one exact
 * manifest version, so a range cannot be used to select a release. Adapters run this check before
 * they build a deployment or resolution request, so a misconfigured version fails the build with a
 * clear diagnostic instead of resolving to an unpinned, unpredictable artifact.
 */
public final class ExactVersion {

    private ExactVersion() {}

    /**
     * @param parameterName the configuration option the version came from, quoted in the message
     * @throws IllegalArgumentException if {@code version} is a range (contains any of {@code
     *     [](),}) or the case-insensitive meta-version {@code LATEST} or {@code RELEASE}
     */
    public static void require(String version, String parameterName) {
        String upperCase = version.toUpperCase(Locale.ROOT);
        boolean isMetaVersion = upperCase.equals("LATEST") || upperCase.equals("RELEASE");
        boolean isRange = version.chars().anyMatch(c -> "[](),".indexOf(c) >= 0);
        if (isMetaVersion || isRange) {
            throw new IllegalArgumentException(parameterName + " '" + version + "' is not an exact version. "
                    + "A client release's manifest version must be pinned exactly, never a range or a "
                    + "meta-version like LATEST or RELEASE, so a resolved release can never be silently "
                    + "replaced by a newer one.");
        }
    }
}
