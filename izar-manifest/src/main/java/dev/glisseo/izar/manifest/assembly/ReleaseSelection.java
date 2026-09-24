package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.source.ManifestSources;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A versioned, explicit selection of client releases to assemble: a logical graph or enforcing
 * endpoint identity, the environment it applies to, and the exact releases a release engineer has
 * chosen to support.
 *
 * <p>{@code environment} is explicit metadata the caller states, never evidence that a deployment
 * happened; nothing here infers scope from runtime activity. Selecting several releases of one
 * client, including retained rollback versions, is the ordinary case, not a special one: a {@link
 * ReleaseAssembler} unions every named release. Retirement is likewise just a smaller selection on
 * the next assembly call, never an operation this type performs itself.
 *
 * <p>"Versioned" describes what this selection pins, not this type's own JSON schema: every named
 * release carries its exact {@link ClientRelease#manifestVersion}, and {@link ReleaseAssembler}
 * records the exact resolved digest behind each one in the {@link InputLock} it produces. There is
 * no separate selection-format version field to evolve, since {@link #fromJson} is this module's
 * only reader of the selection document shape.
 *
 * @param graphId the logical graph or enforcing endpoint this selection targets
 * @param environment the environment this selection applies to, for example {@code "production"}
 * @param releases every client release this selection explicitly supports; never empty
 */
public record ReleaseSelection(String graphId, String environment, List<SelectedRelease> releases) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public ReleaseSelection {
        requireNonBlank(graphId, "graphId");
        requireNonBlank(environment, "environment");
        if (releases == null || releases.isEmpty()) {
            throw new AssemblyException("A release selection must name at least one client release.");
        }
        releases = List.copyOf(releases);
    }

    /**
     * Parses a selection document: {@code graph}, {@code environment}, and a {@code releases} array
     * of {@code clientName}, {@code manifestVersion}, and {@code manifestFile} entries. Each {@code
     * manifestFile} is resolved against {@code baseDirectory} (typically the selection file's own
     * parent directory) and read through a local source factory, so this stays a purely local
     * operation: no repository lookup, no controller call.
     *
     * @throws AssemblyException if {@code json} is not a well-formed selection document
     */
    public static ReleaseSelection fromJson(String json, Path baseDirectory) {
        SelectionDocument document;
        try {
            document = JSON.readValue(json, SelectionDocument.class);
        } catch (JacksonException e) {
            throw new AssemblyException("Could not parse release selection JSON: " + e.getMessage(), e);
        }
        if (document == null || document.releases() == null) {
            throw new AssemblyException("A release selection document must list at least one release.");
        }
        Path normalizedBaseDirectory = baseDirectory.toAbsolutePath().normalize();
        List<SelectedRelease> selected = document.releases().stream()
                .map(entry -> {
                    if (entry == null) {
                        throw new AssemblyException("A release selection must not contain a null release entry.");
                    }
                    if (entry.manifestFile() == null || entry.manifestFile().isBlank()) {
                        throw new AssemblyException("A selected release's manifestFile must not be blank.");
                    }
                    Path manifestFile = normalizedBaseDirectory.resolve(entry.manifestFile()).normalize();
                    if (!manifestFile.startsWith(normalizedBaseDirectory)) {
                        throw new AssemblyException("A selected release's manifestFile must stay inside the selection directory.");
                    }
                    return new SelectedRelease(
                            new ClientRelease(entry.clientName(), entry.manifestVersion()),
                            ManifestSources.file(manifestFile));
                })
                .toList();
        return new ReleaseSelection(document.graph(), document.environment(), selected);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AssemblyException("A release selection's " + fieldName + " must not be blank.");
        }
    }

    /** The wire shape {@link #fromJson} reads; also reused by {@link TransferBundle} to write a bundle's own {@code selection.json} in the identical shape. */
    public record SelectionDocument(String graph, String environment, List<SelectedReleaseDocument> releases) {}

    public record SelectedReleaseDocument(String clientName, String manifestVersion, String manifestFile) {}
}
