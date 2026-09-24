package dev.glisseo.izar.manifest.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompatibilityPolicyTest {

    @Test
    void bothPoliciesSucceedOnACleanReport() {
        CandidateCheckReport report = report(true, List.of(), List.of());

        assertThat(CompatibilityPolicy.STRICT.evaluate(report)).isEqualTo(CandidateCheckOutcome.SUCCESS);
        assertThat(CompatibilityPolicy.ADVISORY.evaluate(report)).isEqualTo(CandidateCheckOutcome.SUCCESS);
    }

    @Test
    void bothPoliciesFailOnAConfirmedValidationFailure() {
        CandidateCheckReport report = report(true, List.of(), List.of(validationFailure()));

        assertThat(CompatibilityPolicy.STRICT.evaluate(report)).isEqualTo(CandidateCheckOutcome.POLICY_FAILURE);
        assertThat(CompatibilityPolicy.ADVISORY.evaluate(report)).isEqualTo(CandidateCheckOutcome.POLICY_FAILURE);
    }

    @Test
    void onlyStrictFailsOnABreakingChangeWithNoValidationFailure() {
        CandidateCheckReport report = report(true, List.of(breakingChange()), List.of());

        assertThat(CompatibilityPolicy.STRICT.evaluate(report)).isEqualTo(CandidateCheckOutcome.POLICY_FAILURE);
        assertThat(CompatibilityPolicy.ADVISORY.evaluate(report)).isEqualTo(CandidateCheckOutcome.SUCCESS);
    }

    @Test
    void incompleteAnalysisWithNoOtherFindingIsItsOwnOutcomeUnderStrictOnly() {
        CandidateCheckReport incomplete = new CandidateCheckReport("baseline", "candidate", "graph/env",
                false, List.of(new IncompleteOperation("op-1", List.of(), "did not parse")), List.of(), List.of());

        assertThat(CompatibilityPolicy.STRICT.evaluate(incomplete)).isEqualTo(CandidateCheckOutcome.INCOMPLETE_ANALYSIS);
        assertThat(CompatibilityPolicy.ADVISORY.evaluate(incomplete)).isEqualTo(CandidateCheckOutcome.SUCCESS);
    }

    @Test
    void aConfirmedValidationFailureIsNeverMaskedByIncompleteAnalysisElsewhereInTheReport() {
        CandidateCheckReport incompleteWithAConfirmedFailure = new CandidateCheckReport("baseline", "candidate", "graph/env",
                false, List.of(new IncompleteOperation("op-2", List.of(), "did not parse")),
                List.of(validationFailure()), List.of());

        assertThat(CompatibilityPolicy.STRICT.evaluate(incompleteWithAConfirmedFailure)).isEqualTo(CandidateCheckOutcome.POLICY_FAILURE);
        assertThat(CompatibilityPolicy.ADVISORY.evaluate(incompleteWithAConfirmedFailure)).isEqualTo(CandidateCheckOutcome.POLICY_FAILURE);
    }

    @Test
    void underStrictABreakingChangeIsNeverMaskedByIncompleteAnalysisElsewhereInTheReport() {
        CandidateCheckReport incompleteWithABreakingChange = new CandidateCheckReport("baseline", "candidate", "graph/env",
                false, List.of(new IncompleteOperation("op-3", List.of(), "did not parse")),
                List.of(), List.of(breakingChange()));

        assertThat(CompatibilityPolicy.STRICT.evaluate(incompleteWithABreakingChange)).isEqualTo(CandidateCheckOutcome.POLICY_FAILURE);
    }

    private static CandidateCheckReport report(boolean complete, List<ImpactReport.SchemaChange> changes,
            List<OperationValidationFailure> validationFailures) {
        return new CandidateCheckReport("baseline", "candidate", "graph/env", complete, List.of(), validationFailures, changes);
    }

    private static ImpactReport.SchemaChange breakingChange() {
        return new ImpactReport.SchemaChange(ImpactReport.Severity.BREAKING, ImpactReport.Category.FIELD_REMOVED,
                "Thing.name was removed.", "Thing", "name", null, 0, List.of(), ImpactReport.Granularity.FIELD, false);
    }

    private static OperationValidationFailure validationFailure() {
        return new OperationValidationFailure("op-1", "GetThing", List.of(), List.of("Field 'name' does not exist."));
    }
}
