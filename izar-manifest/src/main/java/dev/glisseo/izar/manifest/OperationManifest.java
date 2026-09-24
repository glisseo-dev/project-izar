package dev.glisseo.izar.manifest;

import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * An Apollo-compatible persisted-query manifest: a versioned, ordered set of operations, each
 * naming its SHA-256 ID alongside the exact document that ID was derived from.
 *
 * @param format always {@link #APOLLO_FORMAT}, so a compatible non-Izar tool recognizes this JSON
 * @param version always {@link #CURRENT_VERSION}
 * @param operations every operation this manifest registers, in generation order
 */
public record OperationManifest(String format, int version, List<ManifestOperation> operations) {

    public static final String APOLLO_FORMAT = "apollo-persisted-query-manifest";
    public static final int CURRENT_VERSION = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public OperationManifest {
        if (!APOLLO_FORMAT.equals(format)) {
            throw new InvalidManifestException(
                    "An operation manifest's format must be '" + APOLLO_FORMAT + "', was '" + format + "'.");
        }
        if (version != CURRENT_VERSION) {
            throw new InvalidManifestException(
                    "An operation manifest's version must be " + CURRENT_VERSION + ", was " + version + ".");
        }
        if (operations == null) {
            throw new InvalidManifestException("An operation manifest's operations must not be null.");
        }
        operations = List.copyOf(operations);
    }

    /** Builds a manifest with the current Apollo-compatible format and version. */
    public static OperationManifest of(List<ManifestOperation> operations) {
        return new OperationManifest(APOLLO_FORMAT, CURRENT_VERSION, operations);
    }

    /**
     * Parses a manifest from its JSON form, rejecting malformed JSON and any entry whose ID does
     * not match its own document, through {@link ManifestOperation}'s canonical constructor.
     *
     * @throws InvalidManifestException if {@code json} is not a well-formed manifest, or any entry
     *     fails {@link ManifestOperation}'s validation
     */
    public static OperationManifest fromJson(String json) {
        try {
            return JSON.readValue(json, OperationManifest.class);
        } catch (JacksonException e) {
            throw new InvalidManifestException("Could not parse operation manifest JSON: " + e.getMessage(), e);
        }
    }

    /** Renders this manifest as pretty-printed JSON, in the format an Apollo-compatible tool reads. */
    public String toJson() {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JacksonException e) {
            throw new InvalidManifestException("Could not write operation manifest as JSON.", e);
        }
    }
}
