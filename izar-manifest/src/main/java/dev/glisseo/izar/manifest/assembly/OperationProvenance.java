package dev.glisseo.izar.manifest.assembly;

import java.util.List;

/**
 * Every client release that contributed one assembled operation.
 *
 * <p>A {@link ReleaseAssembler} deduplicates identical operation entries by ID, but never discards
 * which selected releases actually required each one: a requirements engineer investigating a
 * single operation ID must still be able to name every release depending on it, not just the first
 * one assembly happened to see.
 *
 * @param operationId the {@link ManifestOperation#id()} this provenance describes
 * @param releases every contributing release, in {@link ClientRelease#compareTo} order; never empty
 */
public record OperationProvenance(String operationId, List<ClientRelease> releases) {

    public OperationProvenance {
        if (operationId == null || operationId.isBlank()) {
            throw new AssemblyException("An operation provenance entry's operationId must not be blank.");
        }
        if (releases == null || releases.isEmpty()) {
            throw new AssemblyException("An operation provenance entry must name at least one contributing release.");
        }
        releases = List.copyOf(releases);
    }
}
