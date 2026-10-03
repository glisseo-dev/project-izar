package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.source.LocalManifestSource;
import dev.glisseo.izar.manifest.source.ManifestSource;
/**
 * One entry in a {@link ReleaseSelection}: a client release identity paired with the source that
 * supplies its manifest.
 *
 * <p>{@code source} is a {@link ManifestSource}, not a file path, so a {@link ReleaseAssembler}
 * stays agnostic to where the release's manifest actually lives. Callers resolve local
 * files through {@link LocalManifestSource}; repository resolution is a separate build-tool
 * integration layered on top, never part of local assembly itself.
 */
public record SelectedRelease(ClientRelease release, ManifestSource source) {

    public SelectedRelease {
        if (release == null) {
            throw new AssemblyException("A selected release must name a client release.");
        }
        if (source == null) {
            throw new AssemblyException("A selected release must supply a manifest source.");
        }
    }
}
