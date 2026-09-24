package dev.glisseo.izar.manifest;

/**
 * One operation entry in an Apollo-compatible persisted-query manifest.
 *
 * <p>{@code id} is always the lowercase hex SHA-256 digest of {@code body}. The canonical
 * constructor enforces that on every path an entry can be built through, including {@link
 * OperationManifest#fromJson}, so a manifest read from disk or over the wire can never carry an ID
 * that disagrees with its own document.
 *
 * @param id the SHA-256 hex digest of {@code body}
 * @param body the exact final executable document this ID was derived from
 * @param name the operation's name, as declared in {@code body}
 * @param type {@code "query"}, {@code "mutation"}, or {@code "subscription"}, matching the operation kind in {@code body}
 */
public record ManifestOperation(String id, String body, String name, String type) {

    public ManifestOperation {
        requireNonBlank(id, "id");
        requireNonBlank(body, "body");
        requireNonBlank(name, "name");
        requireNonBlank(type, "type");

        String expectedId = OperationHash.sha256(body);
        if (!expectedId.equals(id)) {
            throw new InvalidManifestException(
                    "Operation '"
                            + name
                            + "' has id '"
                            + id
                            + "', but its document hashes to '"
                            + expectedId
                            + "'.");
        }
    }

    /** Builds an entry from a document, deriving {@code id} as this document's SHA-256 hash. */
    public static ManifestOperation of(String name, String type, String document) {
        return new ManifestOperation(OperationHash.sha256(document), document, name, type);
    }

    /**
     * Returns the ID a manifest entry for {@code document} would have, without building an entry.
     *
     * <p>A persisted-ID execution client and a server's registry lookup both need this same
     * derivation, so they agree with manifest generation on what identifies an operation.
     */
    public static String idFor(String document) {
        return OperationHash.sha256(document);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidManifestException("A manifest operation's " + fieldName + " must not be blank.");
        }
    }
}
