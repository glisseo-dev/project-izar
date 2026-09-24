package dev.glisseo.izar.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.operation.DecodingPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Actually compiles a generated operation with {@code javac} and runs its {@code decode} method,
 * rather than only asserting on generated source text. This is what proves generated decode
 * logic works, independent of the example project's full Maven+Spring integration test.
 */
class CompiledOperationDecodingTest {

    @Test
    void generatedOperationCompilesAndDecodesRawResponseData(@TempDir Path tempDir) throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("GetBook.graphql");
        Files.writeString(schema, Fixtures.BOOK_SCHEMA);
        Files.writeString(operation, Fixtures.GET_BOOK_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.book");

        Path javaFile =
                generatedSources.resolve("generated").resolve("book").resolve("GetBookQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        Map<String, Object> author = Map.of("name", "Frank Herbert");
        Map<String, Object> book = new HashMap<>();
        book.put("title", "Dune");
        book.put("pageCount", 412);
        book.put("author", author);
        Map<String, Object> data = Map.of("book", book);

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.book.GetBookQuery", true, loader);
            Object operation2 = operationClass.getDeclaredConstructor().newInstance();

            Object decodedData = operationClass.getMethod("decode", Object.class).invoke(operation2, data);
            Object decodedBook = decodedData.getClass().getMethod("book").invoke(decodedData);

            assertThat(decodedBook.getClass().getMethod("title").invoke(decodedBook)).isEqualTo("Dune");
            assertThat(decodedBook.getClass().getMethod("pageCount").invoke(decodedBook)).isEqualTo(412);

            Object decodedAuthor = decodedBook.getClass().getMethod("author").invoke(decodedBook);
            assertThat(decodedAuthor.getClass().getMethod("name").invoke(decodedAuthor))
                    .isEqualTo("Frank Herbert");
        }
    }

    @Test
    void decodeFailsUsefullyWhenANonNullFieldIsMissing(@TempDir Path tempDir) throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("GetBook.graphql");
        Files.writeString(schema, Fixtures.BOOK_SCHEMA);
        Files.writeString(operation, Fixtures.GET_BOOK_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.book");

        Path javaFile =
                generatedSources.resolve("generated").resolve("book").resolve("GetBookQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        // 'title' is schema-non-null (String!): a spec-compliant server would have nulled 'book'
        // itself instead of sending it with 'title' missing, so this map is deliberately
        // malformed. Decode must fail clearly rather than throwing a raw NullPointerException or
        // fabricating a value.
        Map<String, Object> book = new HashMap<>();
        book.put("pageCount", 412);
        Map<String, Object> data = Map.of("book", book);

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.book.GetBookQuery", true, loader);
            Object operationInstance = operationClass.getDeclaredConstructor().newInstance();
            Method decode = operationClass.getMethod("decode", Object.class);

            assertThatThrownBy(() -> decode.invoke(operationInstance, data))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("String");
        }
    }

    @Test
    void decodesBothOutcomesOfAConditionallySelectedField(@TempDir Path tempDir) throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("GetBookConditional.graphql");
        Files.writeString(schema, Fixtures.BOOK_SCHEMA);
        Files.writeString(operation, Fixtures.GET_BOOK_WITH_CONDITIONAL_SELECTIONS_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.book");

        Path javaFile =
                generatedSources.resolve("generated").resolve("book").resolve("GetBookConditionalQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        // A server omits a '@skip'ped or unincluded field entirely rather than sending it as
        // null, so these maps leave the corresponding keys out altogether.
        Map<String, Object> bookIncluded = new HashMap<>();
        bookIncluded.put("title", "Dune");
        bookIncluded.put("pageCount", 412);
        bookIncluded.put("author", Map.of("name", "Frank Herbert"));
        Map<String, Object> dataIncluded = Map.of("book", bookIncluded);

        Map<String, Object> bookSkipped = new HashMap<>();
        bookSkipped.put("pageCount", 412);
        Map<String, Object> dataSkipped = Map.of("book", bookSkipped);

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.book.GetBookConditionalQuery", true, loader);
            // The operation declares variables, so it has no no-arg constructor: build an
            // instance through its generated builder instead. decode() itself does not depend on
            // the built variable values.
            Object builder = operationClass.getMethod("builder").invoke(null);
            builder = builder.getClass().getMethod("skipTitle", Boolean.class).invoke(builder, true);
            builder = builder.getClass().getMethod("includeAuthor", Boolean.class).invoke(builder, true);
            Object operationInstance = builder.getClass().getMethod("build").invoke(builder);
            Method decode = operationClass.getMethod("decode", Object.class);

            Object decodedIncluded = decode.invoke(operationInstance, dataIncluded);
            Object includedBook = decodedIncluded.getClass().getMethod("book").invoke(decodedIncluded);
            assertThat(includedBook.getClass().getMethod("title").invoke(includedBook)).isEqualTo("Dune");
            Object includedAuthor = includedBook.getClass().getMethod("author").invoke(includedBook);
            assertThat(includedAuthor).isNotNull();
            assertThat(includedAuthor.getClass().getMethod("name").invoke(includedAuthor))
                    .isEqualTo("Frank Herbert");

            Object decodedSkipped = decode.invoke(operationInstance, dataSkipped);
            Object skippedBook = decodedSkipped.getClass().getMethod("book").invoke(decodedSkipped);
            assertThat(skippedBook.getClass().getMethod("title").invoke(skippedBook)).isNull();
            assertThat(skippedBook.getClass().getMethod("author").invoke(skippedBook)).isNull();
            assertThat(skippedBook.getClass().getMethod("pageCount").invoke(skippedBook)).isEqualTo(412);
        }
    }

    @Test
    void decodesNestedListsPreservingContainerAndElementNullability(@TempDir Path tempDir) throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("GetBooks.graphql");
        Files.writeString(schema, Fixtures.BOOK_SCHEMA_WITH_LISTS);
        Files.writeString(operation, Fixtures.GET_BOOKS_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.book");

        Path javaFile = generatedSources.resolve("generated").resolve("book").resolve("GetBooksQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        Map<String, Object> book = new HashMap<>();
        book.put("title", "Dune");
        List<Object> tags = new ArrayList<>();
        tags.add("sci-fi");
        tags.add(null);
        book.put("tags", tags);
        List<Object> ratings = new ArrayList<>();
        ratings.add(List.of(5, 4));
        ratings.add(null);
        book.put("ratings", ratings);
        Map<String, Object> data = Map.of("books", List.of(book));

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.book.GetBooksQuery", true, loader);
            Object operationInstance = operationClass.getDeclaredConstructor().newInstance();

            Object decoded = operationClass.getMethod("decode", Object.class).invoke(operationInstance, data);
            @SuppressWarnings("unchecked")
            List<Object> decodedBooks = (List<Object>) decoded.getClass().getMethod("books").invoke(decoded);
            assertThat(decodedBooks).hasSize(1);
            Object decodedBook = decodedBooks.get(0);

            assertThat(decodedBook.getClass().getMethod("title").invoke(decodedBook)).isEqualTo("Dune");
            assertThat(decodedBook.getClass().getMethod("tags").invoke(decodedBook))
                    .isEqualTo(Arrays.asList("sci-fi", null));
            assertThat(decodedBook.getClass().getMethod("ratings").invoke(decodedBook))
                    .isEqualTo(Arrays.asList(List.of(5, 4), null));
        }
    }

    @Test
    void decodesAKnownEnumValueAndPreservesAnUnrecognizedOneUnderTheLenientPolicy(@TempDir Path tempDir)
            throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("GetBookGenre.graphql");
        Files.writeString(schema, Fixtures.BOOK_SCHEMA_WITH_OUTPUT_ENUM);
        Files.writeString(operation, Fixtures.GET_BOOK_GENRE_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.book");

        Path javaFile = generatedSources.resolve("generated").resolve("book").resolve("GetBookGenreQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        Map<String, Object> knownBook = new HashMap<>();
        knownBook.put("title", "Dune");
        knownBook.put("genre", "FICTION");
        Map<String, Object> knownData = Map.of("book", knownBook);

        // 'MYSTERY' is not one of the schema's known 'Genre' values at generation time: an
        // additive server change this client must still decode usefully under the lenient
        // policy, and reject clearly under the strict one.
        Map<String, Object> unknownBook = new HashMap<>();
        unknownBook.put("title", "Some Future Book");
        unknownBook.put("genre", "MYSTERY");
        Map<String, Object> unknownData = Map.of("book", unknownBook);

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.book.GetBookGenreQuery", true, loader);
            Object operationInstance = operationClass.getDeclaredConstructor().newInstance();
            Method decode = operationClass.getMethod("decode", Object.class, DecodingPolicy.class);

            Object decodedKnown = decode.invoke(operationInstance, knownData, DecodingPolicy.LENIENT);
            Object knownBookRecord = decodedKnown.getClass().getMethod("book").invoke(decodedKnown);
            Object knownGenre = knownBookRecord.getClass().getMethod("genre").invoke(knownBookRecord);
            assertThat(((Enum<?>) knownGenre).name()).isEqualTo("FICTION");

            Object decodedUnknown = decode.invoke(operationInstance, unknownData, DecodingPolicy.LENIENT);
            Object unknownBookRecord = decodedUnknown.getClass().getMethod("book").invoke(decodedUnknown);
            Object unknownGenre = unknownBookRecord.getClass().getMethod("genre").invoke(unknownBookRecord);
            assertThat(unknownGenre.getClass().getSimpleName()).isEqualTo("Unrecognized");
            assertThat(unknownGenre.getClass().getMethod("rawValue").invoke(unknownGenre)).isEqualTo("MYSTERY");

            assertThatThrownBy(() -> decode.invoke(operationInstance, unknownData, DecodingPolicy.STRICT))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Genre")
                    .hasMessageContaining("MYSTERY");
        }
    }

    @Test
    void decodesUnionBranchesAndPreservesAnUnrecognizedConcreteTypeUnderTheLenientPolicy(@TempDir Path tempDir)
            throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("Search.graphql");
        Files.writeString(schema, Fixtures.SEARCH_RESULT_SCHEMA);
        Files.writeString(operation, Fixtures.SEARCH_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.search");

        Path javaFile = generatedSources.resolve("generated").resolve("search").resolve("SearchQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        Map<String, Object> book = new HashMap<>();
        book.put("__typename", "Book");
        book.put("title", "Dune");

        Map<String, Object> movie = new HashMap<>();
        movie.put("__typename", "Movie");
        movie.put("title", "Dune");
        movie.put("director", "Denis Villeneuve");

        // 'TVShow' is not one of 'SearchResult's known member types at generation time.
        Map<String, Object> tvShow = new HashMap<>();
        tvShow.put("__typename", "TVShow");
        tvShow.put("title", "Foundation");

        Map<String, Object> data = Map.of("search", List.of(book, movie, tvShow));

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.search.SearchQuery", true, loader);
            Object operationInstance = operationClass.getDeclaredConstructor().newInstance();
            Method decode = operationClass.getMethod("decode", Object.class, DecodingPolicy.class);

            Object decoded = decode.invoke(operationInstance, data, DecodingPolicy.LENIENT);
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) decoded.getClass().getMethod("search").invoke(decoded);
            assertThat(results).hasSize(3);

            Object bookResult = results.get(0);
            assertThat(bookResult.getClass().getSimpleName()).isEqualTo("SearchResultBook");
            assertThat(bookResult.getClass().getMethod("title").invoke(bookResult)).isEqualTo("Dune");

            Object movieResult = results.get(1);
            assertThat(movieResult.getClass().getSimpleName()).isEqualTo("SearchResultMovie");
            assertThat(movieResult.getClass().getMethod("director").invoke(movieResult)).isEqualTo("Denis Villeneuve");

            // The unrecognized branch still decodes the shared field ('title' is not shared in
            // this fixture, so it only carries the raw type name), rather than failing the whole
            // list.
            Object unrecognizedResult = results.get(2);
            assertThat(unrecognizedResult.getClass().getSimpleName()).isEqualTo("SearchResultUnrecognized");
            assertThat(unrecognizedResult.getClass().getMethod("__typename").invoke(unrecognizedResult))
                    .isEqualTo("TVShow");

            assertThatThrownBy(() -> decode.invoke(operationInstance, data, DecodingPolicy.STRICT))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TVShow");
        }
    }

    @Test
    void decodesInterfaceBranchesPreservingTheSharedFieldOnTheUnrecognizedCase(@TempDir Path tempDir)
            throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Path operation = tempDir.resolve("GetFeaturedMedia.graphql");
        Files.writeString(schema, Fixtures.MEDIA_SCHEMA);
        Files.writeString(operation, Fixtures.GET_FEATURED_MEDIA_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler().generate(List.of(schema), List.of(operation), generatedSources, "generated.media");

        Path javaFile = generatedSources.resolve("generated").resolve("media").resolve("GetFeaturedMediaQuery.java");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(javaFile, classesOutput);

        Map<String, Object> book = new HashMap<>();
        book.put("__typename", "Book");
        book.put("title", "Dune");
        book.put("pageCount", 412);

        Map<String, Object> movie = new HashMap<>();
        movie.put("__typename", "Movie");
        movie.put("title", "Foundation");
        movie.put("director", "David Goyer");

        // 'Podcast' implements 'Media' on the live server, but names no fragment in the operation
        // the (older) schema generated: the interface's shared field ('title') must still decode,
        // alongside the raw type name, rather than being dropped along with the whole result.
        Map<String, Object> podcast = new HashMap<>();
        podcast.put("__typename", "Podcast");
        podcast.put("title", "The Daily");

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> operationClass = Class.forName("generated.media.GetFeaturedMediaQuery", true, loader);
            Object operationInstance = operationClass.getDeclaredConstructor().newInstance();
            Method decode = operationClass.getMethod("decode", Object.class, DecodingPolicy.class);

            Object bookResult = featured(decode, operationInstance, book, DecodingPolicy.LENIENT);
            assertThat(bookResult.getClass().getSimpleName()).isEqualTo("FeaturedBook");
            assertThat(bookResult.getClass().getMethod("title").invoke(bookResult)).isEqualTo("Dune");
            assertThat(bookResult.getClass().getMethod("pageCount").invoke(bookResult)).isEqualTo(412);

            Object movieResult = featured(decode, operationInstance, movie, DecodingPolicy.LENIENT);
            assertThat(movieResult.getClass().getSimpleName()).isEqualTo("FeaturedMovie");
            assertThat(movieResult.getClass().getMethod("director").invoke(movieResult)).isEqualTo("David Goyer");

            Object podcastResult = featured(decode, operationInstance, podcast, DecodingPolicy.LENIENT);
            assertThat(podcastResult.getClass().getSimpleName()).isEqualTo("FeaturedUnrecognized");
            assertThat(podcastResult.getClass().getMethod("title").invoke(podcastResult)).isEqualTo("The Daily");
            assertThat(podcastResult.getClass().getMethod("__typename").invoke(podcastResult)).isEqualTo("Podcast");

            Map<String, Object> data = Map.of("featured", podcast);
            assertThatThrownBy(() -> decode.invoke(operationInstance, data, DecodingPolicy.STRICT))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Podcast");
        }
    }

    private static Object featured(Method decode, Object operationInstance, Map<String, Object> featured, DecodingPolicy policy)
            throws Exception {
        Object data = decode.invoke(operationInstance, Map.of("featured", featured), policy);
        return data.getClass().getMethod("featured").invoke(data);
    }

    private static void compile(Path javaFile, Path classesOutput) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result =
                compiler.run(
                        null,
                        new PrintStream(diagnostics),
                        new PrintStream(diagnostics),
                        "-d",
                        classesOutput.toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        javaFile.toString());
        assertThat(result).as("javac output:%n%s", diagnostics).isZero();
    }
}
