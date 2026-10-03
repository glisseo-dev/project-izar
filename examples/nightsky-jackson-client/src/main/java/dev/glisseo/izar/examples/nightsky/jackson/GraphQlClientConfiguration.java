package dev.glisseo.izar.examples.nightsky.jackson;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpSyncGraphQlClient;

@Configuration
class GraphQlClientConfiguration {

    @Bean
    GraphQlClient nightskyGraphQlClient(
            @Value("${nightsky.server-url:http://localhost:8083/graphql}") String serverUrl) {
        return HttpSyncGraphQlClient.builder().url(serverUrl).build();
    }
}
