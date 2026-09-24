package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.glisseo.izar.client.GraphQlOperationException;
import dev.glisseo.izar.client.GraphQlResult;
import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.GetBrokenBookQuery;
import dev.glisseo.izar.generated.books.GetPartialBookQuery;
import dev.glisseo.izar.operation.DecodingPolicy;
import dev.glisseo.izar.operation.GraphQlOperation;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * The issue 04 acceptance test: real GraphQL execution against {@link BookGraphQlController}
 * proves partial data with nullable-parent propagation, a whole-data-null execution result, and a
 * request-level failure, not just synthetic responses assembled by hand. {@link
 * dev.glisseo.izar.client.SynchronousGraphQlOperationsTest} covers the remaining synthetic cases
 * (decoding failures, transport failures, and the accessor-level contract) that do not need a
 * real server to demonstrate.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class PartialResultsAndFailuresIntegrationTest {

    @Autowired private SynchronousGraphQlOperations operations;

    @Test
    void decodesPartialDataAndReportsTheFieldNulledByPropagation() {
        GraphQlResult<GetPartialBookQuery.Data> result = operations.execute(new GetPartialBookQuery());

        assertThat(result.hasErrors()).isTrue();
        GetPartialBookQuery.Data data = result.data();
        assertThat(data).isNotNull();
        // 'author' is nullable; graphql-java nulled it because its non-null 'name' resolved to
        // null, rather than failing the whole 'partialBook' object.
        assertThat(data.partialBook().title()).isEqualTo("Dune");
        assertThat(data.partialBook().pageCount()).isEqualTo(412);
        assertThat(data.partialBook().author()).isNull();
        assertThat(data.partialBook().tags()).containsExactly("sci-fi", "classic");

        assertThatThrownBy(result::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
    }

    @Test
    void representsAWholeDataNullResultWithoutFabricatingData() {
        GraphQlResult<GetBrokenBookQuery.Data> result = operations.execute(new GetBrokenBookQuery());

        assertThat(result.data()).isNull();
        assertThat(result.hasErrors()).isTrue();
        assertThatThrownBy(result::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
    }

    /**
     * A request-level failure: the server rejects the request during validation, before any field
     * resolves, so the response has no {@code data} key at all rather than a {@code data} value
     * nulled by execution-time propagation (as in {@code brokenBook} above). An operation name
     * absent from its own document cannot come from generated code (the compiler always pairs the
     * two correctly); this operation wrapper misrepresents its name on purpose to reach that
     * server-side rejection over real HTTP.
     */
    @Test
    void representsARequestLevelFailureWithNoUsableData() {
        GraphQlOperation<GetPartialBookQuery.Data> operationWithUnknownName = unknownOperationNameFor(new GetPartialBookQuery());

        GraphQlResult<GetPartialBookQuery.Data> result = operations.execute(operationWithUnknownName);

        assertThat(result.data()).isNull();
        assertThat(result.hasErrors()).isTrue();
        assertThatThrownBy(result::assertNoErrors).isInstanceOf(GraphQlOperationException.class);
    }

    private static <TResponse> GraphQlOperation<TResponse> unknownOperationNameFor(GraphQlOperation<TResponse> operation) {
        return new GraphQlOperation<>() {
            @Override
            public String operationName() {
                return "NotDefinedInTheDocument";
            }

            @Override
            public String document() {
                return operation.document();
            }

            @Override
            public String operationId() {
                return operation.operationId();
            }

            @Override
            public Map<String, Object> variables() {
                return operation.variables();
            }

            @Override
            public TResponse decode(@Nullable Object data, DecodingPolicy policy) {
                return operation.decode(data, policy);
            }
        };
    }
}
