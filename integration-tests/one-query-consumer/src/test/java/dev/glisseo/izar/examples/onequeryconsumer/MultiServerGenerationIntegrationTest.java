package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MultiServerGenerationIntegrationTest {

    @Test
    void generatesSeparateSourcesAndManifestsForEachServerSchema() throws IOException {
        Path target = Path.of("target");
        Path booksManifest = target.resolve("izar/books-server/manifest.json");
        Path statusManifest = target.resolve("izar/status-server/manifest.json");

        assertThat(target.resolve(
                        "generated-sources/izar/dev/glisseo/izar/generated/books/GetBookQuery.java"))
                .exists();
        assertThat(target.resolve(
                        "generated-sources/izar/dev/glisseo/izar/generated/status/GetServiceStatusQuery.java"))
                .exists();

        OperationManifest books = OperationManifest.fromJson(Files.readString(booksManifest));
        OperationManifest status = OperationManifest.fromJson(Files.readString(statusManifest));
        assertThat(books.operations()).extracting(operation -> operation.name()).contains("GetBook");
        assertThat(books.operations()).extracting(operation -> operation.name()).doesNotContain("GetServiceStatus");
        assertThat(status.operations()).extracting(operation -> operation.name()).containsExactly("GetServiceStatus");
    }
}
