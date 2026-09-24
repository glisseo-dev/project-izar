package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.source.LocalManifestSource;
import dev.glisseo.izar.manifest.source.ManifestSource;
/**
 * One release as it was actually resolved during assembly: its identity, the exact content it
 * carried, and how many operations it contributed.
 *
 * @param release the resolved release's identity
 * @param revision the resolved manifest's digest, as its {@link ManifestSource} reported it (a
 *     {@link LocalManifestSource} reports the SHA-256 of the file's raw bytes)
 * @param operationCount how many operations that release's own manifest carried, before union or
 *     deduplication against any other release
 */
public record LockedRelease(ClientRelease release, String revision, int operationCount) {

    public LockedRelease {
        if (release == null) {
            throw new AssemblyException("A locked release must name a client release.");
        }
        if (revision == null || revision.isBlank()) {
            throw new AssemblyException("A locked release's revision must not be blank.");
        }
    }
}
