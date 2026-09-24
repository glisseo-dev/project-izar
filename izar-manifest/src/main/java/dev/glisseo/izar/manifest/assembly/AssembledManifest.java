package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The result of one {@link ReleaseAssembler#assemble} call: the union manifest itself, the
 * provenance behind every operation in it, and the input lock that produced it.
 *
 * <p>{@code manifest} stays exactly the portable, Apollo-compatible contract {@link
 * OperationManifest} always is; nothing here reformats it or changes an operation's ID. Provenance
 * and the lock are assembly-specific metadata that travels alongside it, never inside it, per ADR
 * 0011.
 *
 * @param manifest the assembled union, deduplicated by operation ID
 * @param provenance every operation's contributing releases, in the same order as {@code
 *     manifest.operations()}
 * @param lock the exact resolved inputs this manifest was assembled from
 */
public record AssembledManifest(OperationManifest manifest, List<OperationProvenance> provenance, InputLock lock) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public AssembledManifest {
        if (manifest == null) {
            throw new AssemblyException("An assembled manifest must not be null.");
        }
        if (provenance == null) {
            throw new AssemblyException("Assembled provenance must not be null.");
        }
        if (lock == null) {
            throw new AssemblyException("An assembled input lock must not be null.");
        }
        provenance = List.copyOf(provenance);
    }

    /**
     * Writes three files under {@code outputDirectory}: {@code manifest.json} (the portable
     * manifest, unchanged), {@code provenance.json}, and {@code lock.json}. Creates {@code
     * outputDirectory} if it does not already exist.
     *
     * <p>Every JSON body is rendered before any file is touched, so a serialization failure never
     * reaches disk. Each file is then written to a sibling temporary file and moved into place, so
     * no reader ever observes a half-written {@code manifest.json}, {@code provenance.json}, or
     * {@code lock.json}. The three files are not one atomic set, though: if the process fails
     * between renaming the first and the last, {@code outputDirectory} can hold a mix of newly
     * written and previously existing files. That window is the same one an ordinary deployment
     * already lives with when replacing local configuration; a controller-published release (see
     * ADR 0027) carries the stronger, single-file atomic-rename guarantee of ADR 0014.
     *
     * @throws AssemblyException if a JSON body cannot be rendered or a file cannot be written
     */
    public void writeTo(Path outputDirectory) {
        String manifestJson = manifest.toJson();
        String provenanceJson = provenanceJson();
        String lockJson = lock.toJson();
        try {
            Files.createDirectories(outputDirectory);
            AtomicFiles.writeAtomically(outputDirectory.resolve("manifest.json"), manifestJson);
            AtomicFiles.writeAtomically(outputDirectory.resolve("provenance.json"), provenanceJson);
            AtomicFiles.writeAtomically(outputDirectory.resolve("lock.json"), lockJson);
        } catch (IOException e) {
            throw new AssemblyException("Could not write assembled output to '" + outputDirectory + "'.", e);
        }
    }

    /**
     * Reads back the three files {@link #writeTo} produces, the inverse operation: {@code
     * manifest.json}, {@code provenance.json}, and {@code lock.json} under {@code directory}.
     *
     * <p>Used to recover an assembled result without re-resolving its inputs, for example a {@link
     * TransferBundle} verifying its own stored content against a freshly recomputed one.
     *
     * @throws AssemblyException if any of the three files is missing, unreadable, or not
     *     well-formed
     */
    public static AssembledManifest readFrom(Path directory) {
        try {
            OperationManifest manifest = OperationManifest.fromJson(Files.readString(directory.resolve("manifest.json")));
            InputLock lock = InputLock.fromJson(Files.readString(directory.resolve("lock.json")));
            String provenanceJson = Files.readString(directory.resolve("provenance.json"));
            List<OperationProvenance> provenance = JSON.readValue(provenanceJson, ProvenanceDocument.class).operations();
            return new AssembledManifest(manifest, provenance, lock);
        } catch (IOException e) {
            throw new AssemblyException("Could not read assembled manifest from '" + directory + "'.", e);
        } catch (JacksonException e) {
            throw new AssemblyException("Could not parse assembled provenance JSON: " + e.getMessage(), e);
        }
    }

    private String provenanceJson() {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(new ProvenanceDocument(provenance));
        } catch (JacksonException e) {
            throw new AssemblyException("Could not write assembly provenance as JSON.", e);
        }
    }

    private record ProvenanceDocument(List<OperationProvenance> operations) {}
}
