package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OperationManifestInputTest {

    @Test
    void acceptsOpaqueIdsAndPreservesTheBody() {
        String body = "query GetBook { book { title } }";
        String json = """
                {"format":"apollo-persisted-query-manifest","version":1,"operations":[
                  {"id":"catalog-v4:GetBook","body":"%s","name":"GetBook","type":"query"}
                ]}
                """.formatted(body);

        OperationManifestInput input = OperationManifestInput.fromJson(json);

        assertThat(input.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.id()).isEqualTo("catalog-v4:GetBook");
            assertThat(operation.body()).isEqualTo(body);
        });
    }

    @Test
    void rejectsBlankIds() {
        assertThatThrownBy(() -> new OperationManifestInput.Operation(" ", "query Q { x }", "Q", "query"))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("id");
    }
}
