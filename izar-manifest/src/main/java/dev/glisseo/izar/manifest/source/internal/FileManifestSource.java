package dev.glisseo.izar.manifest.source.internal;

import dev.glisseo.izar.manifest.ManifestSnapshot;
import dev.glisseo.izar.manifest.ManifestSourceException;
import dev.glisseo.izar.manifest.OperationHash;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.source.LocalManifestSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A {@link ManifestSource} that reads a complete manifest from one file on disk.
 *
 * <p>The revision is the SHA-256 hex digest of the file's raw bytes, so two loads of unchanged
 * content always agree on revision, and any edit, including one that leaves the manifest's own
 * JSON semantically equivalent (reordered whitespace, for instance), is visible as a new one.
 */
public final class FileManifestSource implements LocalManifestSource {

    private final Path file;

    public FileManifestSource(Path file) {
        this.file = file;
    }

    /** The file this source reads from, for a caller that needs the raw bytes {@link #load} hashes. */
    public Path file() {
        return file;
    }

    @Override
    public ManifestSnapshot load() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ManifestSourceException("Could not read manifest file '" + file + "'.", e);
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        return new ManifestSnapshot(OperationHash.sha256(content), OperationManifest.fromJson(content));
    }
}
