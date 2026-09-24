package dev.glisseo.izar.manifest;

/**
 * Thrown when a {@link dev.glisseo.izar.manifest.source.ManifestSource} cannot produce a candidate snapshot at all: a missing file,
 * a denied read, or an equivalent transport failure.
 *
 * <p>Distinct from {@link InvalidManifestException}, which a source's {@link dev.glisseo.izar.manifest.source.ManifestSource#load()}
 * still lets propagate unchanged when the content it did read is not a trustworthy manifest. Both
 * mean the same thing to a caller deciding whether a candidate is usable: this one just names the
 * failure as "could not read the source" rather than "read something, but it was invalid."
 */
public final class ManifestSourceException extends RuntimeException {

    public ManifestSourceException(String message) {
        super(message);
    }

    public ManifestSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
