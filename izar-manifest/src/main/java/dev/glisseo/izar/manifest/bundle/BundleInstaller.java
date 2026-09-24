package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.OperationHash;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;

/**
 * Installs a verified {@link TransferBundle} into a local install root as one atomic unit, so a
 * reader of {@code installRoot} never observes a mixture of files from two different revisions and
 * an interrupted or rejected install never disturbs the previously installed one (decision 11,
 * "multi-file bundles become visible as a verified unit, not a mixture of files from different
 * revisions").
 *
 * <p>{@link #install} always verifies through {@link BundleVerifier} first; nothing under {@code
 * installRoot} is touched at all if the bundle does not verify. A verified bundle's content is
 * copied into a fresh, content-addressed directory under {@code installRoot/revisions}, built
 * entirely out of sight in a sibling temporary directory and made visible only by one atomic
 * directory rename onto a path nothing referenced before. Only after that rename succeeds does a
 * second, single-file atomic rename repoint {@code installRoot/ACTIVE} at it. A process failure at
 * any point before that final rename leaves {@code ACTIVE} exactly as it was: the previously
 * installed revision, if any, stays active and intact, and at worst an unreferenced, harmless
 * leftover directory remains under {@code revisions/}.
 *
 * <p>This is a local, deployment-tool-independent primitive, not a running server's activation.
 * Wiring a server's own local manifest source at a stable path on top of an install root (a
 * symlink, a bind mount, or a GitOps-managed mount) is each delivery route's own concern.
 */
public final class BundleInstaller {

    static final String REVISIONS_DIRECTORY = "revisions";
    private static final String ACTIVE_POINTER = "ACTIVE";

    /**
     * Verifies {@code bundleDirectory} and installs it under {@code installRoot}.
     *
     * @throws BundleException if the bundle does not verify, or the install could not be completed
     */
    public InstalledBundle install(Path bundleDirectory, Path installRoot) {
        BundleVerificationReport report = new BundleVerifier().verify(bundleDirectory);
        if (!report.valid()) {
            throw new BundleException("Refusing to install an unverified bundle '" + bundleDirectory + "': "
                    + String.join(" ", report.problems()));
        }
        TransferBundle bundle = report.bundle().orElseThrow();
        String revisionId = revisionId(bundle);

        Path revisionsDirectory = installRoot.resolve(REVISIONS_DIRECTORY);
        Path target = revisionsDirectory.resolve(revisionId);
        if (!Files.exists(target)) {
            stage(bundle, revisionsDirectory, target);
        }
        activate(installRoot, revisionId);
        return new InstalledBundle(installRoot, revisionId);
    }

    /** The currently active installed revision under {@code installRoot}, if one has been activated. */
    public Optional<InstalledBundle> active(Path installRoot) {
        Path pointer = installRoot.resolve(ACTIVE_POINTER);
        if (!Files.exists(pointer)) {
            return Optional.empty();
        }
        try {
            String revisionId = Files.readString(pointer).strip();
            return Optional.of(new InstalledBundle(installRoot, revisionId));
        } catch (IOException e) {
            throw new BundleException("Could not read active install pointer '" + pointer + "'.", e);
        }
    }

    private static void stage(TransferBundle bundle, Path revisionsDirectory, Path target) {
        Path temp;
        try {
            Files.createDirectories(revisionsDirectory);
            temp = Files.createTempDirectory(revisionsDirectory, "staging-");
        } catch (IOException e) {
            throw new BundleException("Could not prepare a staging directory under '" + revisionsDirectory + "'.", e);
        }
        try {
            bundle.writeTo(temp);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (BundleException e) {
            deleteRecursively(temp);
            throw e;
        } catch (IOException e) {
            deleteRecursively(temp);
            throw new BundleException("Could not stage installed bundle content under '" + revisionsDirectory + "'.", e);
        }
    }

    private static void activate(Path installRoot, String revisionId) {
        try {
            AtomicFiles.writeAtomically(installRoot.resolve(ACTIVE_POINTER), revisionId);
        } catch (IOException e) {
            throw new BundleException(
                    "Could not activate installed revision '" + revisionId + "' at '" + installRoot + "'.", e);
        }
    }

    /** A content-derived identifier: two bundles assembled from identical inputs install to the same revision. */
    private static String revisionId(TransferBundle bundle) {
        return OperationHash.sha256(bundle.assembled().manifest().toJson() + '\n' + bundle.assembled().lock().toJson());
    }

    private static void deleteRecursively(Path directory) {
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(BundleInstaller::deleteQuietly);
        } catch (IOException ignored) {
            // Best-effort cleanup of a staging directory nothing ever referenced.
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup of a staging directory nothing ever referenced.
        }
    }
}
