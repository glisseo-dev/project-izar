package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.InputLock;
import dev.glisseo.izar.manifest.assembly.ReleaseSelection;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * A transfer bundle's own format header, read before anything else in the bundle: identifies a
 * directory as an Izar transfer bundle and names the supported-release selection it packages.
 *
 * <p>Kept separate from {@link InputLock}, which already carries {@code graphId} and {@code
 * environment}, so {@link BundleVerifier} can recognize an unsupported format or version and fail
 * cleanly before it ever tries to parse {@code manifest.json}, {@code provenance.json}, or {@code
 * lock.json} as this build's shapes. Deliberately not validated against {@link #FORMAT} and {@link
 * #CURRENT_VERSION} in its constructor, unlike {@link OperationManifest}: an unsupported descriptor
 * must still parse successfully so verification can report it as a finding, not a parse failure.
 *
 * @param format identifies this directory as an Izar transfer bundle; only {@link #FORMAT} is
 *     understood by this build
 * @param version the bundle layout version; only {@link #CURRENT_VERSION} is understood by this
 *     build
 * @param graphId the packaged selection's {@link ReleaseSelection#graphId()}
 * @param environment the packaged selection's {@link ReleaseSelection#environment()}
 */
public record BundleDescriptor(String format, int version, String graphId, String environment) {

    public static final String FORMAT = "izar-transfer-bundle";
    public static final int CURRENT_VERSION = 1;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public BundleDescriptor {
        requireNonBlank(format, "format");
        requireNonBlank(graphId, "graphId");
        requireNonBlank(environment, "environment");
    }

    /** Builds a descriptor with this build's own format and version. */
    public static BundleDescriptor of(String graphId, String environment) {
        return new BundleDescriptor(FORMAT, CURRENT_VERSION, graphId, environment);
    }

    /** {@code true} if this build knows how to verify a bundle carrying this descriptor. */
    public boolean supported() {
        return FORMAT.equals(format) && version == CURRENT_VERSION;
    }

    /** This descriptor's {@code graphId} and {@code environment}, combined into one display identity. */
    public String identity() {
        return graphId + "/" + environment;
    }

    public String toJson() {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JacksonException e) {
            throw new BundleException("Could not write bundle descriptor as JSON.", e);
        }
    }

    /**
     * Parses a descriptor from its JSON form. Does not reject an unsupported {@code format} or
     * {@code version}; call {@link #supported()} to check that.
     *
     * @throws BundleException if {@code json} is not a well-formed descriptor
     */
    public static BundleDescriptor fromJson(String json) {
        try {
            return JSON.readValue(json, BundleDescriptor.class);
        } catch (JacksonException e) {
            throw new BundleException("Could not parse bundle descriptor JSON: " + e.getMessage(), e);
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BundleException("A bundle descriptor's " + fieldName + " must not be blank.");
        }
    }
}
