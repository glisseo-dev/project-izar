package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import graphql.schema.DataFetchingEnvironment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

/**
 * A small in-process Spring GraphQL server that the example's generated {@code GetBookQuery} client
 * calls over HTTP. It knows nothing about Izar or the generated client types.
 *
 * <p>{@code createBook} reads its argument through {@link DataFetchingEnvironment} rather than a
 * typed {@code @Argument} parameter, and records the exact coerced map it received: GraphQL Java
 * applies input coercion (including defaults) before a resolver runs. This map shows whether the
 * client omitted a key, sent it explicitly as {@code null}, or supplied a value, and whether the
 * schema applied a default.
 */
@Controller
class BookGraphQlController {

    @Nullable Map<String, Object> lastObservedInput;

    /** The coerced {@code CategoryInput} map last received by {@code categorize}. */
    @Nullable Map<String, Object> lastObservedCategory;

    /** The coerced {@code NodeAInput} map last received by {@code linkNodes}. */
    @Nullable Map<String, Object> lastObservedNodeA;

    @Nullable Map<String, Object> lastObservedNodeB;

    /**
     * The wire values {@code izar-scalars}' codecs must decode back to exactly these: proves the
     * round trip through real, registered {@code graphql.scalars.datetime.DateTimeScalar},
     * {@code DateScalar}, and {@code JavaPrimitives.GraphQLBigDecimal} instances, not a synthetic
     * response map.
     */
    static final OffsetDateTime PUBLISHED_AT = OffsetDateTime.parse("1965-08-01T00:00:00Z");

    static final LocalDate RELEASE_DATE = LocalDate.of(1965, 8, 1);

    static final BigDecimal PRICE = new BigDecimal("19.99");

    @QueryMapping
    Book book() {
        return new Book(
                "Dune",
                412,
                new Author("Frank Herbert"),
                List.of("sci-fi", "classic"),
                PUBLISHED_AT,
                RELEASE_DATE,
                PRICE);
    }

    /**
     * Two books, one with an author and one without: proves the generated list decoder handles
     * more than one element, and that {@code author} being genuinely absent from one element
     * decodes independently of the other's presence.
     */
    @QueryMapping
    List<Book> books() {
        return List.of(
                new Book(
                        "Dune",
                        412,
                        new Author("Frank Herbert"),
                        List.of("sci-fi", "classic"),
                        PUBLISHED_AT,
                        RELEASE_DATE,
                        PRICE),
                new Book("Children of Dune", 408, null, null, null, null, null));
    }

    /**
     * A nullable {@code author} whose non-null {@code name} is missing: graphql-java's own
     * non-null completion nulls {@code author} and records an error, rather than either field
     * decoding it decides on its own. Proves partial data and nullable-parent propagation over a
     * real GraphQL execution, not a synthetic response.
     */
    @QueryMapping
    Book partialBook() {
        return new Book("Dune", 412, new Author(null), List.of("sci-fi", "classic"), null, null, null);
    }

    /**
     * A non-null root field whose resolver fails: with no nullable ancestor to absorb it, the
     * error nulls the entire response's {@code data}, proving a missing result is represented
     * explicitly rather than a fabricated one.
     */
    @QueryMapping
    Book brokenBook() {
        throw new IllegalStateException("Simulated failure fetching the book.");
    }

    @SuppressWarnings("unchecked")
    @MutationMapping
    Book createBook(DataFetchingEnvironment environment) {
        Map<String, Object> input = environment.getArgument("input");
        lastObservedInput = input;
        return new Book(
                (String) input.get("title"), (Integer) input.get("pageCount"), null, null, null, null, null);
    }

    /**
     * A directly self-referential input: {@code environment.getArgument} returns 'children' as a
     * list of the same coerced-map shape as the top-level input, proving the recursive input type
     * round-trips through real GraphQL Java argument coercion, not just the client's own encoding.
     */
    @SuppressWarnings("unchecked")
    @MutationMapping
    boolean categorize(DataFetchingEnvironment environment) {
        lastObservedCategory = environment.getArgument("input");
        return true;
    }

    /**
     * A mutually recursive pair: each argument is coerced independently, and each one's own
     * 'partner' key (when present) is itself a coerced map of the other input type.
     */
    @SuppressWarnings("unchecked")
    @MutationMapping
    boolean linkNodes(DataFetchingEnvironment environment) {
        lastObservedNodeA = environment.getArgument("a");
        lastObservedNodeB = environment.getArgument("b");
        return true;
    }

    /**
     * A plain method rather than a {@link Book} record component: graphql-java coerces this raw
     * string against the schema's declared 'Genre' values the same way it already coerces {@code
     * createBook}'s enum input, so no server-side Java enum type is needed here either.
     */
    @SchemaMapping(typeName = "Book", field = "genre")
    String genre() {
        return "FICTION";
    }

    /**
     * A union field mixing two otherwise-unrelated concrete types: Spring GraphQL's default type
     * resolver matches each returned object to a GraphQL type by its Java simple class name
     * ({@link Book}, {@link Movie}), so no explicit type resolver is needed here.
     */
    @QueryMapping
    List<Object> search() {
        return List.of(
                new Book(
                        "Dune", 412, new Author("Frank Herbert"), List.of("sci-fi", "classic"), null, null, null),
                new Movie("Dune", "Denis Villeneuve"));
    }

    /** Returns the same two generated subscription events to each Spring transport. */
    @SubscriptionMapping
    Flux<Book> bookChanged() {
        return Flux.just(
                new Book("Dune", 412, null, null, null, null, null),
                new Book("Children of Dune", 408, null, null, null, null, null));
    }

    record Book(
            String title,
            Integer pageCount,
            Author author,
            List<String> tags,
            @Nullable OffsetDateTime publishedAt,
            @Nullable LocalDate releaseDate,
            @Nullable BigDecimal price) {}

    record Author(String name) {}

    record Movie(String title, String director) {}
}
