package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ManifestOperationTest {

    @Test
    void derivesIdAsTheLowercaseHexSha256OfTheDocument() {
        // A well-known SHA-256 test vector, so this assertion does not depend on this test's own
        // hashing logic agreeing with itself.
        ManifestOperation operation = ManifestOperation.of("Greeting", "query", "hello");

        assertThat(operation.id())
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void identicalDocumentsProduceIdenticalIds() {
        ManifestOperation first = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
        ManifestOperation second = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentDocumentsProduceDifferentIds() {
        ManifestOperation first = ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");
        ManifestOperation second =
                ManifestOperation.of("GetBook", "query", "query GetBook { book { title pageCount } }");

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void rejectsAnIdThatDoesNotMatchTheDocumentsHash() {
        assertThatThrownBy(() -> new ManifestOperation("not-the-real-hash", "query GetBook { book { title } }",
                        "GetBook", "query"))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("GetBook")
                .hasMessageContaining("not-the-real-hash");
    }

    @Test
    void idForMatchesTheIdAnEquivalentEntryWouldHave() {
        String document = "query GetBook { book { title } }";

        assertThat(ManifestOperation.idFor(document))
                .isEqualTo(ManifestOperation.of("GetBook", "query", document).id());
    }

    @Test
    void rejectsABlankName() {
        String document = "query GetBook { book { title } }";
        assertThatThrownBy(() -> new ManifestOperation(OperationHash.sha256(document), document, " ", "query"))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("name");
    }
}
