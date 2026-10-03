package dev.glisseo.izar.gradle;

import java.io.Serializable;

/**
 * A client release identity paired with the Maven coordinates that supply its manifest, as {@link
 * IzarAssembleTask} receives it.
 *
 * <p>{@code clientName} and {@code manifestVersion} are the domain identity {@link
 * dev.glisseo.izar.manifest.assembly.ClientRelease} carries. The other fields are the independent
 * coordinates the manifest was published under, which need not match any client project's own
 * coordinates.
 */
public record ReleaseEntry(
        String clientName,
        String manifestVersion,
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String extension)
        implements Serializable {

    /** The coordinates in {@code group:artifact:extension:classifier:version} order, as Maven reports them. */
    String coordinates() {
        return groupId + ":" + artifactId + ":" + extension + ":" + classifier + ":" + version;
    }

    /** The coordinates as a Gradle dependency notation: {@code group:artifact:version:classifier@extension}. */
    String dependencyNotation() {
        return groupId + ":" + artifactId + ":" + version + ":" + classifier + "@" + extension;
    }
}
