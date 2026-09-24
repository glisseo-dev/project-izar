package dev.glisseo.izar.examples.nightsky;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Logs HTTP metadata and body content without consuming the response stream. */
final class LoggingExchangeFilterFunction {

    private static final Logger log = LoggerFactory.getLogger(LoggingExchangeFilterFunction.class);
    private static final int MAX_LOGGED_BODY_BYTES = 64 * 1024;

    private LoggingExchangeFilterFunction() {}

    static ExchangeFilterFunction create() {
        return LoggingExchangeFilterFunction::filter;
    }

    private static Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        log.info("--> {} {}", request.method(), request.url());
        ClientRequest requestWithBodyLogging = ClientRequest.from(request)
                .body((outputMessage, context) -> request.body()
                        .insert(new RequestBodyLoggingClientHttpRequest(outputMessage), context))
                .build();

        return next.exchange(requestWithBodyLogging)
                .map(response -> response.mutate()
                        .body(body -> body.doOnNext(data -> logResponseBody(request, data)))
                        .build())
                .doOnNext(response -> log.info("<-- {}", response.statusCode()))
                .doOnError(error -> log.warn("<-- {} {} failed", request.method(), request.url(), error));
    }

    private static void logResponseBody(ClientRequest request, DataBuffer data) {
        String body = data.toString(data.readPosition(), data.readableByteCount(), StandardCharsets.UTF_8);
        log.info("<-- body chunk {} {}\n{}", request.method(), request.url(), body);
    }

    private static final class RequestBodyLoggingClientHttpRequest extends ClientHttpRequestDecorator {

        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private boolean truncated;

        private RequestBodyLoggingClientHttpRequest(ClientHttpRequest delegate) {
            super(delegate);
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body)
                    .doOnNext(this::capture)
                    .doOnComplete(this::logBody));
        }

        private void capture(DataBuffer data) {
            synchronized (body) {
                int remaining = MAX_LOGGED_BODY_BYTES - body.size();
                int length = Math.min(remaining, data.readableByteCount());
                if (length > 0) {
                    byte[] bytes = new byte[length];
                    data.toByteBuffer(data.readPosition(), java.nio.ByteBuffer.wrap(bytes), 0, length);
                    body.writeBytes(bytes);
                }
                truncated |= length < data.readableByteCount();
            }
        }

        private void logBody() {
            synchronized (body) {
                String content = body.toString(StandardCharsets.UTF_8);
                if (truncated) {
                    content += " [truncated]";
                }
                log.info("--> body {} {}\n{}", getMethod(), getURI(), content);
            }
        }
    }
}
