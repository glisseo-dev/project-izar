package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.GetBooksWithFragmentAliasAndConditionalQuery;
import dev.glisseo.izar.generated.books.GetBooksWithFragmentAliasAndConditionalQuery.AllBooks;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * The issue 03 acceptance test: a Maven-generated operation aliases its root selection ({@code
 * allBooks: books}), merges a named fragment ({@code ...BookCoreFields}, authored in its own
 * file) with the operation's own direct selections, decodes a non-null list of non-null elements,
 * and carries a conditionally selected field ({@code author @include}) whose presence is a
 * runtime fact. Both outcomes of that condition are exercised over real HTTP against {@link
 * BookGraphQlController}, and every assertion reads through generated accessors only.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class GetBooksWithFragmentAliasAndConditionalIntegrationTest {

    @Autowired private SynchronousGraphQlOperations operations;

    @Test
    void decodesTheAliasedListWithTheIncludedFieldPresent() {
        GetBooksWithFragmentAliasAndConditionalQuery operation =
                GetBooksWithFragmentAliasAndConditionalQuery.builder().includeAuthor(true).build();

        List<AllBooks> allBooks = operations.execute(operation).assertNoErrors().allBooks();

        assertThat(allBooks).hasSize(2);
        AllBooks dune = allBooks.get(0);
        assertThat(dune.title()).isEqualTo("Dune");
        assertThat(dune.pageCount()).isEqualTo(412);
        assertThat(dune.tags()).containsExactly("sci-fi", "classic");
        assertThat(dune.author()).isNotNull();
        assertThat(dune.author().name()).isEqualTo("Frank Herbert");

        AllBooks childrenOfDune = allBooks.get(1);
        assertThat(childrenOfDune.title()).isEqualTo("Children of Dune");
        assertThat(childrenOfDune.tags()).isNull();
        // This element has no author regardless of the condition: a genuinely absent field, not
        // a conditionally skipped one.
        assertThat(childrenOfDune.author()).isNull();
    }

    @Test
    void decodesTheConditionallySelectedFieldAsAbsentWhenNotIncluded() {
        GetBooksWithFragmentAliasAndConditionalQuery operation =
                GetBooksWithFragmentAliasAndConditionalQuery.builder().includeAuthor(false).build();

        List<AllBooks> allBooks = operations.execute(operation).assertNoErrors().allBooks();

        assertThat(allBooks).hasSize(2);
        // 'author' is excluded from the request entirely this time: every element decodes it as
        // absent, including Dune, which has one when the condition is true.
        assertThat(allBooks.get(0).author()).isNull();
        assertThat(allBooks.get(0).title()).isEqualTo("Dune");
    }
}
