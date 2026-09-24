package dev.glisseo.izar.maven;

import java.util.function.UnaryOperator;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.jspecify.annotations.Nullable;

/**
 * Resolves publisher credentials from external configuration only: the {@code
 * IZAR_PUBLISH_USERNAME}/{@code IZAR_PUBLISH_PASSWORD} environment variables, or a Maven {@code
 * <server>} entry referenced by {@code serverId} and decrypted through the usual Maven settings
 * mechanism. Neither a committed {@code pom.xml} value nor a plugin parameter can carry a password:
 * there is deliberately no such {@code @Parameter} for either field, so a password can never appear
 * in a POM, a Maven {@code -D} flag, or a build log.
 *
 * <p>Kept free of {@link org.apache.maven.plugin.AbstractMojo} so it is testable with plain
 * objects, not a Maven plugin test harness. See ADR 0017 for why there is no password
 * {@code @Parameter} at all, on this class or {@link PublishMojo}.
 */
final class PublishCredentials {

    private PublishCredentials() {}

    record Credentials(String username, String password) {}

    static Credentials resolve(
            UnaryOperator<@Nullable String> env,
            Settings settings,
            SettingsDecrypter decrypter,
            @Nullable String serverId) {
        String envUsername = env.apply("IZAR_PUBLISH_USERNAME");
        String envPassword = env.apply("IZAR_PUBLISH_PASSWORD");
        boolean usernameSet = envUsername != null && !envUsername.isBlank();
        boolean passwordSet = envPassword != null && !envPassword.isBlank();
        if (usernameSet != passwordSet) {
            throw new IllegalStateException(
                    "Set both IZAR_PUBLISH_USERNAME and IZAR_PUBLISH_PASSWORD, or neither.");
        }
        if (usernameSet) {
            return new Credentials(envUsername, envPassword);
        }

        if (serverId == null || serverId.isBlank()) {
            throw new IllegalStateException(
                    "No publication credentials configured. Set IZAR_PUBLISH_USERNAME and "
                            + "IZAR_PUBLISH_PASSWORD, or set izar.publish.serverId to a <server> entry in "
                            + "settings.xml.");
        }
        Server server = settings.getServer(serverId);
        if (server == null) {
            throw new IllegalStateException("No <server> with id '" + serverId + "' in settings.xml.");
        }
        var decrypted = decrypter.decrypt(new DefaultSettingsDecryptionRequest(server));
        if (!decrypted.getProblems().isEmpty()) {
            throw new IllegalStateException(
                    "Could not decrypt settings.xml server '" + serverId + "': " + decrypted.getProblems());
        }
        Server resolved = decrypted.getServer() != null ? decrypted.getServer() : server;
        String username = resolved.getUsername();
        String password = resolved.getPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "settings.xml server '" + serverId + "' needs both <username> and <password>.");
        }
        return new Credentials(username, password);
    }
}
