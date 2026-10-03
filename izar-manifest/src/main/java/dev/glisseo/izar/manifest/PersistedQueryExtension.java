package dev.glisseo.izar.manifest;

import java.util.Map;
import java.util.Optional;

/**
 * The Apollo-compatible {@code persistedQuery} request extension: a client's explicit signal that
 * it selected persisted-ID execution, and the shape a server reads that signal back from.
 *
 * <p>Both {@code izar-client} and a server that resolves persisted IDs depend on {@code
 * izar-manifest} already, so this is the one place that owns the extension's wire shape ({@code "persistedQuery"}, {@code
 * version}, {@code sha256Hash}): a client and a server built from this module can never drift on
 * what those keys mean, the way two independent literal-string call sites could.
 *
 * @param version always {@link #CURRENT_VERSION}
 * @param sha256Hash the operation ID the sender supplied, not yet checked against any document or
 *     registry
 */
public record PersistedQueryExtension(int version, String sha256Hash) {

    /** The extension's key in a GraphQL request's {@code extensions} map. */
    public static final String EXTENSION_NAME = "persistedQuery";

    public static final int CURRENT_VERSION = 1;

    public PersistedQueryExtension {
        if (sha256Hash == null || sha256Hash.isBlank()) {
            throw new InvalidManifestException("A persistedQuery extension's sha256Hash must not be blank.");
        }
    }

    /** Builds the extension a persisted-ID execution client sends for {@code document}. */
    public static PersistedQueryExtension of(String document) {
        return new PersistedQueryExtension(CURRENT_VERSION, ManifestOperation.idFor(document));
    }

    /** This extension's value, ready to add to a request's {@code extensions} map under {@link #EXTENSION_NAME}. */
    public Map<String, Object> toExtensionValue() {
        return Map.of("version", version, "sha256Hash", sha256Hash);
    }

    /**
     * Reads a {@code persistedQuery} extension back out of a request's {@code extensions} map, if
     * one is present and shaped as this type expects.
     */
    public static Optional<PersistedQueryExtension> from(Map<String, Object> extensions) {
        if (extensions.get(EXTENSION_NAME) instanceof Map<?, ?> persistedQuery
                && persistedQuery.get("sha256Hash") instanceof String hash) {
            // A wire value arrives as whatever integer type the transport's JSON layer chose
            // (Integer, Long, ...); this module cares about the ID, not that choice.
            Object rawVersion = persistedQuery.get("version");
            if (rawVersion != null
                    && !(rawVersion instanceof Byte
                            || rawVersion instanceof Short
                            || rawVersion instanceof Integer
                            || rawVersion instanceof Long)) {
                return Optional.empty();
            }
            int version = rawVersion instanceof Number n ? n.intValue() : CURRENT_VERSION;
            if (version != CURRENT_VERSION || hash.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PersistedQueryExtension(version, hash));
        }
        return Optional.empty();
    }
}
