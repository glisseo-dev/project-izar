package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationHash;
import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.AssemblyException;
import dev.glisseo.izar.manifest.assembly.LockedRelease;
import dev.glisseo.izar.manifest.assembly.ReleaseAssembler;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Verifies a {@link TransferBundle} directory entirely offline: every check reads only files
 * already inside {@code bundleDirectory}, never a network location or external account, so a
 * disconnected pipeline can run this the same way it runs {@link ReleaseAssembler} and {@link
 * CandidateChecker}.
 *
 * <p>Verification has two phases. The first reads {@code bundle.json}, the stored {@code
 * manifest.json}/{@code provenance.json}/{@code lock.json} triple, and every embedded release's
 * manifest, checking each embedded file's SHA-256 digest against what the stored {@link InputLock}
 * recorded. The second, run only if the first found nothing wrong, reassembles the embedded
 * originals through the ordinary {@link ReleaseAssembler} and requires the result to equal the
 * stored triple exactly. This mirrors {@link ReleaseAssembler#assemble}'s own two-phase shape:
 * resolve everything first, then only build the union once every input is trustworthy.
 *
 * <p>A bundle directory can carry files this verifier never reads. Their presence never makes a
 * bundle invalid, and verifying a bundle never claims
 * anything about them: only {@code bundle.json}, {@code selection.json}, and the files {@link
 * TransferBundle#writeTo} itself produces are part of what "verified" means here.
 */
public final class BundleVerifier {

    /**
     * Verifies {@code bundleDirectory}, recomputing every content digest and the assembled result
     * from the bundle's own embedded originals.
     */
    public BundleVerificationReport verify(Path bundleDirectory) {
        List<String> problems = new ArrayList<>();

        BundleDescriptor descriptor;
        try {
            descriptor = BundleDescriptor.fromJson(Files.readString(bundleDirectory.resolve("bundle.json")));
            if (descriptor == null) {
                problems.add("Bundle descriptor 'bundle.json' must contain an object.");
                return invalid(bundleDirectory, problems);
            }
        } catch (IOException e) {
            problems.add("Could not read bundle descriptor 'bundle.json': " + e.getMessage());
            return invalid(bundleDirectory, problems);
        } catch (BundleException e) {
            problems.add(e.getMessage());
            return invalid(bundleDirectory, problems);
        }
        if (!descriptor.supported()) {
            problems.add("Unsupported bundle format '" + descriptor.format() + "' version " + descriptor.version()
                    + "; this build understands '" + BundleDescriptor.FORMAT + "' version " + BundleDescriptor.CURRENT_VERSION
                    + ".");
            return invalid(bundleDirectory, problems);
        }

        AssembledManifest stored;
        try {
            stored = AssembledManifest.readFrom(bundleDirectory);
        } catch (AssemblyException | InvalidManifestException e) {
            problems.add("Could not read the bundle's assembled content: " + e.getMessage());
            return invalid(bundleDirectory, problems);
        }

        if (!descriptor.identity().equals(stored.lock().identity())) {
            problems.add("Bundle descriptor identity '" + descriptor.identity() + "' does not match its lock identity '"
                    + stored.lock().identity() + "'.");
        }

        List<BundledRelease> embeddedReleases = new ArrayList<>();
        for (LockedRelease locked : stored.lock().releases()) {
            Path releaseFile = bundleDirectory.resolve(TransferBundle.releasePath(locked.release()));
            if (!Files.exists(releaseFile)) {
                problems.add("Bundle is missing the embedded manifest for release '" + locked.release() + "' ('"
                        + TransferBundle.releasePath(locked.release()) + "').");
                continue;
            }
            String json;
            try {
                json = Files.readString(releaseFile);
            } catch (IOException e) {
                problems.add("Could not read embedded manifest for release '" + locked.release() + "': " + e.getMessage());
                continue;
            }
            String digest = OperationHash.sha256(json);
            if (!digest.equals(locked.revision())) {
                problems.add("Embedded manifest for release '" + locked.release() + "' has digest '" + digest
                        + "', but the bundle's lock records '" + locked.revision() + "'.");
                continue;
            }
            embeddedReleases.add(new BundledRelease(locked.release(), json));
        }

        if (!problems.isEmpty()) {
            return invalid(bundleDirectory, problems);
        }

        ReleaseSelection recomputedSelection;
        try {
            String selectionJson = Files.readString(bundleDirectory.resolve("selection.json"));
            recomputedSelection = ReleaseSelection.fromJson(selectionJson, bundleDirectory);
        } catch (IOException e) {
            problems.add("Could not read bundle selection 'selection.json': " + e.getMessage());
            return invalid(bundleDirectory, problems);
        } catch (AssemblyException | InvalidManifestException e) {
            problems.add("Bundle selection 'selection.json' is not well-formed: " + e.getMessage());
            return invalid(bundleDirectory, problems);
        }

        AssembledManifest recomputed;
        try {
            recomputed = new ReleaseAssembler().assemble(recomputedSelection);
        } catch (AssemblyException e) {
            problems.add("Bundle content does not reassemble consistently: " + e.getMessage());
            return invalid(bundleDirectory, problems);
        }

        if (!recomputed.equals(stored)) {
            problems.add("Recomputing assembly from the bundle's embedded originals does not match its stored"
                    + " manifest, provenance, or lock.");
            return invalid(bundleDirectory, problems);
        }

        TransferBundle bundle = new TransferBundle(descriptor, stored, embeddedReleases);
        return new BundleVerificationReport(bundleDirectory, List.of(), Optional.of(bundle));
    }

    private static BundleVerificationReport invalid(Path bundleDirectory, List<String> problems) {
        return new BundleVerificationReport(bundleDirectory, problems, Optional.empty());
    }
}
