package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.InputLock;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A self-contained transfer bundle: an assembled selection's own {@link AssembledManifest}
 * (manifest, provenance, and lock) plus every contributing release's original manifest, verbatim,
 * so a deployment engineer can move a complete, reviewed result between isolated environments and
 * verify it offline before installing it. It carries the inputs needed to reproduce the result,
 * not a second copy of the union logic: {@link
 * BundleVerifier} reproduces the assembled result from the embedded originals through the ordinary
 * {@link ReleaseAssembler}, then compares it against what is stored here.
 *
 * <p>{@code descriptor} identifies this as a bundle this build knows how to read, independent of
 * {@code assembled}'s own content, so an unsupported bundle fails before any of it is parsed as
 * this build's shapes. {@code releases} carries one entry per {@link InputLock#releases()} entry in
 * {@code assembled.lock()}, in the same order.
 *
 * <p>A bundle is a directory of files, not a single archive: {@link #writeTo} and
 * {@link BundleVerifier#verify} both read and write plain files at fixed relative paths, so an
 * ordinary file copy, {@code rsync}, or artifact-repository upload mirrors a bundle without special
 * tooling without changing the files' identity.
 *
 * @param descriptor this bundle's format header
 * @param assembled the assembled manifest, provenance, and input lock this bundle packages
 * @param releases every contributing release's original manifest, one per {@code
 *     assembled.lock().releases()} entry
 */
public record TransferBundle(BundleDescriptor descriptor, AssembledManifest assembled, List<BundledRelease> releases) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public TransferBundle {
        if (descriptor == null) {
            throw new BundleException("A transfer bundle must carry a descriptor.");
        }
        if (assembled == null) {
            throw new BundleException("A transfer bundle must carry an assembled manifest.");
        }
        if (releases == null) {
            throw new BundleException("A transfer bundle's releases must not be null.");
        }
        releases = List.copyOf(releases);
    }

    /** The bundle-relative path {@link #writeTo} embeds {@code release}'s original manifest at. */
    static String releasePath(ClientRelease release) {
        return "releases/" + release.clientName() + "/" + release.manifestVersion() + "/manifest.json";
    }

    /**
     * Writes this bundle to {@code bundleDirectory}: {@code bundle.json}, {@code selection.json},
     * the assembled {@code manifest.json}, {@code provenance.json}, and {@code lock.json} (via
     * {@link AssembledManifest#writeTo}), and every entry of {@code releases} under {@code
     * releases/<clientName>/<manifestVersion>/manifest.json}. Creates {@code bundleDirectory} if it
     * does not already exist.
     *
     * <p>Every file is written through the same temporary-file-and-rename {@link AssembledManifest}
     * itself uses, so no reader ever observes a half-written file; that guarantee does not extend
     * across the whole bundle as one set, for the same reason {@link AssembledManifest#writeTo}
     * documents. A caller needing the stronger guarantee that a reader never sees a mixture of two
     * bundles' files should install through {@link BundleInstaller} instead, which publishes an
     * entire verified bundle as one atomically renamed directory.
     *
     * @throws BundleException if a JSON body cannot be rendered or a file cannot be written
     */
    public void writeTo(Path bundleDirectory) {
        try {
            Files.createDirectories(bundleDirectory);
        } catch (IOException e) {
            throw new BundleException("Could not create bundle directory '" + bundleDirectory + "'.", e);
        }

        assembled.writeTo(bundleDirectory);

        try {
            AtomicFiles.writeAtomically(bundleDirectory.resolve("bundle.json"), descriptor.toJson());
            AtomicFiles.writeAtomically(bundleDirectory.resolve("selection.json"), selectionJson());
            for (BundledRelease release : releases) {
                Path target = bundleDirectory.resolve(releasePath(release.release()));
                Files.createDirectories(target.getParent());
                AtomicFiles.writeAtomically(target, release.manifestJson());
            }
        } catch (IOException e) {
            throw new BundleException("Could not write transfer bundle to '" + bundleDirectory + "'.", e);
        }
    }

    /**
     * Renders {@code releases} as a {@link ReleaseSelection#fromJson} selection document, naming
     * each release's bundle-relative {@link #releasePath}. Reading a bundle's {@code
     * selection.json} back through {@link ReleaseSelection#fromJson} against the bundle's own
     * directory is exactly how {@link BundleVerifier} recomputes assembly from the embedded
     * originals.
     */
    private String selectionJson() {
        List<ReleaseSelection.SelectedReleaseDocument> entries = releases.stream()
                .map(release -> new ReleaseSelection.SelectedReleaseDocument(
                        release.release().clientName(), release.release().manifestVersion(), releasePath(release.release())))
                .toList();
        try {
            return JSON.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(
                            new ReleaseSelection.SelectionDocument(descriptor.graphId(), descriptor.environment(), entries));
        } catch (JacksonException e) {
            throw new BundleException("Could not write bundle selection as JSON.", e);
        }
    }
}
