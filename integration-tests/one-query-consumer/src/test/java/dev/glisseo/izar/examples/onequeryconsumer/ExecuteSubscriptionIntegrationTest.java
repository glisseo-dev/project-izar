package dev.glisseo.izar.examples.onequeryconsumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.glisseo.izar.client.ReactiveGraphQlOperations;
import dev.glisseo.izar.generated.books.WatchBookSubscription;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

/** Issue 133's end-to-end proof for generated full-document subscriptions over HTTP SSE. */
class ExecuteSubscriptionIntegrationTest {

    @Test
    void receivesAndDecodesMultipleGeneratedSubscriptionEvents() {
        try (SseServer server = new SseServer()) {
            ReactiveGraphQlOperations operations = ReactiveGraphQlOperations.fullDocument(
                    HttpGraphQlClient.builder(WebClient.builder().baseUrl(server.url())).build());

            StepVerifier.create(operations.executeSubscription(new WatchBookSubscription()))
                    .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                            .isEqualTo(new WatchBookSubscription.BookChanged("Dune", 412)))
                    .assertNext(result -> assertThat(result.assertNoErrors().bookChanged())
                            .isEqualTo(new WatchBookSubscription.BookChanged("Children of Dune", 408)))
                    .verifyComplete();
        }
    }

    private static final class SseServer implements AutoCloseable {

        private final HttpServer server;

        private SseServer() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/graphql", this::handle);
                server.start();
            } catch (IOException e) {
                throw new IllegalStateException("Could not start the SSE fixture.", e);
            }
        }

        private String url() {
            return "http://localhost:" + server.getAddress().getPort() + "/graphql";
        }

        private void handle(HttpExchange exchange) throws IOException {
            exchange.getRequestBody().close();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                writeEvent(output, "{\"data\":{\"bookChanged\":{\"title\":\"Dune\",\"pageCount\":412}}}");
                writeEvent(
                        output,
                        "{\"data\":{\"bookChanged\":{\"title\":\"Children of Dune\",\"pageCount\":408}}}");
            }
        }

        private static void writeEvent(OutputStream output, String data) throws IOException {
            output.write(("event: next\ndata: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
