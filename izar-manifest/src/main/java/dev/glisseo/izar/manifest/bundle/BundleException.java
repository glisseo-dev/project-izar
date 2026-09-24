package dev.glisseo.izar.manifest.bundle;

/**
 * Thrown when a transfer bundle cannot be created, or read at all, by {@link BundleWriter}, {@link
 * TransferBundle}, or {@link BundleInstaller}.
 *
 * <p>An unverified bundle rejected by {@link BundleVerifier} does not throw this: {@link
 * BundleVerifier#verify} always returns a {@link BundleVerificationReport}, whether the bundle is
 * valid or not, the same way {@link CandidateChecker#check} returns a report rather than throwing
 * on a validation failure. This is reserved for a bundle operation that has no result to report at
 * all: a write that failed, or an install asked to activate a bundle nothing verified first.
 */
public final class BundleException extends RuntimeException {

    public BundleException(String message) {
        super(message);
    }

    public BundleException(String message, Throwable cause) {
        super(message, cause);
    }
}
