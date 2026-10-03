package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.client.GraphQlDecodingException;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.Genre;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.SearchResult;
import dev.glisseo.izar.generated.books.GetBookGenreAndSearchResultsQuery.SearchResultBook;
import dev.glisseo.izar.operation.DecodingPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * {@link GetBookGenreAndSearchResultsQuery} is generated
 * once, from the schema in {@code src/main/graphql/}, but executed here against a live server
 * whose schema has since evolved (an extra {@code Genre} value, an extra {@code SearchResult}
 * member), with no client regeneration. This test uses its own minimal Boot application ({@link
 * EvolvedTestApplication}) rather than the example's usual one: it imports only {@link
 * GraphQlClientConfiguration} plus its own {@link SearchResolvers}, without component-scanning the
 * shared test package, so {@link BookGraphQlController}'s resolvers for the same schema
 * coordinates (also {@code Query.book} and {@code Book.genre}) never enter this context.
 */
@SpringBootTest(
        classes = EvolvedSchemaDecodingIntegrationTest.EvolvedTestApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/test/resources/graphql-evolved/"
        })
class EvolvedSchemaDecodingIntegrationTest {

    @Autowired private SynchronousGraphQlOperations lenientOperations;
    @Autowired @Qualifier("bookServiceGraphQlClient") private GraphQlClient graphQlClient;

    @Test
    void preservesTheUnrecognizedEnumValueAndConcreteTypeUnderTheLenientPolicy() {
        GetBookGenreAndSearchResultsQuery.Data data =
                lenientOperations.execute(new GetBookGenreAndSearchResultsQuery()).assertNoErrors();

        assertThat(data.book().genre()).isInstanceOf(Genre.Unrecognized.class);
        assertThat(((Genre.Unrecognized) data.book().genre()).rawValue()).isEqualTo("MYSTERY");

        List<SearchResult> results = data.search();
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isInstanceOf(SearchResultBook.class);
        // 'TVShow' names no fragment in the operation the older schema generated: the second
        // result still decodes, as the sealed interface's Unrecognized branch, rather than
        // failing the whole list.
        assertThat(results.get(1).getClass().getSimpleName()).isEqualTo("SearchResultUnrecognized");
    }

    @Test
    void rejectsTheUnrecognizedEnumValueUnderTheStrictPolicy() {
        SynchronousGraphQlOperations strictOperations =
                SynchronousGraphQlOperations.fullDocument(graphQlClient, DecodingPolicy.STRICT);

        assertThatThrownBy(() -> strictOperations.execute(new GetBookGenreAndSearchResultsQuery()))
                .isInstanceOf(GraphQlDecodingException.class)
                .hasMessageContaining("GetBookGenreAndSearchResults")
                .cause()
                .hasMessageContaining("Genre")
                .hasMessageContaining("MYSTERY");
    }

    @Configuration
    @EnableAutoConfiguration
    @Import(GraphQlClientConfiguration.class)
    static class EvolvedTestApplication {
        @Bean
        SearchResolvers searchResolvers() {
            return new SearchResolvers();
        }
    }

    @Controller
    static class SearchResolvers {

        @QueryMapping
        Book book() {
            return new Book("Dune");
        }

        @SchemaMapping(typeName = "Book", field = "genre")
        String genre() {
            return "MYSTERY";
        }

        @QueryMapping
        List<Object> search() {
            return List.of(new Book("Dune"), new TVShow("Foundation"));
        }

        record Book(String title) {}

        record TVShow(String title) {}
    }
}
