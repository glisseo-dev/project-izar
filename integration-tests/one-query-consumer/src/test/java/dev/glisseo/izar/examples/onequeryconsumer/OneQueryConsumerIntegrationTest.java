package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * The issue 01 acceptance test: a Maven build generates {@code GetBookQuery}, compiles it,
 * executes it through {@link dev.glisseo.izar.client.SynchronousGraphQlOperations} over real HTTP
 * against {@link BookGraphQlController}, and maps the result with MapStruct — through
 * {@link BookService}'s public interface only.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            // Reuses the same schema the plugin generated against: client and server are
            // proven to agree, not just each independently plausible.
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class OneQueryConsumerIntegrationTest {

    @Autowired private BookService bookService;

    @Test
    void fetchesAndMapsTheBookThroughARealSpringGraphQlServer() {
        BookSummary summary = bookService.fetchBook();

        assertThat(summary).isEqualTo(new BookSummary("Dune", 412, "Frank Herbert"));
    }
}
