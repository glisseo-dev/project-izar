package dev.glisseo.izar.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/** A real HTTP endpoint that records requests and returns a configurable GraphQL response. */
final class RecordingHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile String responseBody = "{\"data\":{\"thing\":\"ok\"}}";
    private volatile String responseContentType = "application/json";
    private volatile int responseStatus = 200;
    private volatile CountDownLatch releaseSseResponse;

    RecordingHttpServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/graphql";
    }

    List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    void respondWith(String jsonBody) {
        responseBody = jsonBody;
        responseContentType = "application/json";
        responseStatus = 200;
    }

    void respondWithGraphQlError(String jsonBody) {
        responseBody = jsonBody;
        responseContentType = "application/graphql-response+json";
        responseStatus = 400;
    }

    void respondWithSse(String... jsonEvents) {
        StringBuilder response = new StringBuilder();
        for (String jsonEvent : jsonEvents) {
            response.append("event: next\ndata: ").append(jsonEvent).append("\n\n");
        }
        response.append("event: complete\n\n");
        responseBody = response.toString();
        responseContentType = "text/event-stream";
        responseStatus = 200;
    }

    void respondWithSseAndKeepOpen(String... jsonEvents) {
        respondWithSse(jsonEvents);
        releaseSseResponse = new CountDownLatch(1);
    }

    void respondWithSseError(String jsonError) {
        responseBody = "event: error\ndata: " + jsonError + "\n\n";
        responseContentType = "text/event-stream";
        responseStatus = 200;
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody;
        try (InputStream in = exchange.getRequestBody()) {
            requestBody = in.readAllBytes();
        }
        requests.add(new RecordedRequest(
                exchange.getRequestURI().getPath(),
                new LinkedHashMap<>(exchange.getRequestHeaders()),
                new String(requestBody, StandardCharsets.UTF_8)));

        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", responseContentType);
        CountDownLatch release = releaseSseResponse;
        exchange.sendResponseHeaders(responseStatus, release == null ? responseBytes.length : 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(responseBytes);
            out.flush();
            if (release != null) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    public void close() {
        CountDownLatch release = releaseSseResponse;
        if (release != null) {
            release.countDown();
        }
        server.stop(0);
    }

    record RecordedRequest(String path, Map<String, List<String>> headers, String body) {}
}
