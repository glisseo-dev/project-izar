package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.ManifestSnapshot;
import dev.glisseo.izar.manifest.ManifestSourceException;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.source.LocalManifestSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Assembles a {@link ReleaseSelection} into one union manifest with provenance, resolving every
 * named release from local files and failing atomically on any missing, invalid, or conflicting
 * input.
 *
 * <p>This is the build-tool-independent seam Maven adapters and the standalone command both call,
 * the same way {@link dev.glisseo.izar.compiler} generation logic and {@link ManifestPublisher}
 * stay reusable across build integrations. It performs no network lookup and depends on no
 * repository SDK: every {@link SelectedRelease} names a {@link ManifestSource} the caller already
 * resolved, typically a {@link LocalManifestSource}.
 *
 * <p>Resolution happens before assembly, and every selected release is resolved even after one
 * fails, so a caller sees every problem in a broken selection in a single run rather than fixing
 * and rerunning one release at a time. Assembly itself never returns a partial result: it either
 * produces a complete {@link AssembledManifest} or throws {@link AssemblyException}.
 */
public final class ReleaseAssembler {

    /**
     * Assembles {@code selection} into its union manifest.
     *
     * @throws AssemblyException if any selected release is missing, unreadable, not a well-formed
     *     manifest, reused with different content, or if two releases disagree about one operation
     *     ID's name or type
     */
    public AssembledManifest assemble(ReleaseSelection selection) {
        List<SelectedRelease> ordered = selection.releases().stream()
                .sorted(Comparator.comparing(SelectedRelease::release))
                .toList();

        Map<ClientRelease, ManifestSnapshot> resolved = new TreeMap<>();
        List<String> errors = new ArrayList<>();

        for (SelectedRelease selectedRelease : ordered) {
            ManifestSnapshot snapshot;
            try {
                snapshot = selectedRelease.source().load();
            } catch (ManifestSourceException | InvalidManifestException e) {
                errors.add("Release '" + selectedRelease.release() + "': " + e.getMessage());
                continue;
            }
            ManifestSnapshot existing = resolved.get(selectedRelease.release());
            if (existing == null) {
                resolved.put(selectedRelease.release(), snapshot);
            } else if (!existing.revision().equals(snapshot.revision())) {
                errors.add("Release '" + selectedRelease.release()
                        + "' was selected more than once with different content (revisions '"
                        + existing.revision() + "' and '" + snapshot.revision() + "').");
            }
        }

        if (!errors.isEmpty()) {
            throw failure(selection, errors);
        }

        Map<String, ManifestOperation> operationsById = new TreeMap<>();
        Map<String, TreeSet<ClientRelease>> contributorsById = new TreeMap<>();

        for (Map.Entry<ClientRelease, ManifestSnapshot> entry : resolved.entrySet()) {
            for (ManifestOperation operation : entry.getValue().manifest().operations()) {
                ManifestOperation existingOperation = operationsById.get(operation.id());
                if (existingOperation == null) {
                    operationsById.put(operation.id(), operation);
                } else if (!existingOperation.equals(operation)) {
                    errors.add("Operation '" + operation.id() + "' has conflicting entries across selected"
                            + " releases: one names it '" + existingOperation.name() + "' (" + existingOperation.type()
                            + "), another '" + operation.name() + "' (" + operation.type() + ").");
                    continue;
                }
                contributorsById.computeIfAbsent(operation.id(), id -> new TreeSet<>()).add(entry.getKey());
            }
        }

        if (!errors.isEmpty()) {
            throw failure(selection, errors);
        }

        List<ManifestOperation> operations = List.copyOf(operationsById.values());
        List<OperationProvenance> provenance = operations.stream()
                .map(operation -> new OperationProvenance(operation.id(), List.copyOf(contributorsById.get(operation.id()))))
                .toList();
        List<LockedRelease> lockedReleases = resolved.entrySet().stream()
                .map(entry -> new LockedRelease(
                        entry.getKey(), entry.getValue().revision(), entry.getValue().manifest().operations().size()))
                .toList();

        return new AssembledManifest(
                OperationManifest.of(operations),
                provenance,
                new InputLock(selection.graphId(), selection.environment(), lockedReleases));
    }

    private static AssemblyException failure(ReleaseSelection selection, List<String> errors) {
        return new AssemblyException("Could not assemble selection '" + selection.graphId() + "/"
                + selection.environment() + "': " + String.join(" ", errors));
    }
}
