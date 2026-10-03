package dev.glisseo.izar.examples.nightsky.jackson;

import dev.glisseo.izar.examples.nightsky.jackson.generated.GetVisibleNowQuery;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Prints what's visible from Amsterdam on startup, through {@link NightskyGraphQlFacade}, the same
 * call {@link VisibleSkyController} serves over REST.
 */
@Component
class VisibleSkyRunner implements CommandLineRunner {

    private final NightskyGraphQlFacade facade;

    VisibleSkyRunner(NightskyGraphQlFacade facade) {
        this.facade = facade;
    }

    @Override
    public void run(String... args) {
        List<GetVisibleNowQuery.CelestialObject> visible = facade.visibleNow("amsterdam");

        System.out.println("=== Visible now from Amsterdam ===");
        for (GetVisibleNowQuery.CelestialObject object : visible) {
            System.out.printf(
                    Locale.ROOT,
                    "  %s (%s), magnitude %.2f [%s]%n",
                    object.name(),
                    object.constellation().name(),
                    object.magnitude(),
                    object.getClass().getSimpleName());
        }
    }
}
