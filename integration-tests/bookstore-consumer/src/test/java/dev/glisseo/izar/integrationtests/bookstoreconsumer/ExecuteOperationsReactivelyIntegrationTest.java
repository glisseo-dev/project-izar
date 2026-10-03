package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.client.GraphQlOperationException;
import dev.glisseo.izar.client.GraphQlResult;
import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.generated.books.CreateBookMutation.BookInput;
import dev.glisseo.izar.generated.books.GetBookQuery;
import dev.glisseo.izar.generated.books.GetBrokenBookQuery;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The same generated {@code GetBookQuery} and {@code
 * CreateBookMutation}, executed against the same real {@link BookGraphQlController}, produce
 * identical server-observed requests and decoded results whether the caller executes through
 * {@link SynchronousGraphQlOperations} or {@link ReactiveGraphQlOperations}. Both adapters share
 * generated models, operation metadata, and encoding/decoding behavior rather than each
 * maintaining its own.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class ExecuteOperationsReactivelyIntegrationTest {

    @Autowired private SynchronousGraphQlOperations syncOperations;
    @Autowired private ReactiveGraphQlOperations reactiveOperations;
    @Autowired private BookGraphQlController controller;

    @Test
    void executesAndDecodesTheSameQueryAsTheSynchronousAdapter() {
        GetBookQuery.Data syncData = syncOperations.execute(new GetBookQuery()).assertNoErrors();

        Mono<GraphQlResult<GetBookQuery.Data>> reactiveResult =
                reactiveOperations.execute(new GetBookQuery());

        StepVerifier.create(reactiveResult)
                .assertNext(result -> assertThat(result.assertNoErrors()).isEqualTo(syncData))
                .verifyComplete();
    }

    @Test
    void sendsTheSameServerObservedVariablesAsTheSynchronousAdapter() {
        BookInput input = BookInput.builder().title("Dune").pageCount(null).build();

        syncOperations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();
        // Map.copyOf/Map.of reject null values, and 'pageCount' is explicitly null here, so a
        // plain defensive copy is used instead.
        Map<String, Object> syncObservedInput = new HashMap<>(controller.lastObservedInput);

        StepVerifier.create(reactiveOperations.execute(CreateBookMutation.builder().input(input).build()))
                .assertNext(GraphQlResult::assertNoErrors)
                .verifyComplete();

        // Same builder-produced input, sent through each adapter in turn: the server sees an
        // identical coerced argument map either way, proving neither adapter encodes variables
        // differently from the other.
        assertThat(controller.lastObservedInput).isEqualTo(syncObservedInput);
        assertThat(controller.lastObservedInput).containsEntry("title", "Dune");
        assertThat(controller.lastObservedInput).containsKey("pageCount");
        assertThat(controller.lastObservedInput.get("pageCount")).isNull();
    }

    @Test
    void reportsAServerSideFailureAsAResultRatherThanAnErrorSignal() {
        StepVerifier.create(reactiveOperations.execute(new GetBrokenBookQuery()))
                .assertNext(result -> {
                    assertThat(result.hasErrors()).isTrue();
                    assertThat(result.data()).isNull();
                    assertThatThrownBy(result::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
                })
                .verifyComplete();
    }

    @Test
    void aCallerComposedTimeoutTerminatesAnAbandonedOperation() {
        StepVerifier.create(reactiveOperations.execute(new GetBookQuery()).timeout(Duration.ofNanos(1)))
                .verifyError(java.util.concurrent.TimeoutException.class);
    }
}
