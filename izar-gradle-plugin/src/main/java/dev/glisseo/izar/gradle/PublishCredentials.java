package dev.glisseo.izar.gradle;

import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Resolves publisher credentials from external configuration only: the {@code
 * IZAR_PUBLISH_USERNAME}/{@code IZAR_PUBLISH_PASSWORD} environment variables, or the Gradle
 * properties {@code <credentialsId>Username} and {@code <credentialsId>Password}, which Gradle
 * reads from {@code ~/.gradle/gradle.properties}, {@code ORG_GRADLE_PROJECT_*} environment
 * variables, or the command line. There is deliberately no task option or extension property for
 * either value, so a password can never appear in a build script or a build log.
 */
final class PublishCredentials {

    private PublishCredentials() {}

    record Credentials(String username, String password) {}

    static Credentials resolve(
            UnaryOperator<@Nullable String> env,
            UnaryOperator<@Nullable String> gradleProperty,
            @Nullable String credentialsId) {
        String envUsername = env.apply("IZAR_PUBLISH_USERNAME");
        String envPassword = env.apply("IZAR_PUBLISH_PASSWORD");
        boolean usernameSet = isSet(envUsername);
        boolean passwordSet = isSet(envPassword);
        if (usernameSet != passwordSet) {
            throw new IllegalStateException("Set both IZAR_PUBLISH_USERNAME and IZAR_PUBLISH_PASSWORD, or neither.");
        }
        if (usernameSet) {
            return new Credentials(envUsername, envPassword);
        }

        if (credentialsId == null || credentialsId.isBlank()) {
            throw new IllegalStateException(
                    "No publication credentials configured. Set IZAR_PUBLISH_USERNAME and IZAR_PUBLISH_PASSWORD, "
                            + "or set izar.publish.credentialsId to a prefix whose <prefix>Username and "
                            + "<prefix>Password Gradle properties hold them.");
        }
        String username = gradleProperty.apply(credentialsId + "Username");
        String password = gradleProperty.apply(credentialsId + "Password");
        if (!isSet(username) || !isSet(password)) {
            throw new IllegalStateException("Gradle properties '" + credentialsId + "Username' and '" + credentialsId
                    + "Password' must both be set.");
        }
        return new Credentials(username, password);
    }

    private static boolean isSet(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
