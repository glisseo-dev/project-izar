package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.generated.books.WatchBookSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Executes a generated hash-only subscription over HTTP SSE against a plain Spring GraphQL server
 * that resolves persisted IDs through GraphQL Java's {@code PreparsedDocumentProvider}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.graphql.schema.locations=file:src/main/graphql/")
@Import(PersistedDocumentsConfiguration.class)
class ExecutePersistedSubscriptionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PersistedDocumentsConfiguration.RecordingProvider provider;

    @Test
    void receivesAndDecodesMultipleGeneratedPersistedSubscriptionEvents() {
        ReactiveGraphQlOperations operations = ReactiveGraphQlOperations.persistedQuery(
                WebClient.builder().baseUrl("http://localhost:" + port + "/graphql").build());

        // The SSE decoder tries to parse the server's data-less terminal event as a JSON map.
        StepVerifier.create(operations.executeSubscription(new WatchBookSubscription()).take(2))
                .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                        .isEqualTo(new WatchBookSubscription.BookChanged("Dune", 412)))
                .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                        .isEqualTo(new WatchBookSubscription.BookChanged("Children of Dune", 408)))
                .verifyComplete();

        assertThat(provider.receivedQueries()).containsOnly(graphql.ExecutionInput.PERSISTED_QUERY_MARKER);
    }
}
