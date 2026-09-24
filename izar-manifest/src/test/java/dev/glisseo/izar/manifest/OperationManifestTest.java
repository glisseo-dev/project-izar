package dev.glisseo.izar.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationManifestTest {

    private static final ManifestOperation GET_BOOK =
            ManifestOperation.of("GetBook", "query", "query GetBook { book { title } }");

    @Test
    void ofBuildsTheCurrentApolloCompatibleFormatAndVersion() {
        OperationManifest manifest = OperationManifest.of(List.of(GET_BOOK));

        assertThat(manifest.format()).isEqualTo("apollo-persisted-query-manifest");
        assertThat(manifest.version()).isEqualTo(1);
        assertThat(manifest.operations()).containsExactly(GET_BOOK);
    }

    @Test
    void toJsonEmitsTheApolloCompatibleShape() {
        String json = OperationManifest.of(List.of(GET_BOOK)).toJson();

        assertThat(json)
                .contains("\"format\" : \"apollo-persisted-query-manifest\"")
                .contains("\"version\" : 1")
                .contains("\"id\" : \"" + GET_BOOK.id() + "\"")
                .contains("\"body\" : \"query GetBook { book { title } }\"")
                .contains("\"name\" : \"GetBook\"")
                .contains("\"type\" : \"query\"");
    }

    @Test
    void toJsonThenFromJsonRoundTrips() {
        OperationManifest original = OperationManifest.of(List.of(GET_BOOK));

        OperationManifest roundTripped = OperationManifest.fromJson(original.toJson());

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void identicalOperationsProduceIdenticalJson() {
        OperationManifest first = OperationManifest.of(List.of(GET_BOOK));
        OperationManifest second =
                OperationManifest.of(List.of(ManifestOperation.of("GetBook", "query",
                        "query GetBook { book { title } }")));

        assertThat(first.toJson()).isEqualTo(second.toJson());
    }

    @Test
    void fromJsonRejectsAnEntryWhoseIdDoesNotMatchItsDocument() {
        String tampered =
                """
                {
                  "format" : "apollo-persisted-query-manifest",
                  "version" : 1,
                  "operations" : [ {
                    "id" : "0000000000000000000000000000000000000000000000000000000000000000",
                    "body" : "query GetBook { book { title } }",
                    "name" : "GetBook",
                    "type" : "query"
                  } ]
                }
                """;

        assertThatThrownBy(() -> OperationManifest.fromJson(tampered))
                .isInstanceOf(InvalidManifestException.class);
    }

    @Test
    void fromJsonRejectsMalformedJson() {
        assertThatThrownBy(() -> OperationManifest.fromJson("not json"))
                .isInstanceOf(InvalidManifestException.class);
    }

    @Test
    void rejectsABlankFormat() {
        assertThatThrownBy(() -> new OperationManifest(" ", 1, List.of(GET_BOOK)))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("format");
    }

    @Test
    void rejectsAnUnsupportedVersion() {
        assertThatThrownBy(
                        () -> new OperationManifest(OperationManifest.APOLLO_FORMAT, 2, List.of(GET_BOOK)))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsNullOperations() {
        assertThatThrownBy(() -> new OperationManifest(OperationManifest.APOLLO_FORMAT, 1, null))
                .isInstanceOf(InvalidManifestException.class)
                .hasMessageContaining("operations");
    }
}
