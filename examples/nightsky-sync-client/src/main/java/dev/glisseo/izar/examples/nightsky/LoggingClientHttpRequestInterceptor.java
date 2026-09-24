package dev.glisseo.izar.examples.nightsky;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Logs every GraphQL request and response this client sends, the way any consumer would add
 * request/response logging to its own {@code RestClient}: Spring ships the {@link
 * ClientHttpRequestInterceptor} extension point (used here) but no ready-made logging
 * implementation, so this is an ordinary application-supplied one, wired onto the {@code
 * RestClient} underlying {@link GraphQlClientConfiguration}'s {@code SynchronousGraphQlOperations}
 * the same way authentication or timeouts would be.
 */
final class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingClientHttpRequestInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        log.info(
                "--> {} {}\n{}",
                request.getMethod(),
                request.getURI(),
                new String(body, StandardCharsets.UTF_8));

        ClientHttpResponse response = execution.execute(request, body);
        byte[] responseBody = response.getBody().readAllBytes();
        log.info(
                "<-- {} {} {}\n{}",
                response.getStatusCode(),
                request.getMethod(),
                request.getURI(),
                new String(responseBody, StandardCharsets.UTF_8));

        return new BufferedClientHttpResponse(response, responseBody);
    }

    /**
     * Re-exposes a response body already consumed for logging, since {@link ClientHttpResponse}
     * only allows reading it once.
     */
    private record BufferedClientHttpResponse(ClientHttpResponse delegate, byte[] body)
            implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
