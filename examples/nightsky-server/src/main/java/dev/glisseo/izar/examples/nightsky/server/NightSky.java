package dev.glisseo.izar.examples.nightsky.server;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * A compressed night cycle: real Earth rotation takes 24 hours to bring every object through a
 * full rise-and-set cycle, far too slow to demonstrate a live reactive feed. This class instead
 * compresses one full night into {@link #NIGHT_CYCLE}, measured from server startup, so a client
 * polling every few seconds visibly watches the sky rotate within one demo run.
 *
 * <p>Longitude still behaves the way it does for a real night: {@link #currentHourAt} offsets the
 * compressed universal hour by 1 hour per 15 degrees of longitude, so two {@link ViewingLocation}s
 * far apart in longitude see different objects visible at the same instant, the way real
 * longitude determines local time of night.
 */
@Component
class NightSky {

    private static final Duration NIGHT_CYCLE = Duration.ofSeconds(60);

    private final Instant startedAt = Instant.now();

    double currentHourAt(GeoCoordinates location) {
        double localOffset = location.longitude() / 15.0;
        return normalize(universalHour() + localOffset);
    }

    boolean isVisible(CelestialObject object, double currentHour) {
        double rise = object.riseHour();
        double set = object.setHour();
        if (rise <= set) {
            return currentHour >= rise && currentHour < set;
        }
        // Wraps past midnight, for example rise=20, set=2: visible from 20:00 through 24:00, then
        // again from 00:00 up to (not including) 02:00.
        return currentHour >= rise || currentHour < set;
    }

    private double universalHour() {
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        double cycleFraction = (elapsedMillis % NIGHT_CYCLE.toMillis()) / (double) NIGHT_CYCLE.toMillis();
        return cycleFraction * 24.0;
    }

    private static double normalize(double hour) {
        double result = hour % 24.0;
        return result < 0 ? result + 24.0 : result;
    }
}
