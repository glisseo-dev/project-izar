package dev.glisseo.izar.manifest.analysis;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Which registered client releases a usage report counts.
 *
 * <p>"Newest" always means the highest registry revision a release was published at, never the
 * highest version string.
 */
public enum ReleaseScope {
    /** Only the operations in each client's newest release. */
    LATEST,
    /** Each distinct (client, operation ID) once, attributed to the newest release containing it. */
    DEDUPED,
    /** Every (client, release, operation). */
    ALL;

    /** The lower-case form used in query parameters and JSON responses. */
    @JsonValue
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @throws IllegalArgumentException if {@code value} is not {@code latest}, {@code deduped} or
     *     {@code all} in any letter case
     */
    public static ReleaseScope parse(String value) {
        for (ReleaseScope scope : values()) {
            if (scope.wireName().equalsIgnoreCase(value)) {
                return scope;
            }
        }
        throw new IllegalArgumentException("Unknown release scope '" + value + "'. Use latest, deduped or all.");
    }
}
