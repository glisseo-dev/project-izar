package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.OperationProvenance;
import graphql.language.Document;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checks an assembled supported-release selection's stored operations against a candidate schema
 * before deployment, per decisions 18 through 20 of the phase 2 specification: the
 * build-tool-independent seam Maven adapters and the standalone command both call, the same way
 * {@link ReleaseAssembler} and {@link ManifestPublisher} stay reusable across build integrations.
 *
 * <p>Combines two independent checks against {@link CandidateSchemas#candidate()}: GraphQL Java's
 * own {@link Validator} confirms every operation document still executes against it, and {@link
 * SchemaImpactAnalyzer} explains structural risk that document validation alone does not surface
 * (a nullability change still valid syntactically, for instance), cross-referenced against a {@link
 * UsageIndex} of {@link CandidateSchemas#baseline()} so a finding still names every release that
 * depends on it. An operation shared verbatim by several releases is validated once and its
 * failure, if any, still names every contributing release, mirroring how {@link UsageIndex}
 * already attributes usage.
 *
 * <p>Performs no network lookup and changes no current schema, selection, published snapshot, or
 * runtime registry: {@code check} is a pure computation over its arguments.
 */
public final class CandidateChecker {

    /** Checks {@code assembled}'s operations against {@code schemas}. */
    public CandidateCheckReport check(CandidateSchemas schemas, AssembledManifest assembled) {
        OperationManifest manifest = assembled.manifest();
        List<OperationProvenance> provenance = assembled.provenance();

        UsageIndex usage = UsageIndex.of(schemas.baseline(), manifest, provenance);
        List<ImpactReport.SchemaChange> changes =
                SchemaImpactAnalyzer.analyze(schemas.baseline(), schemas.candidate(), usage);
        List<OperationValidationFailure> validationFailures =
                validateAgainstCandidate(schemas.candidate(), manifest, provenance);

        String selectionIdentity = assembled.lock().identity();
        return new CandidateCheckReport(schemas.baselineIdentity(), schemas.candidateIdentity(), selectionIdentity,
                usage.isComplete(), usage.incomplete(), validationFailures, changes);
    }

    private static List<OperationValidationFailure> validateAgainstCandidate(
            GraphQLSchema candidate, OperationManifest manifest, List<OperationProvenance> provenance) {
        Map<String, OperationProvenance> provenanceById = provenance.stream()
                .collect(Collectors.toMap(OperationProvenance::operationId, Function.identity()));
        Validator validator = new Validator();
        List<OperationValidationFailure> failures = new ArrayList<>();
        for (ManifestOperation operation : manifest.operations()) {
            List<OperationRef> refs = OperationRef.refsFor(operation, provenanceById);
            try {
                Document document = Parser.parse(operation.body());
                List<ValidationError> errors = validator.validateDocument(candidate, document, Locale.ROOT);
                if (!errors.isEmpty()) {
                    failures.add(new OperationValidationFailure(operation.id(), operation.name(), refs,
                            errors.stream().map(ValidationError::getMessage).toList()));
                }
            } catch (InvalidSyntaxException e) {
                failures.add(new OperationValidationFailure(operation.id(), operation.name(), refs,
                        List.of("Operation document failed to parse: " + e.getMessage())));
            }
        }
        return List.copyOf(failures);
    }
}
