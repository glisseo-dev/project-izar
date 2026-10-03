package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.SynchronousGraphQlOperations;
import dev.glisseo.izar.generated.books.CategorizeMutation;
import dev.glisseo.izar.generated.books.CategorizeMutation.CategoryInput;
import dev.glisseo.izar.generated.books.LinkNodesMutation;
import dev.glisseo.izar.generated.books.LinkNodesMutation.NodeAInput;
import dev.glisseo.izar.generated.books.LinkNodesMutation.NodeBInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * A self-referential input type and a mutually recursive pair build
 * and execute over real HTTP against {@link BookGraphQlController}, the way {@link
 * CreateBookVariableEncodingIntegrationTest} covers a plain nested input
 * object. Assertions read the exact coerced maps GraphQL Java's own argument coercion handed the
 * resolver, not a re-inspection of the client's own encoding.
 */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "book-service.graphql-endpoint=http://localhost:${local.server.port}/graphql",
            "spring.graphql.schema.locations=file:src/main/graphql/"
        })
class RecursiveInputVariableEncodingIntegrationTest {

    @Autowired private SynchronousGraphQlOperations operations;
    @Autowired private BookGraphQlController controller;

    @Test
    void sendsADirectlySelfReferentialInputTypeAsANestedTreeOfCoercedMaps() {
        CategoryInput leaf = CategoryInput.builder().name("Fiction").build();
        CategoryInput root = CategoryInput.builder().name("Books").children(List.of(leaf)).build();

        operations.execute(CategorizeMutation.builder().input(root).build()).assertNoErrors();

        assertThat(controller.lastObservedCategory).containsEntry("name", "Books");
        @SuppressWarnings("unchecked")
        List<Object> observedChildren = (List<Object>) controller.lastObservedCategory.get("children");
        assertThat(observedChildren).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> observedLeaf = (Map<String, Object>) observedChildren.get(0);
        assertThat(observedLeaf).containsEntry("name", "Fiction");
        assertThat(observedLeaf).doesNotContainKey("children");
    }

    @Test
    void sendsAMutuallyRecursivePairAsEachOthersNestedCoercedMap() {
        NodeBInput nodeB = NodeBInput.builder().label("B").build();
        NodeAInput nodeA = NodeAInput.builder().label("A").partner(nodeB).build();
        NodeBInput standaloneB = NodeBInput.builder().label("B").build();

        operations
                .execute(LinkNodesMutation.builder().a(nodeA).b(standaloneB).build())
                .assertNoErrors();

        assertThat(controller.lastObservedNodeA).containsEntry("label", "A");
        @SuppressWarnings("unchecked")
        Map<String, Object> observedPartner = (Map<String, Object>) controller.lastObservedNodeA.get("partner");
        assertThat(observedPartner).containsEntry("label", "B");
        assertThat(observedPartner).doesNotContainKey("partner");

        assertThat(controller.lastObservedNodeB).containsEntry("label", "B");
        assertThat(controller.lastObservedNodeB).doesNotContainKey("partner");
    }
}
