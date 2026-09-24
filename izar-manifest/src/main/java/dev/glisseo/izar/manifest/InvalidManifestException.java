package dev.glisseo.izar.manifest;

/**
 * Thrown when a manifest or one of its operations cannot be trusted: a malformed JSON body, or an
 * operation whose ID does not match the SHA-256 hash of its own document.
 *
 * <p>An entry like this cannot come from this module's own {@link ManifestOperation#of} factory,
 * since that always derives the ID from the document it is given. It signals a manifest built or
 * edited some other way, whether corrupted in transit or tampered with.
 */
public final class InvalidManifestException extends RuntimeException {

    public InvalidManifestException(String message) {
        super(message);
    }

    public InvalidManifestException(String message, Throwable cause) {
        super(message, cause);
    }
}
