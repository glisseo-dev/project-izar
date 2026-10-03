package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.OperationHash;
import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.LockedRelease;
import dev.glisseo.izar.manifest.assembly.ReleaseAssembler;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import dev.glisseo.izar.manifest.assembly.SelectedRelease;
import dev.glisseo.izar.manifest.source.LocalManifestSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a {@link TransferBundle} from a {@link ReleaseSelection} resolved from local files, and
 * writes it to a directory in one call.
 *
 * <p>Holds no assembly logic of its own: {@link ReleaseAssembler} still performs the union, exactly
 * as {@link dev.glisseo.izar.cli.AssembleCommand} already calls it directly. This adds only what
 * assembly does not already keep around: each contributing release's original manifest bytes,
 * embedded so a bundle stays self-contained.
 *
 * <p>Every selected release must resolve through a {@link LocalManifestSource}, the same local-only
 * scope {@link dev.glisseo.izar.cli.AssembleCommand} already has: a bundle's embedded
 * originals are the exact bytes on disk, not a re-fetch from wherever a source might otherwise read
 * from.
 */
public final class BundleWriter {

    /**
     * Assembles {@code selection} and writes the result, plus every contributing release's original
     * manifest, to {@code bundleDirectory}.
     *
     * @throws AssemblyException under the same conditions {@link ReleaseAssembler#assemble} does
     * @throws BundleException if a selected release does not resolve through a {@link
     *     LocalManifestSource}, or the bundle cannot be written
     */
    public TransferBundle write(ReleaseSelection selection, Path bundleDirectory) {
        // Checked before assembly even runs, so a non-file source fails as a local-only bundling
        // error rather than as whatever assembly's own attempt to load it happens to raise (a
        // network timeout, for an HTTP source), and so bundling never makes a network call at all.
        Map<ClientRelease, Path> sourceFiles = new LinkedHashMap<>();
        for (SelectedRelease selectedRelease : selection.releases()) {
            if (!(selectedRelease.source() instanceof LocalManifestSource fileSource)) {
                throw new BundleException("Could not bundle release '" + selectedRelease.release()
                        + "': bundle creation requires a local file source.");
            }
            sourceFiles.putIfAbsent(selectedRelease.release(), fileSource.file());
        }

        AssembledManifest assembled = new ReleaseAssembler().assemble(selection);

        List<BundledRelease> releases =
                assembled.lock().releases().stream().map(locked -> embed(locked, sourceFiles.get(locked.release()))).toList();

        TransferBundle bundle =
                new TransferBundle(BundleDescriptor.of(selection.graphId(), selection.environment()), assembled, releases);
        bundle.writeTo(bundleDirectory);
        return bundle;
    }

    private static BundledRelease embed(LockedRelease locked, Path file) {
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            throw new BundleException("Could not read manifest file for release '" + locked.release() + "'.", e);
        }
        String digest = OperationHash.sha256(json);
        if (!digest.equals(locked.revision())) {
            // Assembly already resolved this release once; a mismatch here means the file changed
            // between resolution and bundling, not an ordinary input error ReleaseAssembler reports.
            throw new BundleException("Manifest file for release '" + locked.release()
                    + "' changed between assembly and bundling (digest '" + digest + "', expected '" + locked.revision()
                    + "').");
        }
        return new BundledRelease(locked.release(), json);
    }
}
