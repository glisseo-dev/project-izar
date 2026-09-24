package dev.glisseo.izar.manifest;

import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * An Apollo-compatible manifest input for code generation. Unlike {@link OperationManifest}, this
 * import value accepts operation IDs that are not SHA-256 hashes of their bodies, and it never
 * rewrites the supplied operation document.
 */
public record OperationManifestInput(
        String format, int version, List<OperationManifestInput.Operation> operations) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public OperationManifestInput {
        if (!OperationManifest.APOLLO_FORMAT.equals(format)) {
            throw new InvalidManifestException(
                    "An operation manifest's format must be '" + OperationManifest.APOLLO_FORMAT + "', was '" + format + "'.");
        }
        if (version != OperationManifest.CURRENT_VERSION) {
            throw new InvalidManifestException(
                    "An operation manifest's version must be " + OperationManifest.CURRENT_VERSION + ", was " + version + ".");
        }
        if (operations == null) {
            throw new InvalidManifestException("An operation manifest's operations must not be null.");
        }
        if (operations.stream().anyMatch(operation -> operation == null)) {
            throw new InvalidManifestException("An operation manifest's operations must not contain null entries.");
        }
        operations = List.copyOf(operations);
    }

    public static OperationManifestInput fromJson(String json) {
        try {
            return JSON.readValue(json, OperationManifestInput.class);
        } catch (JacksonException e) {
            throw new InvalidManifestException("Could not parse operation manifest JSON: " + e.getMessage(), e);
        }
    }

    /** One manifest operation whose ID and document are preserved verbatim. */
    public record Operation(String id, String body, String name, String type) {
        public Operation {
            requireNonBlank(id, "id");
            requireNonBlank(body, "body");
            requireNonBlank(name, "name");
            requireNonBlank(type, "type");
        }

        private static void requireNonBlank(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new InvalidManifestException("A manifest operation's " + fieldName + " must not be blank.");
            }
        }
    }
}
