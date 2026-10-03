package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.AssembledManifest;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.InputLock;
import dev.glisseo.izar.manifest.assembly.LockedRelease;
import dev.glisseo.izar.manifest.assembly.OperationProvenance;
import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateCheckerTest {

    private static final CandidateChecker CHECKER = new CandidateChecker();

    @Test
    void anUnchangedCandidateReportsNoChangesAndNoValidationFailures() {
        GraphQLSchema schema = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThing", "query", "query GetThing($id: ID!) { thing(id: $id) { name } }");
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(schema, schema), assembled);

        assertThat(report.complete()).isTrue();
        assertThat(report.incomplete()).isEmpty();
        assertThat(report.changes()).isEmpty();
        assertThat(report.validationFailures()).isEmpty();
        assertThat(report.hasBreakingChanges()).isFalse();
        assertThat(report.baselineIdentity()).isEqualTo("baseline.graphqls");
        assertThat(report.candidateIdentity()).isEqualTo("candidate.graphqls");
        assertThat(report.selectionIdentity()).isEqualTo("nightsky/production");
    }

    @Test
    void aBreakingStructuralChangeIsReportedWithUsage() {
        GraphQLSchema baseline = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);
        GraphQLSchema candidate = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThing", "query", "query GetThing($id: ID!) { thing(id: $id) { name } }");
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(baseline, candidate), assembled);

        assertThat(report.hasBreakingChanges()).isTrue();
        assertThat(report.changes()).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ImpactReport.Category.FIELD_REMOVED);
            assertThat(change.typeName()).isEqualTo("Thing");
            assertThat(change.fieldName()).isEqualTo("name");
            assertThat(change.usedBy()).extracting(OperationRef::operationId).containsExactly(operation.id());
        });
    }

    @Test
    void aCandidateValidationFailureIsReportedForAnOperationThatNoLongerValidates() {
        GraphQLSchema baseline = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);
        GraphQLSchema candidate = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThing", "query", "query GetThing($id: ID!) { thing(id: $id) { name } }");
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(baseline, candidate), assembled);

        assertThat(report.validationFailures()).hasSize(1);
        OperationValidationFailure failure = report.validationFailures().get(0);
        assertThat(failure.operationId()).isEqualTo(operation.id());
        assertThat(failure.operationName()).isEqualTo("GetThing");
        assertThat(failure.releases()).extracting(OperationRef::clientName).containsExactly("catalog");
        assertThat(failure.errors()).isNotEmpty();
    }

    @Test
    void aNonBreakingChangeProducesNoValidationFailure() {
        GraphQLSchema baseline = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);
        GraphQLSchema candidate = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String, description: String }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThing", "query", "query GetThing($id: ID!) { thing(id: $id) { name } }");
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(baseline, candidate), assembled);

        assertThat(report.validationFailures()).isEmpty();
        assertThat(report.hasBreakingChanges()).isFalse();
        assertThat(report.changes()).anySatisfy(change -> assertThat(change.category()).isEqualTo(ImpactReport.Category.FIELD_ADDED));
    }

    @Test
    void incompleteBaselineAnalysisIsReportedAndDoesNotCrashTheCheck() {
        GraphQLSchema schema = schema("""
                type Query { thing: Thing }
                type Thing { name: String }
                """);
        ManifestOperation operation = ManifestOperation.of("Q", "query", "query Q($filter: MissingInput!) { thing { name } }");
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(schema, schema), assembled);

        assertThat(report.complete()).isFalse();
        assertThat(report.incomplete()).hasSize(1);
        assertThat(report.incomplete().get(0).operationId()).isEqualTo(operation.id());
    }

    @Test
    void aSharedOperationIsCheckedOnceButRetainsEveryContributingRelease() {
        GraphQLSchema baseline = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);
        GraphQLSchema candidate = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThing", "query", "query GetThing($id: ID!) { thing(id: $id) { name } }");
        OperationManifest manifest = OperationManifest.of(List.of(operation));
        List<OperationProvenance> provenance = List.of(new OperationProvenance(operation.id(),
                List.of(new ClientRelease("catalog-web", "1.0.0"), new ClientRelease("catalog-mobile", "2.3.1"))));
        AssembledManifest assembled = new AssembledManifest(manifest, provenance, new InputLock("nightsky", "production", List.of()));

        CandidateCheckReport report = CHECKER.check(schemas(baseline, candidate), assembled);

        assertThat(report.validationFailures()).hasSize(1);
        assertThat(report.validationFailures().get(0).releases())
                .extracting(OperationRef::clientName)
                .containsExactlyInAnyOrder("catalog-web", "catalog-mobile");
        assertThat(report.changes()).anySatisfy(change -> assertThat(change.usedBy())
                .extracting(OperationRef::clientName)
                .containsExactlyInAnyOrder("catalog-web", "catalog-mobile"));
    }

    @Test
    void aliasesFragmentsAndConditionalSelectionsAreCheckedWithoutError() {
        GraphQLSchema schema = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String description: String }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThing", "query", """
                query GetThing($id: ID!, $withDescription: Boolean!) {
                    aliasedThing: thing(id: $id) {
                        ...ThingFields
                        description @include(if: $withDescription)
                    }
                }
                fragment ThingFields on Thing {
                    id
                    name
                }
                """);
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(schema, schema), assembled);

        assertThat(report.complete()).isTrue();
        assertThat(report.validationFailures()).isEmpty();
        assertThat(report.changes()).isEmpty();
    }

    @Test
    void anEnumValueRemovalIsAttributedAtTheEnclosingTypeLevel() {
        GraphQLSchema baseline = schema("""
                type Query { things(status: Status): [Thing!]! }
                type Thing { id: ID! }
                enum Status { ACTIVE INACTIVE }
                """);
        GraphQLSchema candidate = schema("""
                type Query { things(status: Status): [Thing!]! }
                type Thing { id: ID! }
                enum Status { ACTIVE }
                """);
        ManifestOperation operation = ManifestOperation.of("GetThings", "query", "query GetThings { things(status: ACTIVE) { id } }");
        AssembledManifest assembled = assembled(operation, "catalog", "1.0.0");

        CandidateCheckReport report = CHECKER.check(schemas(baseline, candidate), assembled);

        // The literal value the operation actually supplies (ACTIVE) still exists in the candidate,
        // so GraphQL Java's validator has nothing to reject; the removal is only visible structurally.
        assertThat(report.validationFailures()).isEmpty();
        assertThat(report.changes()).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ImpactReport.Category.ENUM_VALUE_REMOVED);
            assertThat(change.typeName()).isEqualTo("Status");
            assertThat(change.granularity()).isEqualTo(ImpactReport.Granularity.TYPE);
            assertThat(change.usedBy()).extracting(OperationRef::operationId).containsExactly(operation.id());
        });
    }

    @Test
    void allSelectedReleasesAreCheckedAndAFindingNamesOnlyTheReleaseItActuallyAffects() {
        GraphQLSchema baseline = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);
        GraphQLSchema candidate = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! }
                """);
        ManifestOperation queriesName = ManifestOperation.of("GetThingName", "query", "query GetThingName { thing(id: \"x\") { name } }");
        ManifestOperation queriesIdOnly = ManifestOperation.of("GetThingId", "query", "query GetThingId { thing(id: \"x\") { id } }");
        OperationManifest manifest = OperationManifest.of(List.of(queriesName, queriesIdOnly));
        List<OperationProvenance> provenance = List.of(
                new OperationProvenance(queriesName.id(), List.of(new ClientRelease("web-app", "1.0.0"))),
                new OperationProvenance(queriesIdOnly.id(), List.of(new ClientRelease("mobile-app", "2.0.0"))));
        AssembledManifest assembled = new AssembledManifest(manifest, provenance, new InputLock("nightsky", "production", List.of()));

        CandidateCheckReport report = CHECKER.check(schemas(baseline, candidate), assembled);

        assertThat(report.complete()).isTrue();
        assertThat(report.validationFailures()).hasSize(1);
        assertThat(report.validationFailures().get(0).operationId()).isEqualTo(queriesName.id());
        assertThat(report.validationFailures().get(0).releases()).extracting(OperationRef::clientName).containsExactly("web-app");
        assertThat(report.changes()).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ImpactReport.Category.FIELD_REMOVED);
            assertThat(change.usedBy()).extracting(OperationRef::clientName).containsExactly("web-app");
        });
    }

    private static AssembledManifest assembled(ManifestOperation operation, String clientName, String manifestVersion) {
        OperationManifest manifest = OperationManifest.of(List.of(operation));
        List<OperationProvenance> provenance =
                List.of(new OperationProvenance(operation.id(), List.of(new ClientRelease(clientName, manifestVersion))));
        return new AssembledManifest(manifest, provenance, new InputLock("nightsky", "production", List.of()));
    }

    private static CandidateSchemas schemas(GraphQLSchema baseline, GraphQLSchema candidate) {
        return new CandidateSchemas(baseline, "baseline.graphqls", candidate, "candidate.graphqls");
    }

    private static GraphQLSchema schema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }
}
