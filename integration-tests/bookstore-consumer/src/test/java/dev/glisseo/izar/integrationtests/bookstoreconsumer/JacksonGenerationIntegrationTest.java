package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.integrationtests.bookstoreconsumer.jackson.GetBookGenreAndSearchResultsQuery;
import dev.glisseo.izar.integrationtests.bookstoreconsumer.jackson.GetBookGenreAndSearchResultsQuery.SearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.graphql.client.GraphQlClient;

/** Exercises Jackson-mode generated models through Spring GraphQL's real HTTP client. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class JacksonGenerationIntegrationTest {

    @Autowired
    @Qualifier("bookServiceGraphQlClient")
    private GraphQlClient graphQlClient;

    @Test
    void mapsEnumsAndPolymorphicUnionMembersWithToEntity() {
        GetBookGenreAndSearchResultsQuery operation = new GetBookGenreAndSearchResultsQuery();

        GetBookGenreAndSearchResultsQuery.Book book = graphQlClient.document(operation.document())
                .retrieve(GetBookGenreAndSearchResultsQuery.BOOK)
                .toEntity(GetBookGenreAndSearchResultsQuery.Book.class)
                .block();
        List<SearchResult> results = graphQlClient.document(operation.document())
                .retrieve(GetBookGenreAndSearchResultsQuery.SEARCH)
                .toEntityList(SearchResult.class)
                .block();

        assertThat(book).isNotNull();
        assertThat(book.genre()).isEqualTo("FICTION");
        assertThat(results)
                .extracting(Object::getClass)
                .containsExactly(
                        GetBookGenreAndSearchResultsQuery.SearchResultBook.class,
                        GetBookGenreAndSearchResultsQuery.SearchResultMovie.class);
    }
}
