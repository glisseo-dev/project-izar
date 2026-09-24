package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PackageStructureTest {

    private static final Set<String> SHARED_ROOT_FILES = Set.of(
            "EnforcementReport.java",
            "InvalidManifestException.java",
            "ManifestOperation.java",
            "ManifestSnapshot.java",
            "ManifestSourceException.java",
            "OperationHash.java",
            "OperationManifest.java",
            "OperationManifestInput.java",
            "PersistedQueryExtension.java",
            "package-info.java");

    private static final Set<String> CAPABILITY_PACKAGES = Set.of(
            "analysis", "assembly", "bundle", "publication", "source");

    private static final List<String> CALLER_MODULES = List.of(
            "izar-cli", "izar-client", "izar-compiler", "izar-controller", "izar-maven-plugin", "izar-server");

    private static final List<String> IMPLEMENTATION_PACKAGES = List.of(
            "dev.glisseo.izar.manifest.source.internal");

    @Test
    void rootContainsOnlySharedManifestTypes() throws IOException {
        Path root = Path.of("src/main/java/dev/glisseo/izar/manifest");

        try (var files = Files.list(root)) {
            Set<String> rootFiles = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertThat(rootFiles).containsExactlyInAnyOrderElementsOf(SHARED_ROOT_FILES);
        }
    }

    @Test
    void eachManifestCapabilityHasItsOwnPackage() throws IOException {
        Path root = Path.of("src/main/java/dev/glisseo/izar/manifest");

        try (var children = Files.list(root)) {
            Set<String> packages = children
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
            assertThat(packages).containsAll(CAPABILITY_PACKAGES);
        }
    }

    @Test
    void callersDoNotImportCapabilityImplementationPackages() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize();

        for (String module : CALLER_MODULES) {
            Path sourceRoot = repository.resolve(module).resolve("src");
            try (var files = Files.walk(sourceRoot)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        for (String implementationPackage : IMPLEMENTATION_PACKAGES) {
                            assertThat(source)
                                    .as("implementation package import in %s", path)
                                    .doesNotContain(implementationPackage);
                        }
                    } catch (IOException e) {
                        throw new PackageStructureException(e);
                    }
                });
            }
        }
    }

    private static final class PackageStructureException extends RuntimeException {

        private PackageStructureException(IOException cause) {
            super(cause);
        }
    }
}
