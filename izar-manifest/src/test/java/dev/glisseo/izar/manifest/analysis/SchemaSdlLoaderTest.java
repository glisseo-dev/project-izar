package dev.glisseo.izar.manifest.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaSdlLoaderTest {

    @Test
    void loadsAWellFormedSchemaFile(@TempDir Path dir) throws IOException {
        Path schemaFile = dir.resolve("schema.graphqls");
        Files.writeString(schemaFile, "type Query { thing: String }");

        var schema = SchemaSdlLoader.load(schemaFile);

        assertThat(schema.getQueryType().getName()).isEqualTo("Query");
        assertThat(schema.getQueryType().getFieldDefinition("thing")).isNotNull();
    }

    @Test
    void failsOnAMissingFile(@TempDir Path dir) {
        assertThatThrownBy(() -> SchemaSdlLoader.load(dir.resolve("missing.graphqls")))
                .isInstanceOf(InvalidSchemaException.class)
                .hasMessageContaining("Could not read schema file");
    }

    @Test
    void failsOnMalformedSdl() {
        assertThatThrownBy(() -> SchemaSdlLoader.parse("type Query { thing"))
                .isInstanceOf(InvalidSchemaException.class);
    }
}
