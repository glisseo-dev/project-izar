package dev.glisseo.izar.manifest;

/**
 * A manifest as loaded from one {@link dev.glisseo.izar.manifest.source.ManifestSource} call, paired with a revision identifying
 * that particular load.
 *
 * <p>{@code revision} distinguishes one candidate from another independently of the manifest's own
 * content: a file source derives it from what it read, an HTTP source from what the controller
 * published. Comparing or ordering revisions across successive loads is a caller concern, not this
 * type's.
 *
 * @param revision a non-blank identifier for this particular snapshot
 * @param manifest the manifest this snapshot carries
 */
public record ManifestSnapshot(String revision, OperationManifest manifest) {

    public ManifestSnapshot {
        if (revision == null || revision.isBlank()) {
            throw new InvalidManifestException("A manifest snapshot's revision must not be blank.");
        }
        if (manifest == null) {
            throw new InvalidManifestException("A manifest snapshot's manifest must not be null.");
        }
    }
}
