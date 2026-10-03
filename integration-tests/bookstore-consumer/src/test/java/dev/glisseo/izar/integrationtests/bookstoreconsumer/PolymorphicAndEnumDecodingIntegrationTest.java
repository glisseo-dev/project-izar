package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.Genre;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.SearchResult;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.SearchResultBook;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.SearchResultMovie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * A Maven-generated operation selects an
 * output enum ({@code Book.genre}) and a union ({@code search: [SearchResult!]!}) with no {@code
 * __typename} of its own, over real HTTP against {@link BookGraphQlController}. The compiler's
 * injected discriminator has to survive a real server round trip, not just look right in generated
 * source text (that is covered at the compiler level by {@code OperationCompilerTest}).
 *
 * <p>{@link EvolvedSchemaDecodingIntegrationTest} covers the same operation's unfamiliar-value
 * path: the same client, generated from this schema, executed against a live server whose schema
 * has since evolved.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class PolymorphicAndEnumDecodingIntegrationTest {

    @Autowired private SynchronousGraphQlOperations operations;

    @Test
    void decodesAKnownEnumValueAndDispatchesEachUnionBranchByItsInjectedDiscriminator() {
        GetBookGenreAndSearchResultsQuery.Data data =
                operations.execute(new GetBookGenreAndSearchResultsQuery()).assertNoErrors();

        assertThat(data.book().genre()).isEqualTo(Genre.Known.FICTION);

        List<SearchResult> results = data.search();
        assertThat(results).hasSize(2);

        assertThat(results.get(0)).isInstanceOf(SearchResultBook.class);
        assertThat(((SearchResultBook) results.get(0)).title()).isEqualTo("Dune");

        assertThat(results.get(1)).isInstanceOf(SearchResultMovie.class);
        SearchResultMovie movie = (SearchResultMovie) results.get(1);
        assertThat(movie.title()).isEqualTo("Dune");
        assertThat(movie.director()).isEqualTo("Denis Villeneuve");
    }
}
