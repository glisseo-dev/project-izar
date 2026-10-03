package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.GetBookScalarsQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * {@code izar-maven-plugin} is configured with a {@code
 * <scalarMapping>} for each of the schema's three custom scalars, pairing them with {@code
 * izar-scalars}' codecs. The generated fields decode to plain {@code java.time.Instant}, {@code
 * java.time.LocalDate}, and {@code java.math.BigDecimal} values over real HTTP against {@link
 * BookGraphQlController}, whose {@code book} resolver returns {@code OffsetDateTime}, {@code
 * LocalDate}, and {@code BigDecimal} values through genuinely registered {@code
 * graphql.scalars.datetime.DateTimeScalar}, {@code DateScalar}, and {@code
 * JavaPrimitives.GraphQLBigDecimal} instances registered by {@link ScalarWiringConfiguration},
 * rather than decoding a synthetic response map.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class CustomScalarRoundTripIntegrationTest {

    @Autowired private SynchronousGraphQlOperations operations;

    @Test
    void decodesEveryCustomScalarFieldThroughARealExtendedScalarsServer() {
        GetBookScalarsQuery.Data data = operations.execute(new GetBookScalarsQuery()).assertNoErrors();

        assertThat(data.book().publishedAt()).isEqualTo(BookGraphQlController.PUBLISHED_AT.toInstant());
        assertThat(data.book().releaseDate()).isEqualTo(LocalDate.of(1965, 8, 1));
        assertThat(data.book().price()).isEqualByComparingTo(new BigDecimal("19.99"));
    }
}
