package dev.glisseo.izar.gradle;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

/** The {@code skip} option every task shares: log once and leave the task's outputs untouched. */
final class SkipOption {

    private SkipOption() {}

    static void apply(Task task, Provider<Boolean> skip, String optionName, String message) {
        task.onlyIf(optionName + " is not set", t -> {
            boolean skipped = skip.get();
            if (skipped) {
                t.getLogger().lifecycle(message);
            }
            return !skipped;
        });
    }
}
