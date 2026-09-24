package dev.glisseo.izar.manifest.bundle;

import dev.glisseo.izar.manifest.assembly.ClientRelease;
/**
 * One client release's original manifest, embedded in a {@link TransferBundle} verbatim.
 *
 * <p>{@code manifestJson} is exactly the bytes {@link BundleWriter} read from that release's own
 * {@link dev.glisseo.izar.manifest.source.LocalManifestSource}, never reformatted or reordered; {@link BundleVerifier} recomputes its
 * SHA-256 digest and compares it against the bundle's {@link InputLock} to detect any change. This
 * is what lets {@link BundleVerifier} reassemble a bundle's exact original inputs offline, the same
 * way {@link ReleaseAssembler} would from local files, without a second copy of the union logic.
 *
 * @param release the identity of the release this manifest belongs to
 * @param manifestJson the release's complete manifest, exactly as its source produced it
 */
public record BundledRelease(ClientRelease release, String manifestJson) {

    public BundledRelease {
        if (release == null) {
            throw new BundleException("A bundled release must name a client release.");
        }
        if (manifestJson == null || manifestJson.isBlank()) {
            throw new BundleException("A bundled release's manifestJson must not be blank.");
        }
    }
}
