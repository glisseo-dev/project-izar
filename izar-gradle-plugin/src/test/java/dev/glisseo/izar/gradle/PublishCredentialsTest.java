package dev.glisseo.izar.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class PublishCredentialsTest {

    private static UnaryOperator<String> lookup(Map<String, String> values) {
        return values::get;
    }

    @Test
    void environmentVariablesWinOverGradleProperties() {
        var credentials = PublishCredentials.resolve(
                lookup(Map.of("IZAR_PUBLISH_USERNAME", "env-user", "IZAR_PUBLISH_PASSWORD", "env-secret")),
                lookup(Map.of("controllerUsername", "prop-user", "controllerPassword", "prop-secret")),
                "controller");

        assertThat(credentials).isEqualTo(new PublishCredentials.Credentials("env-user", "env-secret"));
    }

    @Test
    void readsGradlePropertiesNamedAfterTheCredentialsId() {
        var credentials = PublishCredentials.resolve(
                lookup(Map.of()),
                lookup(Map.of("controllerUsername", "prop-user", "controllerPassword", "prop-secret")),
                "controller");

        assertThat(credentials).isEqualTo(new PublishCredentials.Credentials("prop-user", "prop-secret"));
    }

    @Test
    void rejectsOnlyOneEnvironmentVariable() {
        assertThatThrownBy(() -> PublishCredentials.resolve(
                        lookup(Map.of("IZAR_PUBLISH_USERNAME", "env-user")), lookup(Map.of()), "controller"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both IZAR_PUBLISH_USERNAME and IZAR_PUBLISH_PASSWORD");
    }

    @Test
    void failsWhenNothingIsConfigured() {
        assertThatThrownBy(() -> PublishCredentials.resolve(lookup(Map.of()), lookup(Map.of()), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No publication credentials configured");
    }

    @Test
    void failsWhenAGradlePropertyIsMissing() {
        assertThatThrownBy(() -> PublishCredentials.resolve(
                        lookup(Map.of()), lookup(Map.of("controllerUsername", "prop-user")), "controller"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("controllerUsername")
                .hasMessageContaining("controllerPassword");
    }
}
