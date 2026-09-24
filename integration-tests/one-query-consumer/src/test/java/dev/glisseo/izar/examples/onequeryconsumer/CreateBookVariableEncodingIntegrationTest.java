package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.CreateBookMutation;
import dev.glisseo.izar.generated.books.CreateBookMutation.AuthorInput;
import dev.glisseo.izar.generated.books.CreateBookMutation.BookInput;
import dev.glisseo.izar.generated.books.CreateBookMutation.Genre;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * The issue 02 acceptance test: a generated mutation builds variables with a nested input object,
 * an enum, and a list of inputs, then executes over real HTTP against {@link
 * BookGraphQlController}. Assertions read {@link BookGraphQlController#lastObservedInput}, the
 * exact map GraphQL Java's own argument coercion handed the resolver, so omission, explicit
 * {@code null}, and schema-default application are distinguished by what the server actually
 * received rather than by re-inspecting the client's own encoding.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class CreateBookVariableEncodingIntegrationTest {

    @Autowired private SynchronousGraphQlOperations operations;
    @Autowired private BookGraphQlController controller;

    @Test
    void omitsUntouchedOptionalPropertiesAndAppliesTheSchemaDefault() {
        BookInput input = BookInput.builder().title("Dune").build();

        operations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();

        assertThat(controller.lastObservedInput).containsEntry("title", "Dune");
        assertThat(controller.lastObservedInput).doesNotContainKey("pageCount");
        assertThat(controller.lastObservedInput).doesNotContainKey("tags");
        // 'genre' was never set, but has a schema default: the server observes it applied.
        assertThat(controller.lastObservedInput).containsEntry("genre", "FICTION");
    }

    @Test
    void distinguishesAnExplicitNullFromOmission() {
        BookInput input = BookInput.builder().title("Dune").pageCount(null).build();

        operations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();

        assertThat(controller.lastObservedInput).containsKey("pageCount");
        assertThat(controller.lastObservedInput.get("pageCount")).isNull();
    }

    @Test
    void sendsAnExplicitEnumValueOverridingTheDefault() {
        BookInput input = BookInput.builder().title("Dune").genre(Genre.NONFICTION).build();

        operations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();

        assertThat(controller.lastObservedInput).containsEntry("genre", "NONFICTION");
    }

    @Test
    void sendsAListOfInputsPreservingANullElement() {
        List<String> tags = Arrays.asList("sci-fi", null);
        BookInput input = BookInput.builder().title("Dune").tags(tags).build();

        operations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();

        @SuppressWarnings("unchecked")
        List<Object> observedTags = (List<Object>) controller.lastObservedInput.get("tags");
        assertThat(observedTags).containsExactly("sci-fi", null);
    }

    @Test
    void sendsANestedInputObjectAndAListOfInputObjects() {
        AuthorInput coAuthor = AuthorInput.builder().name("Frank Herbert").build();
        BookInput input = BookInput.builder().title("Dune").coAuthors(List.of(coAuthor)).build();

        operations.execute(CreateBookMutation.builder().input(input).build()).assertNoErrors();

        @SuppressWarnings("unchecked")
        List<Object> observedCoAuthors = (List<Object>) controller.lastObservedInput.get("coAuthors");
        assertThat(observedCoAuthors).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> observedCoAuthor = (Map<String, Object>) observedCoAuthors.get(0);
        assertThat(observedCoAuthor).containsEntry("name", "Frank Herbert");
    }
}
