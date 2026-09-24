package dev.glisseo.izar.manifest.analysis;

/** A supplied schema SDL document does not parse or does not describe a well-formed GraphQL schema. */
public final class InvalidSchemaException extends RuntimeException {

    public InvalidSchemaException(String message) {
        super(message);
    }

    public InvalidSchemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
