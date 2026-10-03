package dev.glisseo.izar.manifest.assembly;

/**
 * The identity of one immutable client release: a client name paired with its manifest version,
 * independently of operation identity, which is always the document hash.
 *
 * <p>Two releases with the same {@code clientName} but different {@code manifestVersion} are
 * different releases, not revisions of one another; a {@link ReleaseSelection} can name several at
 * once so rolling deployments and retained rollback versions all stay supported.
 *
 * <p>Both parts become directory names when a release is written into a transfer bundle, so
 * neither may be {@code "."} or {@code ".."} or contain a path separator or colon.
 *
 * @param clientName the publishing client's name
 * @param manifestVersion the version of that client's manifest, exactly as published
 */
public record ClientRelease(String clientName, String manifestVersion) implements Comparable<ClientRelease> {

    public ClientRelease {
        requireNonBlank(clientName, "clientName");
        requireNonBlank(manifestVersion, "manifestVersion");
    }

    /** Orders by client name, then manifest version, so assembly output has a stable, arbitrary-input-order-independent ordering. */
    @Override
    public int compareTo(ClientRelease other) {
        int byName = clientName.compareTo(other.clientName);
        return byName != 0 ? byName : manifestVersion.compareTo(other.manifestVersion);
    }

    @Override
    public String toString() {
        return clientName + "@" + manifestVersion;
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AssemblyException("A client release's " + fieldName + " must not be blank.");
        }
        if (value.equals(".") || value.equals("..") || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0) {
            throw new AssemblyException("A client release's " + fieldName
                    + " must not be \".\" or \"..\" or contain '/', '\\' or ':', because a bundle uses it as a path segment: "
                    + value);
        }
    }
}
