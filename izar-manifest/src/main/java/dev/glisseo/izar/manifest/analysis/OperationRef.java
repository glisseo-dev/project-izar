package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.assembly.OperationProvenance;
import java.util.List;
import java.util.Map;

/**
 * One contributing client release's operation that references a schema type, field, or argument.
 *
 * <p>Carrying {@code operationId} and {@code manifestVersion} alongside the client name and
 * operation's own name is what lets a finding link straight to the exact release and stored
 * document behind it, replacing the earlier {@code (clientName, operationName)} identity that
 * required probing every release of a client newest-first to guess which one a name still matched.
 * An operation shared verbatim across several releases (see {@link OperationProvenance}) is
 * represented as one {@code OperationRef} per contributing release, so no association is lost.
 */
public record OperationRef(String operationId, String operationName, String clientName, String manifestVersion) {

    /**
     * Every {@link OperationRef} for {@code operation}'s contributing releases, looked up from
     * {@code provenanceById} by {@link ManifestOperation#id()}. Empty if {@code operation} has no
     * entry in {@code provenanceById}.
     */
    public static List<OperationRef> refsFor(ManifestOperation operation, Map<String, OperationProvenance> provenanceById) {
        OperationProvenance operationProvenance = provenanceById.get(operation.id());
        return operationProvenance == null
                ? List.of()
                : operationProvenance.releases().stream()
                        .map(release -> new OperationRef(operation.id(), operation.name(), release.clientName(), release.manifestVersion()))
                        .toList();
    }
}
