package dev.glisseo.izar.gradle;

import dev.glisseo.izar.manifest.InvalidManifestException;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;

/** Reads and validates the manifest file the publish, attach, and deploy tasks all start from. */
final class ManifestFiles {

    private ManifestFiles() {}

    /**
     * @param optionName the option that points at a different manifest, quoted in the "missing" error
     * @throws GradleException if the file is missing, unreadable, or not a valid manifest
     */
    static OperationManifest read(Path manifestFile, String optionName) {
        if (!Files.isRegularFile(manifestFile)) {
            throw new GradleException("No manifest file at " + manifestFile
                    + ". Run izarGenerate or point " + optionName + " at a collected manifest.");
        }
        String json;
        try {
            json = Files.readString(manifestFile);
        } catch (IOException e) {
            throw new GradleException("Could not read manifest file " + manifestFile, e);
        }
        try {
            return OperationManifest.fromJson(json);
        } catch (InvalidManifestException e) {
            throw new GradleException(
                    "Manifest file " + manifestFile + " is not a valid manifest: " + e.getMessage(), e);
        }
    }
}
