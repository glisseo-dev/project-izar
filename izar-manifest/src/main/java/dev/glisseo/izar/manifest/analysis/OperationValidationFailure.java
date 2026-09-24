package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import java.util.List;

/**
 * One stored operation document that {@link CandidateChecker} found does not validate against the
 * candidate schema with GraphQL Java's own {@link graphql.validation.Validator}, per decision 19 of
 * the phase 2 specification: a definite incompatibility, not a heuristic derived from structural
 * diffing.
 *
 * @param operationId the failing operation's {@link ManifestOperation#id()}
 * @param operationName the failing operation's display name, for a readable report
 * @param releases every contributing release, so the failure can still be traced to an exact
 *     release even though it is reported once for the whole operation
 * @param errors every validation error's message, or the parse failure message if the document did
 *     not even parse
 */
public record OperationValidationFailure(String operationId, String operationName, List<OperationRef> releases, List<String> errors) {

    public OperationValidationFailure {
        releases = List.copyOf(releases);
        errors = List.copyOf(errors);
    }
}
