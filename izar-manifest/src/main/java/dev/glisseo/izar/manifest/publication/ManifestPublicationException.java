package dev.glisseo.izar.manifest.publication;

/**
 * Thrown when a controller rejects or cannot complete a {@link ManifestPublisher#publish} call:
 * rejected credentials, a malformed or conflicting release, or a storage failure on the controller
 * side. The message is meant to reach a developer or a CI log directly, so it always includes the
 * controller's own diagnostic detail when one was returned.
 */
public final class ManifestPublicationException extends RuntimeException {

    public ManifestPublicationException(String message) {
        super(message);
    }

    public ManifestPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
