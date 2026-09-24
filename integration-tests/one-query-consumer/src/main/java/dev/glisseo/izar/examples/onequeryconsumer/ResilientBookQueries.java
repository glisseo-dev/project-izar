package dev.glisseo.izar.examples.onequeryconsumer;

import dev.glisseo.izar.client.GraphQlOperationException;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.operation.GraphQlOperation;
import java.time.Duration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Issue 08's application-controlled resilience example: ordinary Spring Retry, not an
 * Izar-specific retry mechanism. Izar's adapters make exactly one attempt per {@code execute}
 * call; whatever repeats a failed operation is the application's own choice, made in its own
 * configuration, the same way it would be for any other Spring HTTP call.
 */
@Component
class ResilientBookQueries {

    private final SynchronousGraphQlOperations operations;
    private final RetryTemplate retryTemplate;

    ResilientBookQueries(SynchronousGraphQlOperations bookServiceOperations) {
        this.operations = bookServiceOperations;
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(Duration.ofMillis(10))
                .retryOn(GraphQlOperationException.class)
                .build();
    }

    /**
     * Retries {@code query} up to 3 times, a fixed 10ms apart, on {@link
     * GraphQlOperationException} (any GraphQL error, including one the server always returns for
     * a broken field).
     *
     * <p>A query has no side effect: at worst, a retried attempt repeats the same read the
     * failed one already tried. That is what makes wrapping it in a blanket retry safe without
     * any further reasoning about what the server did with the first attempt.
     */
    <TResponse> TResponse executeQueryWithRetry(GraphQlOperation<TResponse> query) {
        return retryTemplate.execute(context -> operations.execute(query).assertNoErrors());
    }

    /**
     * Executes {@code mutation} exactly once, deliberately without {@link #retryTemplate}.
     *
     * <p>A mutation is not automatically safe to repeat: if the server applies it but the
     * response never reaches the client (a dropped connection, a timeout past the point the
     * server committed), retrying resends the same "create" and risks a second book, not just a
     * second request. Making that retry safe needs the mutation to carry its own idempotency key
     * or the caller to check whether the first attempt already succeeded before trying again;
     * neither is something a generic retry policy can supply on the operation's behalf. Until an
     * operation opts into one of those, the safe default is the one used here: no retry at all.
     */
    CreateBookMutation.Data executeMutationWithoutRetry(CreateBookMutation mutation) {
        return operations.execute(mutation).assertNoErrors();
    }
}
