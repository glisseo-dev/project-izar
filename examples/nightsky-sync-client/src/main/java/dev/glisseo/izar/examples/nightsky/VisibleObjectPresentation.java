package dev.glisseo.izar.examples.nightsky;

import dev.glisseo.izar.examples.nightsky.generated.GetVisibleNowQuery;
import java.util.Locale;

/**
 * The one place that switches over {@link GetVisibleNowQuery.CelestialObject}'s variants for what
 * actually differs per concrete type, shared by {@link SkyExplorerRunner}'s printed story and
 * {@link SkyExplorerController}'s JSON view, so a fourth {@code CelestialObject} implementation
 * only needs a new case here. Fields every variant shares ({@code id}, {@code name}, {@code
 * magnitude}, {@code constellation}) are declared straight on the sealed interface itself and read
 * directly off {@code object}, with no switch needed.
 */
final class VisibleObjectPresentation {

    private VisibleObjectPresentation() {}

    static String kind(GetVisibleNowQuery.CelestialObject object) {
        return switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar star -> "Star";
            case GetVisibleNowQuery.CelestialObjectNebula nebula -> "Nebula";
            case GetVisibleNowQuery.CelestialObjectGalaxy galaxy -> "Galaxy";
            case GetVisibleNowQuery.CelestialObjectUnrecognized unrecognized -> "Unrecognized";
        };
    }

    /** What sets this concrete type apart: a star's spectral type, a nebula's kind, and so on. */
    static String detail(GetVisibleNowQuery.CelestialObject object) {
        return switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar star -> "spectral type " + star.spectralType();
            case GetVisibleNowQuery.CelestialObjectNebula nebula -> nebula.nebulaType().toString();
            case GetVisibleNowQuery.CelestialObjectGalaxy galaxy ->
                    galaxy.distanceLightYears() + " light years away";
            case GetVisibleNowQuery.CelestialObjectUnrecognized unrecognized -> unrecognized.__typename();
        };
    }

    /** The one-line description {@link SkyExplorerRunner} prints for a visible object. */
    static String describe(GetVisibleNowQuery.CelestialObject object) {
        String typeSpecific = switch (object) {
            case GetVisibleNowQuery.CelestialObjectStar star -> "star, spectral type " + star.spectralType();
            case GetVisibleNowQuery.CelestialObjectNebula nebula -> nebula.nebulaType() + " nebula";
            case GetVisibleNowQuery.CelestialObjectGalaxy galaxy ->
                    galaxy.galaxyType() + " galaxy, " + galaxy.distanceLightYears() + " light years away";
            case GetVisibleNowQuery.CelestialObjectUnrecognized unrecognized ->
                    "unrecognized type '" + unrecognized.__typename() + "'";
        };
        return String.format(
                Locale.ROOT, "%s (%s) - %s, magnitude %.2f", object.name(), object.constellation().name(),
                typeSpecific, object.magnitude());
    }
}
