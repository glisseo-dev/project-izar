package dev.glisseo.izar.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationCompilerTest {

    @Test
    void jacksonGenerationEmitsSpringGraphQlModelsWithoutIzarDecoders(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation = writeFile(tempDir, "GetFeaturedMedia.graphql", Fixtures.GET_FEATURED_MEDIA_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.jackson", GenerationMode.JACKSON);

        String source = Files.readString(generatedFile(output, "generated.jackson", "GetFeaturedMediaQuery"));
        assertThat(source)
                .contains("implements GraphQlRequest")
                .contains("@JsonTypeInfo")
                .contains("@JsonSubTypes")
                .doesNotContain("implements GraphQlOperation<")
                .doesNotContain("GraphQlDecoding")
                .doesNotContain("DecodingPolicy")
                .doesNotContain(" decode(");
    }

    @Test
    void jacksonGenerationUsesStringsForOutputEnums(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_OUTPUT_ENUM);
        Path operation = writeFile(tempDir, "GetBookGenre.graphql", Fixtures.GET_BOOK_GENRE_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.jackson", GenerationMode.JACKSON);

        String source = Files.readString(generatedFile(output, "generated.jackson", "GetBookGenreQuery"));
        assertThat(source)
                .contains("String genre")
                .doesNotContain("public sealed interface Genre")
                .doesNotContain("GraphQlDecoding");
    }

    @Test
    void jacksonGenerationKeepsVariableBuildersWithoutGeneratingDecoders(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_MUTATION);
        Path operation = writeFile(tempDir, "CreateBook.graphql", Fixtures.CREATE_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.jackson", GenerationMode.JACKSON);

        String source = Files.readString(generatedFile(output, "generated.jackson", "CreateBookMutation"));
        assertThat(source)
                .contains("implements GraphQlRequest")
                .contains("import dev.glisseo.izar.operation.GraphQlEncoding;")
                .contains("public static Builder builder()")
                .contains("public Map<String, Object> variables()")
                .doesNotContain("GraphQlDecoding")
                .doesNotContain("DecodingPolicy");
    }

    @Test
    void generatesSimpleNestedQuery(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookQuery"));

        assertThat(source)
                .contains("package generated.book;")
                .contains("public final class GetBookQuery implements GraphQlOperation<GetBookQuery.Data>")
                .contains("public static final String OPERATION_NAME = \"GetBook\";")
                .contains("@Nullable Book book")
                .contains("String title")
                .contains("@Nullable Integer pageCount")
                .contains("@Nullable Author author")
                .contains("String name")
                .contains("decodeBook(GraphQlDecoding.asMap(bookValue), policy)")
                .contains("GraphQlDecoding.asString(titleValue)")
                .contains("GraphQlDecoding.asInt(pageCountValue)");
    }

    @Test
    void generatesASubscriptionWithSubscriptionMetadata(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_SUBSCRIPTION);
        Path operation = writeFile(tempDir, "WatchBook.graphql", Fixtures.WATCH_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        OperationManifest manifest =
                new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "WatchBookSubscription"));
        assertThat(source)
                .contains("public final class WatchBookSubscription")
                .contains("GraphQlOperationKind.SUBSCRIPTION")
                .contains("public GraphQlOperationKind operationKind()")
                .contains("bookChanged");
        assertThat(manifest.operations()).singleElement().satisfies(entry ->
                assertThat(entry.type()).isEqualTo("subscription"));
    }

    @Test
    void rejectsTwoOperationFilesThatGenerateTheSameJavaType(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path first = writeFile(Files.createDirectories(tempDir.resolve("a")), "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);
        Path second = writeFile(Files.createDirectories(tempDir.resolve("b")), "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        assertThatThrownBy(() -> new OperationCompiler()
                        .generate(List.of(schema), List.of(first, second), output, "generated.book"))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("'GetBook'")
                .hasMessageContaining("same Java type");
    }

    @Test
    void generatedOperationIdMatchesTheManifestEntrysId(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        OperationManifest manifest =
                new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookQuery"));
        ManifestOperation entry = manifest.operations().get(0);

        // Both the generated OPERATION_ID constant and the manifest entry derive from the same
        // hash computed once by the compiler, not two independent hashes that merely happen to
        // agree.
        assertThat(source).contains("public static final String OPERATION_ID = \"" + entry.id() + "\";");
    }

    @Test
    void generatesFromManifestAndPreservesTheSuppliedIdAndDocument(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        String body = "query GetBook { book { title } }";
        Path manifest = writeFile(
                tempDir,
                "manifest.json",
                """
                {"format":"apollo-persisted-query-manifest","version":1,"operations":[
                  {"id":"server-operation-42","body":"%s","name":"GetBook","type":"query"}
                ]}
                """.formatted(body));
        Path output = tempDir.resolve("out");

        new OperationCompiler().generateFromManifest(List.of(schema), manifest, output, "generated.book", List.of());

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookQuery"));
        assertThat(source)
                .contains("public static final String OPERATION_ID = \"server-operation-42\";")
                .contains("public static final String DOCUMENT = \"" + body + "\";");
    }

    @Test
    void rejectsManifestPolymorphismWithoutTypenameInsteadOfRewritingTheDocument(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        String body = Fixtures.GET_FEATURED_MEDIA_OPERATION.strip().replace("\n", " ").replaceAll(" +", " ");
        Path manifest = writeFile(
                tempDir,
                "manifest.json",
                """
                {"format":"apollo-persisted-query-manifest","version":1,"operations":[
                  {"id":"remote-id","body":"%s","name":"GetFeaturedMedia","type":"query"}
                ]}
                """.formatted(body));

        assertThatThrownBy(() -> new OperationCompiler().validateManifest(List.of(schema), manifest, "generated", List.of()))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("GetFeaturedMedia")
                .hasMessageContaining("unconditional __typename")
                .hasMessageContaining("remote-id");
    }

    @Test
    void rejectsManifestOperationsThatWouldOverwriteTheSameJavaClass(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path manifest = writeFile(
                tempDir,
                "manifest.json",
                """
                {"format":"apollo-persisted-query-manifest","version":1,"operations":[
                  {"id":"catalog-title","body":"query GetBook { book { title } }","name":"GetBook","type":"query"},
                  {"id":"catalog-pages","body":"query GetBook { book { pageCount } }","name":"GetBook","type":"query"}
                ]}
                """);

        assertThatThrownBy(() -> new OperationCompiler().validateManifest(List.of(schema), manifest, "generated", List.of()))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("catalog-title")
                .hasMessageContaining("catalog-pages")
                .hasMessageContaining("GetBookQuery");
    }

    @Test
    void generationIsDeterministic(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_OPERATION);
        Path outputA = tempDir.resolve("outA");
        Path outputB = tempDir.resolve("outB");

        new OperationCompiler().generate(List.of(schema), List.of(operation), outputA, "generated.book");
        new OperationCompiler().generate(List.of(schema), List.of(operation), outputB, "generated.book");

        String sourceA = Files.readString(generatedFile(outputA, "generated.book", "GetBookQuery"));
        String sourceB = Files.readString(generatedFile(outputB, "generated.book", "GetBookQuery"));
        assertThat(sourceA).isEqualTo(sourceB);
    }

    @Test
    void rejectsAnUnknownField(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            titlee
                          }
                        }
                        """);

        assertGenerationFails(tempDir, schema, operation, "titlee");
    }

    @Test
    void generatesAMutationWithVariablesNestedInputObjectsEnumsAndListsOfInputs(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_MUTATION);
        Path operation = writeFile(tempDir, "CreateBook.graphql", Fixtures.CREATE_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "CreateBookMutation"));

        assertThat(source)
                .contains("public final class CreateBookMutation implements GraphQlOperation<CreateBookMutation.Data>")
                .contains("public static Builder builder()")
                .contains("public Builder input(BookInput input)")
                .contains("public static final class BookInput")
                .contains("public static final class AuthorInput")
                .contains("public enum Genre")
                .contains("FICTION")
                .contains("NONFICTION")
                // 'title' is required with no default: unset must fail before a request.
                .contains("\"BookInput.title is required and was not set.\"")
                // 'genre' has a schema default: unset must not fail, unlike 'title'.
                .doesNotContain("\"BookInput.genre is required and was not set.\"")
                .contains("List<@Nullable String> tags")
                .contains("List<AuthorInput> coAuthors")
                .contains("GraphQlEncoding.copyList(tags)")
                // 'tags' elements are nullable (String, not String!): null passes through.
                .contains("GraphQlEncoding.encodeList(tags")
                // 'coAuthors' elements are non-null (AuthorInput!): a null element must fail
                // validation rather than reach a request.
                .contains("GraphQlEncoding.encodeNonNullElementList(coAuthors");
    }

    @Test
    void doesNotRequireANonNullTopLevelVariableThatHasAUsableOperationDefault(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_MUTATION);
        Path operation = writeFile(tempDir, "SetFeatureFlag.graphql", Fixtures.SET_FEATURE_FLAG_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "SetFeatureFlagMutation"));
        // '$enabled' is non-null but has an operation default ('= true'): unlike CreateBook's
        // '$input', leaving it unset must not be a required-input failure.
        assertThat(source).doesNotContain("\"variables.enabled is required and was not set.\"");
    }

    @Test
    void decodesAnAliasIntoItsOwnRecordComponentAtEveryNestingDepth(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation = writeFile(tempDir, "GetBook.graphql", Fixtures.GET_BOOK_WITH_ALIASES_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookWithAliasQuery"));
        assertThat(source)
                // The nested record's Java type name derives from the response key ('b'), not
                // the schema field name ('book'): an alias lets a query author pick the generated
                // record name, e.g. to disambiguate two same-typed fields that would otherwise
                // collide on the schema type name.
                .contains("@Nullable B b")
                .contains("String bookTitle")
                .contains("map.get(\"b\")")
                .contains("map.get(\"bookTitle\")")
                .contains("GraphQlDecoding.asString(bookTitleValue)");
    }

    @Test
    void mergesANamedFragmentReferencedFromASeparateFileWithTheOperationsOwnSelections(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path fragmentFile = writeFile(tempDir, "BookFields.graphql", Fixtures.BOOK_FIELDS_FRAGMENT);
        Path operation =
                writeFile(tempDir, "GetBookWithFragment.graphql", Fixtures.GET_BOOK_WITH_NAMED_FRAGMENT_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler()
                .generate(List.of(schema), List.of(fragmentFile, operation), output, "generated.book");

        String source =
                Files.readString(generatedFile(output, "generated.book", "GetBookWithFragmentQuery"));
        assertThat(source)
                .contains("String title")
                .contains("@Nullable Integer pageCount")
                .contains("@Nullable Author author")
                .contains("String name")
                // Exactly one Book record: the fragment's fields and the operation's own author
                // field merge into one type rather than generating a separate type per fragment.
                .containsOnlyOnce("public record Book(");
        // The final executable document includes the fragment's definition, since a server
        // cannot resolve '...BookFields' against a definition it was never sent.
        assertThat(source).contains("fragment BookFields on Book");
    }

    @Test
    void mergesADirectSelectionWithAnOverlappingInlineFragmentSelectionIntoOneType(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation =
                writeFile(
                        tempDir, "GetBookWithInlineFragment.graphql", Fixtures.GET_BOOK_WITH_INLINE_FRAGMENT_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source =
                Files.readString(generatedFile(output, "generated.book", "GetBookWithInlineFragmentQuery"));
        assertThat(source)
                .containsOnlyOnce("String title")
                .containsOnlyOnce("@Nullable Integer pageCount")
                .containsOnlyOnce("public record Book(");
    }

    @Test
    void makesAConditionallySelectedSchemaNonNullFieldNullable(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBookConditional.graphql",
                        Fixtures.GET_BOOK_WITH_CONDITIONAL_SELECTIONS_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookConditionalQuery"));
        // 'title' is 'String!' in the schema but carries '@skip': it may be absent at runtime
        // regardless of the schema, so the generated component must be nullable.
        assertThat(source).contains("@Nullable String title");
    }

    @Test
    void keepsAFieldNonNullWhenAtLeastOneSelectionPathToItIsUnconditional(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBookPartiallyConditional.graphql",
                        Fixtures.GET_BOOK_WITH_PARTIALLY_CONDITIONAL_FIELD_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source =
                Files.readString(
                        generatedFile(output, "generated.book", "GetBookPartiallyConditionalQuery"));
        // 'title' is selected unconditionally at the top level too, so the conditional inline
        // fragment selecting it again must not make it nullable.
        assertThat(source).containsOnlyOnce("String title").doesNotContain("@Nullable String title");
    }

    @Test
    void generatesNestedListsPreservingContainerAndElementNullabilityIndependently(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_LISTS);
        Path operation = writeFile(tempDir, "GetBooks.graphql", Fixtures.GET_BOOKS_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "GetBooksQuery"));
        assertThat(source)
                // 'books: [Book!]!': non-null container of non-null elements. A list element's
                // nested record is named after its schema type ('Book'), not the plural field
                // name ('books').
                .contains("List<Book> books")
                // 'tags: [String]': nullable container of a nullable scalar element.
                .contains("List<@Nullable String> tags")
                // 'ratings: [[Int]]': nullable container of nullable lists of a nullable scalar.
                .contains("List<@Nullable List<@Nullable Integer>> ratings")
                .contains("GraphQlDecoding.decodeList(booksValue");
    }

    @Test
    void rejectsConflictingAliasedSelectionsThroughGraphQlValidation(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            a: title
                            a: pageCount
                          }
                        }
                        """);

        assertGenerationFails(tempDir, schema, operation, "a");
    }

    @Test
    void rejectsAFragmentSpreadOnAnIncompatibleType(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path fragmentFile = writeFile(tempDir, "AuthorFields.graphql", Fixtures.AUTHOR_FIELDS_FRAGMENT);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook {
                          book {
                            ...AuthorFields
                          }
                        }
                        """);

        assertGenerationFails(tempDir, schema, List.of(fragmentFile, operation), "AuthorFields");
    }

    @Test
    void rejectsTwoFragmentsWithTheSameNameDefinedInDifferentFiles(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA);
        Path firstFragmentFile = writeFile(tempDir, "First.graphql", Fixtures.BOOK_FIELDS_FRAGMENT);
        Path secondFragmentFile =
                writeFile(
                        tempDir,
                        "Second.graphql",
                        """
                        fragment BookFields on Book {
                          title
                        }
                        """);
        Path operation =
                writeFile(tempDir, "GetBookWithFragment.graphql", Fixtures.GET_BOOK_WITH_NAMED_FRAGMENT_OPERATION);

        assertThatThrownBy(
                        () ->
                                new OperationCompiler()
                                        .generate(
                                                List.of(schema),
                                                List.of(firstFragmentFile, secondFragmentFile, operation),
                                                tempDir.resolve("out"),
                                                "generated"))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("already defined");
    }

    @Test
    void generatesFieldArgumentsUsingBothLiteralsAndVariables(@TempDir Path tempDir) throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        type Query {
                          book(id: ID!, preview: Boolean): Book
                        }

                        type Book {
                          title: String!
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetBook.graphql",
                        """
                        query GetBook($id: ID!) {
                          book(id: $id, preview: true) {
                            title
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetBookQuery"));
        assertThat(source)
                .contains("book(id:$id,preview:true)")
                .contains("public Builder id(String id)");
    }

    @Test
    void requiresAVariableToBeSetBeforeExecutingWhenItHasNoUsableDefault(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_MUTATION);
        Path operation = writeFile(tempDir, "CreateBook.graphql", Fixtures.CREATE_BOOK_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "CreateBookMutation"));
        // The operation-level 'input' variable is non-null with no operation default.
        assertThat(source).contains("\"variables.input is required and was not set.\"");
    }

    @Test
    void rejectsUnmappedCustomScalars(@TempDir Path tempDir) throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        scalar DateTime

                        type Query {
                          now: DateTime
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetNow.graphql",
                        """
                        query GetNow {
                          now
                        }
                        """);

        assertGenerationFails(tempDir, schema, operation, "no configured scalar mapping");
    }

    @Test
    void generatesACodecFieldAndScalarEncodeDecodeCallsForAConfiguredCustomScalar(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_SCALARS);
        Path operation = writeFile(tempDir, "GetBookWithScalars.graphql", Fixtures.GET_BOOK_WITH_SCALARS_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler()
                .generate(
                        List.of(schema),
                        List.of(operation),
                        output,
                        "generated.book",
                        List.of(
                                new ScalarMapping("DateTime", "java.time.Instant", "dev.glisseo.izar.scalars.InstantScalarCodec"),
                                new ScalarMapping(
                                        "Money", "java.math.BigDecimal", "dev.glisseo.izar.compiler.fixtures.MoneyScalarCodec")));

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookWithScalarsQuery"));

        assertThat(source)
                .contains(
                        "private static final ScalarCodec<java.time.Instant> CODEC_DATE_TIME = new dev.glisseo.izar.scalars.InstantScalarCodec();")
                .contains(
                        "private static final ScalarCodec<java.math.BigDecimal> CODEC_MONEY = new dev.glisseo.izar.compiler.fixtures.MoneyScalarCodec();")
                .contains("java.time.@Nullable Instant publishedAt")
                .contains("java.math.@Nullable BigDecimal price")
                .contains("GraphQlDecoding.decodeScalar(publishedAtValue, CODEC_DATE_TIME, \"DateTime\")")
                .contains("GraphQlDecoding.decodeScalar(priceValue, CODEC_MONEY, \"Money\")");
    }

    @Test
    void namesOnlyTheStillUnmappedScalarWhenOthersAreConfigured(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_SCALARS);
        Path operation = writeFile(tempDir, "GetBookWithScalars.graphql", Fixtures.GET_BOOK_WITH_SCALARS_OPERATION);

        assertThatThrownBy(
                        () ->
                                new OperationCompiler()
                                        .generate(
                                                List.of(schema),
                                                List.of(operation),
                                                tempDir.resolve("out"),
                                                "generated.book",
                                                List.of(
                                                        new ScalarMapping(
                                                                "DateTime",
                                                                "java.time.Instant",
                                                                "dev.glisseo.izar.scalars.InstantScalarCodec"))))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("Money")
                .hasMessageContaining("no configured scalar mapping");
    }

    @Test
    void rejectsTwoScalarMappingsConfiguredForTheSameGraphQlScalarName(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_SCALARS);
        Path operation = writeFile(tempDir, "GetBookWithScalars.graphql", Fixtures.GET_BOOK_WITH_SCALARS_OPERATION);

        assertThatThrownBy(
                        () ->
                                new OperationCompiler()
                                        .generate(
                                                List.of(schema),
                                                List.of(operation),
                                                tempDir.resolve("out"),
                                                "generated.book",
                                                List.of(
                                                        new ScalarMapping(
                                                                "DateTime",
                                                                "java.time.Instant",
                                                                "dev.glisseo.izar.scalars.InstantScalarCodec"),
                                                        new ScalarMapping(
                                                                "DateTime",
                                                                "java.time.OffsetDateTime",
                                                                "dev.glisseo.izar.scalars.InstantScalarCodec"))))
                // Reported the same clean, catchable way as any other generation failure (an
                // unmapped scalar included), not a raw runtime exception a build tool would print
                // unhelpfully: see ScalarMappingRegistry.of.
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining("Duplicate")
                .hasMessageContaining("DateTime");
    }

    @Test
    void generatesAnOutputEnumAsASealedInterfaceWithAKnownAndUnrecognizedCase(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_OUTPUT_ENUM);
        Path operation = writeFile(tempDir, "GetBookGenre.graphql", Fixtures.GET_BOOK_GENRE_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated.book");

        String source = Files.readString(generatedFile(output, "generated.book", "GetBookGenreQuery"));
        assertThat(source)
                .contains("public sealed interface Genre")
                .contains("enum Known implements Genre")
                .contains("FICTION")
                .contains("NONFICTION")
                .contains("record Unrecognized(String rawValue) implements Genre")
                .contains("Genre genre")
                .contains(
                        "GraphQlDecoding.decodeEnum(genreValue, Genre.Known.class, known -> known,"
                                + " Genre.Unrecognized::new, \"Genre\", policy)");
    }

    @Test
    void generatesAUnionFieldAsASealedInterfaceWithOneBranchPerConcreteTypeAndReusesAnExplicitTypename(
            @TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.SEARCH_RESULT_SCHEMA);
        Path operation = writeFile(tempDir, "Search.graphql", Fixtures.SEARCH_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "SearchQuery"));
        assertThat(source)
                // 'search: [SearchResult!]!': the list element's sealed interface is named after
                // its schema type ('SearchResult'), not the plural field name ('search').
                .contains(
                        "public sealed interface SearchResult permits SearchResultBook, SearchResultMovie,"
                                + " SearchResultUnrecognized")
                // 'search' also selects '__typename' directly (a shared field): it merges into
                // every branch, including the concrete ones, not just Unrecognized.
                .contains(
                        "record SearchResultBook(\n            String __typename,\n            String title\n    )"
                                + " implements SearchResult")
                .contains("director")
                .contains(
                        "record SearchResultUnrecognized(\n            String __typename\n    ) implements"
                                + " SearchResult")
                .contains("String typename = GraphQlDecoding.asString(map.get(\"__typename\"));")
                .contains("case \"Book\" -> decodeSearchResultBook(map, policy);")
                .contains("case \"Movie\" -> decodeSearchResultMovie(map, policy);")
                .contains("yield decodeSearchResultUnrecognized(map, policy);")
                // The operation already selects '__typename' directly: the compiler must not add
                // a second, aliased discriminator field to reach it.
                .doesNotContain("__typename2");
    }

    @Test
    void injectsATypeDiscriminatorWhenTheOperationDoesNotSelectOneItself(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation = writeFile(tempDir, "GetFeaturedMedia.graphql", Fixtures.GET_FEATURED_MEDIA_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetFeaturedMediaQuery"));
        // The injected discriminator lives in the final executable DOCUMENT, not the authored
        // source: the inline fragments' type conditions are untouched, just minified like the
        // rest of the document.
        assertThat(source)
                .contains("public static final String DOCUMENT = ")
                .contains("__typename")
                .contains("...on Book")
                .contains("...on Movie");
    }

    @Test
    void generationOfADiscriminatedDocumentIsDeterministic(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation = writeFile(tempDir, "GetFeaturedMedia.graphql", Fixtures.GET_FEATURED_MEDIA_OPERATION);
        Path outputA = tempDir.resolve("outA");
        Path outputB = tempDir.resolve("outB");

        new OperationCompiler().generate(List.of(schema), List.of(operation), outputA, "generated");
        new OperationCompiler().generate(List.of(schema), List.of(operation), outputB, "generated");

        String sourceA = Files.readString(generatedFile(outputA, "generated", "GetFeaturedMediaQuery"));
        String sourceB = Files.readString(generatedFile(outputB, "generated", "GetFeaturedMediaQuery"));
        assertThat(sourceA).isEqualTo(sourceB);
    }

    @Test
    void doesNotGenerateAPolymorphicTypeWhenNoFragmentNarrowsTheSelection(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation =
                writeFile(
                        tempDir, "GetFeaturedMediaTitleOnly.graphql", Fixtures.GET_FEATURED_MEDIA_TITLE_ONLY_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetFeaturedMediaTitleOnlyQuery"));
        assertThat(source)
                .doesNotContain("sealed interface")
                .doesNotContain("__typename")
                .contains("public record Featured(\n            String title\n    ) {}");
    }

    @Test
    void reusesOneNestedObjectTypeForAPolymorphicFieldsSharedObjectTypedFieldAcrossEveryBranch(
            @TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.CELESTIAL_OBJECT_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetFeaturedCelestialObject.graphql",
                        Fixtures.GET_FEATURED_CELESTIAL_OBJECT_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetFeaturedCelestialObjectQuery"));
        // 'constellation' is a shared interface field selecting only scalars, identically in every
        // branch (Star/Nebula/Galaxy) and the Unrecognized fallback: it must resolve to exactly one
        // 'Constellation' record, reused everywhere, not a fresh 'Constellation2'/'Constellation3'/
        // 'Constellation4' per branch.
        assertThat(source)
                .contains("public record Constellation(\n            String name\n    ) {}")
                .doesNotContain("Constellation2")
                .doesNotContain("Constellation3")
                .doesNotContain("Constellation4")
                .contains("Constellation constellation")
                .contains("record FeaturedStar(")
                .contains("record FeaturedNebula(")
                .contains("record FeaturedGalaxy(")
                .contains("record FeaturedUnrecognized(");
    }

    @Test
    void hoistsAPolymorphicFieldsUnconditionallySharedFieldsOntoTheSealedInterface(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.CELESTIAL_OBJECT_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetFeaturedCelestialObject.graphql",
                        Fixtures.GET_FEATURED_CELESTIAL_OBJECT_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetFeaturedCelestialObjectQuery"));
        // 'name' and 'constellation' are selected unconditionally on every branch, so callers get
        // them straight off the interface instead of an exhaustive switch to reach a field every
        // branch already has.
        assertThat(source)
                .contains(
                        "public sealed interface Featured permits FeaturedStar, FeaturedNebula, FeaturedGalaxy,"
                                + " FeaturedUnrecognized {\n"
                                + "\n        String name();\n"
                                + "\n        Constellation constellation();\n    }")
                .contains("record FeaturedStar(\n            String name,\n            Constellation constellation,")
                .contains("implements Featured");
    }

    @Test
    void doesNotHoistABranchSpecificOverrideOfASharedField(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.CELESTIAL_OBJECT_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetFeaturedCelestialObject.graphql",
                        """
                        query GetFeaturedCelestialObject {
                          featured {
                            name
                            constellation {
                              name
                            }
                            ... on Star {
                              spectralType
                              constellation {
                                name
                              }
                            }
                            ... on Nebula {
                              nebulaType
                            }
                            ... on Galaxy {
                              distanceLightYears
                            }
                          }
                        }
                        """);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetFeaturedCelestialObjectQuery"));
        // 'Star' re-selects 'constellation' itself: even though it asks for the exact same
        // sub-selection, the compiler cannot assume that in general, so 'constellation' stays a
        // branch-only accessor and only the untouched 'name' is hoisted.
        assertThat(source)
                .contains("public sealed interface Featured permits FeaturedStar, FeaturedNebula, FeaturedGalaxy,"
                        + " FeaturedUnrecognized {\n"
                        + "\n        String name();\n    }");
    }

    @Test
    void generatesANullableOutputEnumFieldWithCorrectNullability(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.BOOK_SCHEMA_WITH_NULLABLE_OUTPUT_ENUM);
        Path operation = writeFile(tempDir, "GetBookNullableGenre.graphql", Fixtures.GET_BOOK_NULLABLE_GENRE_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetBookNullableGenreQuery"));
        assertThat(source).contains("@Nullable Genre genre");
    }

    @Test
    void generatesANullablePolymorphicFieldWithCorrectNullability(@TempDir Path tempDir) throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation = writeFile(tempDir, "GetFeaturedMedia.graphql", Fixtures.GET_FEATURED_MEDIA_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source = Files.readString(generatedFile(output, "generated", "GetFeaturedMediaQuery"));
        // 'featured: Media' (no '!') in the schema: the sealed interface field itself stays
        // nullable, independent of any branch's own fields.
        assertThat(source).contains("@Nullable Featured featured");
    }

    @Test
    void fallsBackToADifferentDiscriminatorKeyWhenTypenameIsAlreadyAliasedToSomethingElse(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation =
                writeFile(
                        tempDir,
                        "GetFeaturedMediaWithTypenameAlias.graphql",
                        Fixtures.GET_FEATURED_MEDIA_WITH_TYPENAME_ALIAS_OPERATION);
        Path output = tempDir.resolve("out");

        new OperationCompiler().generate(List.of(schema), List.of(operation), output, "generated");

        String source =
                Files.readString(generatedFile(output, "generated", "GetFeaturedMediaWithTypenameAliasQuery"));
        // The operation's own alias ('__typename: title') keeps the response key '__typename'
        // for 'title', so the compiler's own discriminator must use a different key: injecting
        // its own '__typename' here would either collide or misdecode 'title' as the type name.
        assertThat(source)
                .contains("izarTypename")
                .doesNotContain("String typename = GraphQlDecoding.asString(map.get(\"__typename\"))");
    }

    @Test
    void leavesTheAuthoredOperationFileUnchangedWhenGeneratingADiscriminatedDocument(@TempDir Path tempDir)
            throws IOException {
        Path schema = writeFile(tempDir, "schema.graphqls", Fixtures.MEDIA_SCHEMA);
        Path operation = writeFile(tempDir, "GetFeaturedMedia.graphql", Fixtures.GET_FEATURED_MEDIA_OPERATION);
        String originalText = Files.readString(operation);

        new OperationCompiler().generate(List.of(schema), List.of(operation), tempDir.resolve("out"), "generated");

        assertThat(Files.readString(operation)).isEqualTo(originalText);
    }

    @Test
    void rejectsAFragmentOnAnUnrelatedInterfaceInsideAPolymorphicSelection(@TempDir Path tempDir) throws IOException {
        Path schema =
                writeFile(
                        tempDir,
                        "schema.graphqls",
                        """
                        type Query {
                          featured: Media
                        }

                        interface Media {
                          title: String!
                        }

                        interface OtherInterface {
                          title: String!
                        }

                        type Book implements Media & OtherInterface {
                          title: String!
                        }
                        """);
        Path operation =
                writeFile(
                        tempDir,
                        "GetFeaturedMedia.graphql",
                        """
                        query GetFeaturedMedia {
                          featured {
                            ... on OtherInterface {
                              title
                            }
                          }
                        }
                        """);

        assertGenerationFails(tempDir, schema, operation, "OtherInterface");
    }

    private static void assertGenerationFails(
            Path tempDir, Path schema, Path operation, String messageFragment) {
        assertGenerationFails(tempDir, schema, List.of(operation), messageFragment);
    }

    private static void assertGenerationFails(
            Path tempDir, Path schema, List<Path> operationFiles, String messageFragment) {
        assertThatThrownBy(
                        () ->
                                new OperationCompiler()
                                        .generate(
                                                List.of(schema),
                                                operationFiles,
                                                tempDir.resolve("out"),
                                                "generated"))
                .isInstanceOf(OperationGenerationException.class)
                .hasMessageContaining(messageFragment);
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
