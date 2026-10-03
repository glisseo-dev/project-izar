package dev.glisseo.izar.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsProblem;
import org.apache.maven.settings.crypto.SettingsDecryptionResult;
import org.junit.jupiter.api.Test;

class PublishCredentialsTest {

    private static final Settings NO_SERVERS = new Settings();

    @Test
    void prefersEnvironmentVariablesWhenBothAreSet() {
        var env = Map.of("IZAR_PUBLISH_USERNAME", "publisher", "IZAR_PUBLISH_PASSWORD", "secret");

        var credentials = PublishCredentials.resolve(env::get, NO_SERVERS, identityDecrypter(), null);

        assertThat(credentials).isEqualTo(new PublishCredentials.Credentials("publisher", "secret"));
    }

    @Test
    void rejectsOnlyOneOfTheEnvironmentVariablesBeingSet() {
        var env = Map.of("IZAR_PUBLISH_USERNAME", "publisher");

        assertThatThrownBy(() -> PublishCredentials.resolve(env::get, NO_SERVERS, identityDecrypter(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both");
    }

    @Test
    void fallsBackToTheReferencedSettingsXmlServerWhenNoEnvironmentVariablesAreSet() {
        var settings = new Settings();
        var server = new Server();
        server.setId("izar-manifest-service");
        server.setUsername("publisher");
        server.setPassword("secret");
        settings.addServer(server);

        var credentials = PublishCredentials.resolve(key -> null, settings, identityDecrypter(), "izar-manifest-service");

        assertThat(credentials).isEqualTo(new PublishCredentials.Credentials("publisher", "secret"));
    }

    @Test
    void decryptsAnEncryptedSettingsXmlServerPassword() {
        var settings = new Settings();
        var server = new Server();
        server.setId("izar-manifest-service");
        server.setUsername("publisher");
        server.setPassword("{encrypted}");
        settings.addServer(server);

        var decrypted = new Server();
        decrypted.setUsername("publisher");
        decrypted.setPassword("secret");
        SettingsDecryptionResult result = fixedResult(decrypted, List.of());

        var credentials = PublishCredentials.resolve(key -> null, settings, request -> result, "izar-manifest-service");

        assertThat(credentials).isEqualTo(new PublishCredentials.Credentials("publisher", "secret"));
    }

    @Test
    void failsWithNoEnvironmentVariablesAndNoServerId() {
        assertThatThrownBy(() -> PublishCredentials.resolve(key -> null, NO_SERVERS, identityDecrypter(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IZAR_PUBLISH_USERNAME");
    }

    @Test
    void failsWhenTheReferencedServerIdIsMissingFromSettingsXml() {
        assertThatThrownBy(
                        () -> PublishCredentials.resolve(key -> null, NO_SERVERS, identityDecrypter(), "missing-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-id");
    }

    @Test
    void failsWhenTheReferencedServerHasNoPassword() {
        var settings = new Settings();
        var server = new Server();
        server.setId("izar-manifest-service");
        server.setUsername("publisher");
        settings.addServer(server);

        assertThatThrownBy(() -> PublishCredentials.resolve(key -> null, settings, identityDecrypter(), "izar-manifest-service"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("izar-manifest-service");
    }

    private static org.apache.maven.settings.crypto.SettingsDecrypter identityDecrypter() {
        return request -> fixedResult(
                request.getServers().isEmpty() ? null : request.getServers().get(0), List.of());
    }

    private static SettingsDecryptionResult fixedResult(Server server, List<SettingsProblem> problems) {
        return new SettingsDecryptionResult() {
            @Override
            public Server getServer() {
                return server;
            }

            @Override
            public List<Server> getServers() {
                return server == null ? List.of() : List.of(server);
            }

            @Override
            public org.apache.maven.settings.Proxy getProxy() {
                return null;
            }

            @Override
            public List<org.apache.maven.settings.Proxy> getProxies() {
                return List.of();
            }

            @Override
            public List<SettingsProblem> getProblems() {
                return problems;
            }
        };
    }
}
