package dev.glisseo.izar.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers a named fragment made entirely of leaf fields (scalars, {@code __typename}, and
 * lists of either), spread unconditionally, generates a shared top-level interface that every
 * merged record implements. {@link OperationCompilerTest} keeps covering that the merged record
 * itself is unaffected (still exactly one flattened {@code Book} record, still the same fields).
 */
class FragmentInterfaceGenerationTest {

    private static final String BOOK_SCHEMA =
            """
            type Query {
              book: Book
              books: [Book!]!
            }

            type Book {
              id: ID!
              name: String!
              author: Author
              genre: Genre!
              tags: [String]
            }

            type Author {
              name: String!
            }

            enum Genre {
              FICTION
              NONFICTION
            }
            """;

    private static final String SMALL_BOOK_FRAGMENT =
            """
            fragment SmallBoek on Book {
              id
              name
            }
            """;

    @Test
    void generatesASharedInterfaceAndImplementsItFromEveryUnconditionalSpread(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile = writeFile(tempDir, "SmallBoek.graphql", SMALL_BOOK_FRAGMENT);
        Path query1 =
                writeFile(
                        tempDir,
                        "Query1.graphql",
                        """
                        query Query1 {
                          books {
                            ...SmallBoek
                          }
                        }
                        """);
        Path query2 =
                writeFile(
                        tempDir,
                        "Query2.graphql",
                        """
                        query Query2 {
                          book {
                            ...SmallBoek
                            author {
                              name
                            }
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler()
                .generate(List.of(schema), List.of(fragmentFile, query1, query2), output, "generated.book");

        String fragmentInterface = Files.readString(generatedFile(output, "generated.book", "SmallBoek"));
        assertThat(fragmentInterface)
                .contains("package generated.book;")
                .contains("public interface SmallBoek {")
                .contains("String id();")
                .contains("String name();");

        String query1Source = Files.readString(generatedFile(output, "generated.book", "Query1Query"));
        assertThat(query1Source).contains("public record Book(").contains("implements SmallBoek {}");

        String query2Source = Files.readString(generatedFile(output, "generated.book", "Query2Query"));
        assertThat(query2Source)
                .containsOnlyOnce("public record Book(")
                .contains("implements SmallBoek {}")
                // the operation's own 'author' field is still merged in alongside the fragment's.
                .contains("@Nullable Author author");
    }

    @Test
    void interfaceAccessorCoversAListOfScalarField(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "TaggedBook.graphql",
                        """
                        fragment TaggedBook on Book {
                          id
                          tags
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...TaggedBook
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        String fragmentInterface = Files.readString(generatedFile(output, "generated.book", "TaggedBook"));
        assertThat(fragmentInterface).contains("List<@Nullable String> tags();");
        String source = Files.readString(generatedFile(output, "generated.book", "GetBookQuery"));
        assertThat(source).contains("implements TaggedBook {}");
    }

    @Test
    void doesNotImplementWhenTheSpreadItselfIsConditional(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile = writeFile(tempDir, "SmallBoek.graphql", SMALL_BOOK_FRAGMENT);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook($include: Boolean!) {
                          book {
                            ...SmallBoek @include(if: $include)
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        // The interface is still generated (the fragment's own definition is unconditional)...
        assertThat(Files.exists(generatedFile(output, "generated.book", "SmallBoek"))).isTrue();
        // ...but a conditional spread does not earn the merged record an 'implements' clause.
        String source = Files.readString(generatedFile(output, "generated.book", "GetBookQuery"));
        assertThat(source).contains("public record Book(").doesNotContain("implements SmallBoek");
    }

    @Test
    void doesNotGenerateAnInterfaceForAFragmentSelectingAnObjectTypedField(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "BookWithAuthor.graphql",
                        """
                        fragment BookWithAuthor on Book {
                          id
                          author {
                            name
                          }
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...BookWithAuthor
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "BookWithAuthor"))).isFalse();
        String source = Files.readString(generatedFile(output, "generated.book", "GetBookQuery"));
        assertThat(source).contains("public record Book(").doesNotContain("implements BookWithAuthor");
    }

    @Test
    void doesNotGenerateAnInterfaceForAFragmentSelectingAUnionTypedField(@TempDir Path tempDir) throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        type Query {
                          search: [SearchResult!]!
                        }

                        union SearchResult = Book | Movie

                        type Book {
                          title: String!
                        }

                        type Movie {
                          title: String!
                        }
                        """);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "QuerySearch.graphql",
                        """
                        fragment QuerySearch on Query {
                          search {
                            __typename
                          }
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "Search.graphql",
                        """
                        query Search {
                          ...QuerySearch
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "QuerySearch"))).isFalse();
    }

    @Test
    void doesNotGenerateAnInterfaceForAFragmentContainingANestedFragmentSpread(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path innerFragmentFile =
                writeFile(
                        tempDir,
                        "InnerFields.graphql",
                        """
                        fragment InnerFields on Book {
                          name
                        }
                        """);
        Path outerFragmentFile =
                writeFile(
                        tempDir,
                        "SmallBoek.graphql",
                        """
                        fragment SmallBoek on Book {
                          id
                          ...InnerFields
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...SmallBoek
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler()
                .generate(
                        List.of(schema),
                        List.of(innerFragmentFile, outerFragmentFile, operation),
                        output,
                        "generated.book");

        // SmallBoek itself is disqualified by its nested spread, even though InnerFields (the
        // fragment it spreads) is leaf-only and gets its own interface.
        assertThat(Files.exists(generatedFile(output, "generated.book", "SmallBoek"))).isFalse();
        assertThat(Files.exists(generatedFile(output, "generated.book", "InnerFields"))).isTrue();
    }

    @Test
    void implementsWhenTheFragmentIsSpreadInsideAPolymorphicFieldsSelection(@TempDir Path tempDir)
            throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        type Query {
                          search: [SearchResult!]!
                        }

                        union SearchResult = Book | Movie

                        type Book {
                          id: ID!
                          name: String!
                        }

                        type Movie {
                          title: String!
                        }
                        """);
        Path fragmentFile = writeFile(tempDir, "SmallBoek.graphql", SMALL_BOOK_FRAGMENT);
        Path operation =
                writeFile(
                        tempDir,
                        "Search.graphql",
                        """
                        query Search {
                          search {
                            __typename
                            ... on Book {
                              ...SmallBoek
                            }
                            ... on Movie {
                              title
                            }
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "SmallBoek"))).isTrue();
        // An unconditional spread inside a polymorphic field's branch selection earns that branch
        // record an 'implements' clause, the same as an ordinary object selection would.
        String source = Files.readString(generatedFile(output, "generated.book", "SearchQuery"));
        assertThat(source)
                .contains("String id")
                .contains("String name")
                .contains("implements SmallBoek");
    }

    @Test
    void doesNotImplementWhenTheSpreadInsideAPolymorphicBranchIsConditional(@TempDir Path tempDir)
            throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        type Query {
                          search: [SearchResult!]!
                        }

                        union SearchResult = Book | Movie

                        type Book {
                          id: ID!
                          name: String!
                        }

                        type Movie {
                          title: String!
                        }
                        """);
        Path fragmentFile = writeFile(tempDir, "SmallBoek.graphql", SMALL_BOOK_FRAGMENT);
        Path operation =
                writeFile(
                        tempDir,
                        "Search.graphql",
                        """
                        query Search($include: Boolean!) {
                          search {
                            __typename
                            ... on Book {
                              ...SmallBoek @include(if: $include)
                            }
                            ... on Movie {
                              title
                            }
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "SmallBoek"))).isTrue();
        String source = Files.readString(generatedFile(output, "generated.book", "SearchQuery"));
        assertThat(source).contains("String id").contains("String name").doesNotContain("implements SmallBoek");
    }

    @Test
    void implementsOnEveryBranchWhenTheFragmentIsSpreadAtThePolymorphicFieldsSharedLevel(@TempDir Path tempDir)
            throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        type Query {
                          search: [SearchResult!]!
                        }

                        interface SearchResult {
                          id: ID!
                        }

                        type Book implements SearchResult {
                          id: ID!
                          name: String!
                        }

                        type Movie implements SearchResult {
                          id: ID!
                          title: String!
                        }
                        """);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "WithId.graphql",
                        """
                        fragment WithId on SearchResult {
                          id
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "Search.graphql",
                        """
                        query Search {
                          search {
                            ...WithId
                            ... on Book {
                              name
                            }
                            ... on Movie {
                              title
                            }
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "WithId"))).isTrue();
        // A fragment spread unconditionally at the shared level of the polymorphic selection is
        // merged into every branch's fields (and the unrecognized catch-all's), so every one of
        // those records implements it too.
        String source = Files.readString(generatedFile(output, "generated.book", "SearchQuery"));
        assertThat(source)
                .containsOnlyOnce("public record SearchResultBook(")
                .containsOnlyOnce("public record SearchResultMovie(")
                .containsOnlyOnce("public record SearchResultUnrecognized(")
                .contains("implements WithId, SearchResult {}");
    }

    @Test
    void doesNotGenerateAnInterfaceForAFragmentSelectingAnEnumTypedField(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "BookWithGenre.graphql",
                        """
                        fragment BookWithGenre on Book {
                          id
                          genre
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...BookWithGenre
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "BookWithGenre"))).isFalse();
    }

    @Test
    void doesNotGenerateAnInterfaceForAFragmentWithADirectiveOnOneOfItsOwnFields(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "SmallBoek.graphql",
                        """
                        fragment SmallBoek on Book {
                          id
                          name @include(if: $include)
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook($include: Boolean!) {
                          book {
                            ...SmallBoek
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "SmallBoek"))).isFalse();
    }

    @Test
    void doesNotGenerateAnInterfaceForAFragmentContainingANestedInlineFragment(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "SmallBoek.graphql",
                        """
                        fragment SmallBoek on Book {
                          id
                          ... on Book {
                            name
                          }
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...SmallBoek
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        assertThat(Files.exists(generatedFile(output, "generated.book", "SmallBoek"))).isFalse();
    }

    @Test
    void silentlyDisqualifiesAFragmentWithAnUnmappedCustomScalarFieldButStillReportsTheUsualError(
            @TempDir Path tempDir) throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        scalar DateTime

                        type Query {
                          book: Book
                        }

                        type Book {
                          id: ID!
                          publishedAt: DateTime
                        }
                        """);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "BookDates.graphql",
                        """
                        fragment BookDates on Book {
                          id
                          publishedAt
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...BookDates
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        assertThatThrownBy(
                        () ->
                                new OperationCompiler()
                                        .generate(
                                                List.of(schema),
                                                List.of(fragmentFile, operation),
                                                output,
                                                "generated.book"))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("no configured scalar mapping")
                .satisfies(
                        exception ->
                                assertThat(((OperationGenerationException) exception).diagnostics()).hasSize(1));
        assertThat(Files.exists(generatedFile(output, "generated.book", "BookDates"))).isFalse();
    }

    @Test
    void failsGenerationWhenAFragmentNameCollidesWithAGeneratedOperationClassName(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", BOOK_SCHEMA);
        Path fragmentFile =
                writeFile(
                        tempDir,
                        "GetBookQuery.graphql",
                        """
                        fragment GetBookQuery on Book {
                          id
                          name
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...GetBookQuery
                          }
                        }
                        """);

        assertThatThrownBy(
                        () ->
                                new OperationCompiler()
                                        .generate(
                                                List.of(schema),
                                                List.of(fragmentFile, operation),
                                                tempDir.resolve("out"),
                                                "generated.book"))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("GetBookQuery")
                .hasMessageContaining("collides");
    }

    private static Path writeFile(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private static Path generatedFile(Path outputDirectory, String basePackage, String className) {
        Path directory = outputDirectory;
        for (String segment : basePackage.split("\\.")) {
            directory = directory.resolve(segment);
        }
        return directory.resolve(className + ".java");
    }
}
