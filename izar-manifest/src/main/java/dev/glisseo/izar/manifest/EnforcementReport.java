package dev.glisseo.izar.manifest;

import java.util.Map;
import java.util.Optional;

/**
 * The response extension an enforcing endpoint adds to every evaluated GraphQL response, so a
 * client developer can tell which policy evaluated the request rather than inferring it from
 * whether the request happened to succeed.
 *
 * <p>Both a server (the writer) and {@code izar-client} (a potential reader) depend on
 * {@code izar-manifest} already, so this is the one place that owns the extension's wire shape
 * ({@code "izar"}, {@code mode}, {@code registered}, {@code revision}), the same way {@link
 * PersistedQueryExtension} owns the request-side extension.
 *
 * @param mode the server's effective enforcement mode ({@code "AUDIT"}, {@code "ENFORCE"}, or
 *     {@code "ID_ONLY"}) that evaluated this request; never influenced by anything the request
 *     itself supplied
 * @param registered whether the evaluated operation was found in the active registry
 * @param revision the active manifest revision the registry was activated from
 */
public record EnforcementReport(String mode, boolean registered, String revision) {

    /** The extension's key in a GraphQL response's {@code extensions} map. */
    public static final String EXTENSION_NAME = "izar";

    public EnforcementReport {
        if (mode == null || mode.isBlank()) {
            throw new InvalidManifestException("An EnforcementReport's mode must not be blank.");
        }
        if (revision == null || revision.isBlank()) {
            throw new InvalidManifestException("An EnforcementReport's revision must not be blank.");
        }
    }

    /** This report's value, ready to add to a response's {@code extensions} map under {@link #EXTENSION_NAME}. */
    public Map<String, Object> toExtensionValue() {
        return Map.of("mode", mode, "registered", registered, "revision", revision);
    }

    /** Reads an {@code izar} extension back out of a response's {@code extensions} map, if present and well-shaped. */
    public static Optional<EnforcementReport> from(Map<String, Object> extensions) {
        if (extensions.get(EXTENSION_NAME) instanceof Map<?, ?> report
                && report.get("mode") instanceof String mode
                && report.get("registered") instanceof Boolean registered
                && report.get("revision") instanceof String revision) {
            return Optional.of(new EnforcementReport(mode, registered, revision));
        }
        return Optional.empty();
    }
}
