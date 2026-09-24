package dev.glisseo.izar.manifest.bundle;

import java.nio.file.Path;

/**
 * The currently installed revision under one {@link BundleInstaller} install root: which
 * content-addressed revision directory is active, and the files inside it.
 *
 * @param installRoot the install root {@link BundleInstaller#install} was called with
 * @param revisionId the installed revision's content-derived identifier
 */
public record InstalledBundle(Path installRoot, String revisionId) {

    public InstalledBundle {
        if (installRoot == null) {
            throw new BundleException("An installed bundle must name an install root.");
        }
        if (revisionId == null || revisionId.isBlank()) {
            throw new BundleException("An installed bundle's revisionId must not be blank.");
        }
    }

    /** This revision's own directory: {@code installRoot/revisions/revisionId}. */
    public Path directory() {
        return installRoot.resolve(BundleInstaller.REVISIONS_DIRECTORY).resolve(revisionId);
    }

    /** The installed, assembled manifest a runtime local manifest source would point at. */
    public Path manifestFile() {
        return directory().resolve("manifest.json");
    }

    public Path provenanceFile() {
        return directory().resolve("provenance.json");
    }

    public Path lockFile() {
        return directory().resolve("lock.json");
    }
}
