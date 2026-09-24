package dev.glisseo.izar.manifest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Derives an Apollo-compatible operation ID: the lowercase hex SHA-256 digest of an operation's
 * exact final executable document, encoded as UTF-8.
 *
 * <p>Hashing the authored source file, or any text other than what a client actually sends on the
 * wire, would let an ID and its document disagree. Callers always hash the document after fragment
 * inclusion and any compiler-added type discriminators, never the author's original file text.
 */
public final class OperationHash {

    private OperationHash() {}

    public static String sha256(String document) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every JDK implementation is required to provide SHA-256.
            throw new AssertionError("SHA-256 is required to be available.", e);
        }
        byte[] hash = digest.digest(document.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
