package dev.glisseo.izar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.ResponseError;

class GraphQlResultTest {

    @Test
    void assertNoErrorsReturnsDataWhenThereAreNoErrors() {
        GraphQlResult<String> result = new GraphQlResult<>("GetThing", "decoded", List.of(), Map.of());

        assertThat(result.assertNoErrors()).isEqualTo("decoded");
    }

    @Test
    void assertNoErrorsFailsWhenTheResponseCarriedGraphQlErrors() {
        ResponseError error = mock(ResponseError.class);
        when(error.getMessage()).thenReturn("boom");
        GraphQlResult<String> result = new GraphQlResult<>("GetThing", "partial", List.of(error), Map.of());

        assertThat(result.hasErrors()).isTrue();
        assertThatThrownBy(result::assertNoErrors)
                .isInstanceOf(GraphQlOperationException.class)
                .hasMessageContaining("GetThing")
                .hasMessageContaining("boom");
    }

    @Test
    void assertNoErrorsFailsWhenDataIsMissingEvenWithoutErrors() {
        GraphQlResult<String> result = new GraphQlResult<>("GetThing", null, List.of(), Map.of());

        assertThatThrownBy(result::assertNoErrors)
                .isInstanceOf(GraphQlOperationException.class)
                .hasMessageContaining("GetThing")
                .hasMessageContaining("no data");
    }

    @Test
    void accessorsReturnImmutableCopies() {
        ResponseError error = mock(ResponseError.class);
        List<ResponseError> errors = new java.util.ArrayList<>(List.of(error));
        Map<String, Object> extensions = new java.util.HashMap<>(Map.of("tracing", "on"));

        GraphQlResult<String> result = new GraphQlResult<>("GetThing", "data", errors, extensions);
        errors.add(mock(ResponseError.class));
        extensions.put("more", "stuff");

        assertThat(result.errors()).hasSize(1);
        assertThat(result.extensions()).containsOnly(Map.entry("tracing", "on"));
        assertThatThrownBy(() -> result.errors().add(error)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.extensions().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
