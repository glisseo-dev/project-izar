package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.client.GraphQlOperationException;
import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.generated.books.CreateBookMutation.BookInput;
import dev.glisseo.izar.generated.books.GetBrokenBookQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * The issue 08 resilience acceptance test: {@link ResilientBookQueries} retries a failing query
 * the configured number of times, evidenced by the real server's own call count, and executes a
 * mutation exactly once regardless.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class ApplicationControlledResilienceIntegrationTest {

    @Autowired private ResilientBookQueries resilientBookQueries;
    @Autowired private BookGraphQlController controller;

    @Test
    void retriesAFailingQueryUpToTheConfiguredAttemptsThenFails() {
        assertThatThrownBy(() -> resilientBookQueries.executeQueryWithRetry(new GetBrokenBookQuery()))
                .isInstanceOf(GraphQlOperationException.class);

        // RetryTemplate.builder().maxAttempts(3): the failing operation ran three times, driven
        // entirely by the application's own retry policy. Izar's SynchronousGraphQlOperations
        // makes exactly one attempt per execute() call; nothing inside it repeats a request.
        assertThat(controller.brokenBookCallCount).isEqualTo(3);
    }

    @Test
    void executesAMutationExactlyOnceWithNoRetry() {
        BookInput input = BookInput.builder().title("Dune").pageCount(412).build();

        CreateBookMutation.Data data = resilientBookQueries.executeMutationWithoutRetry(
                CreateBookMutation.builder().input(input).build());

        assertThat(data).isNotNull();
        assertThat(controller.lastObservedInput).containsEntry("title", "Dune");
    }
}
