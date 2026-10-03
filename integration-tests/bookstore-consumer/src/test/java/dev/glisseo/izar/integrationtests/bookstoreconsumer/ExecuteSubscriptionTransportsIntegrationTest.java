package dev.glisseo.izar.integrationtests.bookstoreconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.glisseo.izar.client.GraphQlResult;
import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.generated.books.WatchBookSubscription;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.rsocket.context.RSocketPortInfoApplicationContextInitializer;
import org.springframework.graphql.client.RSocketGraphQlClient;
import org.springframework.graphql.client.WebSocketGraphQlClient;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** Executes one generated subscription through both application-configured transports. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.graphql.schema.locations=file:src/main/graphql/",
            "spring.graphql.websocket.path=/graphql",
            "spring.graphql.rsocket.mapping=graphql",
            "spring.rsocket.server.port=0"
        })
@ContextConfiguration(initializers = RSocketPortInfoApplicationContextInitializer.class)
class ExecuteSubscriptionTransportsIntegrationTest {

    @LocalServerPort private int httpPort;

    @Value("${local.rsocket.server.port}") private int rsocketPort;

    @Test
    void receivesAndDecodesMultipleGeneratedSubscriptionEventsOverWebSocket() {
        WebSocketGraphQlClient client = WebSocketGraphQlClient.builder(
                        "ws://localhost:" + httpPort + "/graphql", new ReactorNettyWebSocketClient())
                .build();
        try {
            client.start().block(Duration.ofSeconds(10));

            assertBookEvents(ReactiveGraphQlOperations.fullDocument(client)
                    .executeSubscription(new WatchBookSubscription()));
        } finally {
            client.stop().block(Duration.ofSeconds(10));
        }
    }

    @Test
    void receivesAndDecodesMultipleGeneratedSubscriptionEventsOverRSocket() {
        RSocketGraphQlClient client = RSocketGraphQlClient.builder()
                .tcp("localhost", rsocketPort)
                .route("graphql")
                .build();
        try {
            client.start().block(Duration.ofSeconds(10));

            assertBookEvents(ReactiveGraphQlOperations.fullDocument(client)
                    .executeSubscription(new WatchBookSubscription()));
        } finally {
            client.stop().block(Duration.ofSeconds(10));
        }
    }

    private static void assertBookEvents(Flux<GraphQlResult<WatchBookSubscription.Data>> events) {
        StepVerifier.create(events)
                .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                        .isEqualTo(new WatchBookSubscription.BookChanged("Dune", 412)))
                .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                        .isEqualTo(new WatchBookSubscription.BookChanged("Children of Dune", 408)))
                .verifyComplete();
    }
}
