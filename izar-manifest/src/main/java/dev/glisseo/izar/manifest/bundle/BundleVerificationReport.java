package dev.glisseo.izar.manifest.bundle;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The result of one {@link BundleVerifier#verify} call.
 *
 * <p>Carries either a non-empty {@code problems} list and no bundle, or an empty {@code problems}
 * list and the {@link TransferBundle} verification reconstructed from the bundle's own embedded
 * originals. Every documented failure mode, a missing file, a mismatched digest, a conflicting
 * identity, an unsupported format, or an assembled result that does not reproduce, becomes one
 * entry in {@code problems} rather than an exception: verification always finishes and reports what
 * it found, the same way {@link CandidateCheckReport} never throws for a validation failure.
 *
 * @param bundleDirectory the directory that was verified
 * @param problems every reason verification failed; empty exactly when {@code bundle} is present
 * @param bundle the reconstructed bundle, present exactly when {@code problems} is empty
 */
public record BundleVerificationReport(Path bundleDirectory, List<String> problems, Optional<TransferBundle> bundle) {

    public BundleVerificationReport {
        problems = List.copyOf(problems);
        if (problems.isEmpty() == bundle.isEmpty()) {
            throw new BundleException(
                    "A bundle verification report must carry a bundle exactly when it has no problems.");
        }
    }

    public boolean valid() {
        return problems.isEmpty();
    }
}
