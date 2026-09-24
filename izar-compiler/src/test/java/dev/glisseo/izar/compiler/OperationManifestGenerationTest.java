package dev.glisseo.izar.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import graphql.language.AstPrinter;
import graphql.parser.Parser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue 09's first two acceptance criteria: generation emits an Apollo-compatible manifest whose
 * IDs and bodies describe the exact final executable document, and identical inputs reproduce it.
 */
class OperationManifestGenerationTest {

    @Test
    void emitsAManifestEntryMatchingTheFinalExecutableDocument(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(List.of(schema), List.of(operation), tempDir.resolve("out"), "generated.book");

        assertThat(manifest.format()).isEqualTo("apollo-persisted-query-manifest");
        assertThat(manifest.version()).isEqualTo(1);
        assertThat(manifest.operations()).hasSize(1);

        ManifestOperation entry = manifest.operations().get(0);
        assertThat(entry.name()).isEqualTo("GetBook");
        assertThat(entry.type()).isEqualTo("query");
        // The body is minified (issue 18): insignificant whitespace collapsed, comments dropped.
        assertThat(entry.body())
                .isEqualTo(AstPrinter.printAstCompact(Parser.parse(Fixtures.GET_BOOK_OPERATION)));
        // ManifestOperation's own canonical constructor already rejects a mismatched id; this
        // asserts the compiler's entry does derive its id from that exact body, not some other
        // representation of the operation.
        assertThat(entry.id()).isEqualTo(ManifestOperation.of(entry.name(), entry.type(), entry.body()).id());
    }

    @Test
    void manifestBodyDropsCommentsAndInsignificantWhitespaceFromTheAuthoredOperation(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        # a leading comment the server never needs
                        query GetBook {
                          book {
                            title # a trailing comment
                          }
                        }
                        """);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(List.of(schema), List.of(operation), tempDir.resolve("out"), "generated.book");

        String body = manifest.operations().get(0).body();
        assertThat(body).isEqualTo("query GetBook{book{title}}");
    }

    @Test
    void classifiesAMutationsManifestEntryAsMutation(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_MUTATION);
        Path operation = writeFile(tempDir, "CreateBook.graphql", Fixtures.CREATE_BOOK_OPERATION);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(
                                List.of(schema), List.of(operation), tempDir.resolve("out"), "generated.book");

        assertThat(manifest.operations()).hasSize(1);
        assertThat(manifest.operations().get(0).type()).isEqualTo("mutation");
    }

    @Test
    void classifiesASubscriptionManifestEntryAsSubscription(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_SUBSCRIPTION);
        Path operation = writeFile(tempDir, "WatchBook.graphql", Fixtures.WATCH_BOOK_OPERATION);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(List.of(schema), List.of(operation), tempDir.resolve("out"), "generated.book");

        assertThat(manifest.operations()).singleElement().extracting(ManifestOperation::type)
                .isEqualTo("subscription");
    }

    @Test
    void manifestBodyIncludesAReferencedFragmentsDefinition(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path fragmentFile = writeFile(tempDir, "BookFields.graphql", Fixtures.BOOK_FIELDS_FRAGMENT);
        Path operation =
                writeFile(tempDir, "GetBookWithFragment.graphql", Fixtures.GET_BOOK_WITH_NAMED_FRAGMENT_OPERATION);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(
                                List.of(schema),
                                List.of(fragmentFile, operation),
                                tempDir.resolve("out"),
                                "generated.book");

        // Only the operation file produces a manifest entry; the fragment file is not itself an
        // operation, but its definition must still appear in the entry's body, since a server
        // cannot resolve the spread against a fragment it was never sent.
        assertThat(manifest.operations()).hasSize(1);
        assertThat(manifest.operations().get(0).body()).contains("fragment BookFields on Book");
    }

    @Test
    void manifestBodyIncludesAnInjectedTypeDiscriminator(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation = writeFile(tempDir, "GetFeaturedMedia.graphql", Fixtures.GET_FEATURED_MEDIA_OPERATION);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(List.of(schema), List.of(operation), tempDir.resolve("out"), "generated");

        String body = manifest.operations().get(0).body();
        // The discriminator is not in the authored operation file, only in the final document the
        // manifest and the generated DOCUMENT constant both derive from.
        assertThat(Fixtures.GET_FEATURED_MEDIA_OPERATION).doesNotContain("__typename");
        assertThat(body).contains("__typename");
    }

    @Test
    void identicalInputsProduceAnIdenticalManifest(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);

        OperationManifest first =
                new OperationCompiler()
                        .generate(List.of(schema), List.of(operation), tempDir.resolve("outA"), "generated.book");
        OperationManifest second =
                new OperationCompiler()
                        .generate(List.of(schema), List.of(operation), tempDir.resolve("outB"), "generated.book");

        assertThat(first).isEqualTo(second);
        assertThat(first.toJson()).isEqualTo(second.toJson());
    }

    @Test
    void manifestOperationsAreOrderedByOperationFile(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path getBook = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);
        Path getBookWithAlias =
                writeFile(tempDir, "GetBookWithAlias.graphql", Fixtures.GET_BOOK_WITH_ALIASES_OPERATION);

        OperationManifest manifest =
                new OperationCompiler()
                        .generate(
                                List.of(schema),
                                List.of(getBook, getBookWithAlias),
                                tempDir.resolve("out"),
                                "generated.book");

        assertThat(manifest.operations()).extracting(ManifestOperation::name)
                .containsExactly("GetBook", "GetBookWithAlias");
    }

    private static Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
