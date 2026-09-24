package dev.glisseo.izar.manifest.assembly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes one file's content to a temporary sibling and renames it into place, so no reader ever
 * observes a half-written file at {@code target}. This helper is private to the assembly package.
 */
final class AtomicFiles {

    private AtomicFiles() {}

    static void writeAtomically(Path target, String content) throws IOException {
        writeAtomically(target, content.getBytes(StandardCharsets.UTF_8));
    }

    static void writeAtomically(Path target, byte[] content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }
}
