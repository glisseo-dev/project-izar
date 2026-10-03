package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.generated.books.GetBookQuery;
import graphql.ExecutionInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/**
 * Executes generated queries and mutations by persisted ID, synchronously over {@link RestClient}
 * and reactively over {@link WebClient}, against a plain Spring GraphQL server that resolves IDs
 * through GraphQL Java's {@code PreparsedDocumentProvider}. The provider records the query text of
 * each request, so the tests prove that the client sends the ID and never the document.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.graphql.schema.locations=file:src/main/graphql/")
@Import(PersistedDocumentsConfiguration.class)
class ExecutePersistedOperationsIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PersistedDocumentsConfiguration.RecordingProvider provider;

    @Autowired
    private BookGraphQlController controller;

    @Test
    void executesAQueryByPersistedIdSynchronously() {
        SynchronousGraphQlOperations operations =
                SynchronousGraphQlOperations.persistedQuery(RestClient.builder().baseUrl(endpoint()).build());

        GetBookQuery.Data data = operations.execute(new GetBookQuery()).assertNoErrors();

        assertThat(data.book().title()).isEqualTo("Dune");
        assertThat(data.book().pageCount()).isEqualTo(412);
        assertThat(provider.receivedQueries()).containsOnly(ExecutionInput.PERSISTED_QUERY_MARKER);
    }

    @Test
    void executesAQueryByPersistedIdReactively() {
        ReactiveGraphQlOperations operations =
                ReactiveGraphQlOperations.persistedQuery(WebClient.builder().baseUrl(endpoint()).build());

        StepVerifier.create(operations.execute(new GetBookQuery()))
                .assertNext(result -> assertThat(result.assertNoErrors().book().title()).isEqualTo("Dune"))
                .verifyComplete();

        assertThat(provider.receivedQueries()).containsOnly(ExecutionInput.PERSISTED_QUERY_MARKER);
    }

    @Test
    void executesAMutationByPersistedIdWithEncodedVariables() {
        SynchronousGraphQlOperations operations =
                SynchronousGraphQlOperations.persistedQuery(RestClient.builder().baseUrl(endpoint()).build());
        var input = CreateBookMutation.BookInput.builder().title("x").pageCount(7).build();

        operations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();

        assertThat(controller.lastObservedInput).containsEntry("title", "x").containsEntry("pageCount", 7);
        assertThat(provider.receivedQueries()).containsOnly(ExecutionInput.PERSISTED_QUERY_MARKER);
    }

    private String endpoint() {
        return "http://localhost:" + port + "/graphql";
    }
}
