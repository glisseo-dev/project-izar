package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.assembly.ClientRelease;
import dev.glisseo.izar.manifest.assembly.OperationProvenance;
import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsageIndexTests {

    private static final ManifestOperation OPERATION_WITH_REQUIRED_VARIABLE = ManifestOperation.of(
            "Q", "query", "query Q($id: ID!) { thing(id: $id) { name } }");

    @Test
    void anOperationWithARequiredVariableIsNotMarkedIncomplete() {
        GraphQLSchema schema = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! name: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(OPERATION_WITH_REQUIRED_VARIABLE), provenanceOf(OPERATION_WITH_REQUIRED_VARIABLE));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.incomplete()).isEmpty();
        assertThat(index.forField("Query", "thing")).hasSize(1);
        assertThat(index.forField("Thing", "name")).hasSize(1);
        assertThat(index.forArgument("Query", "thing", "id")).hasSize(1);
        assertThat(index.forType("Thing")).hasSize(1);
    }

    @Test
    void retainsOperationReferencesForAnIntrospectionOnlyOperation() {
        ManifestOperation operation = ManifestOperation.of("Ping", "query", "query Ping { __typename }");
        GraphQLSchema schema = schema("type Query { thing: String }");

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.fieldUsage()).isEmpty();
        assertThat(index.operationRefs()).containsExactly(new OperationRef(operation.id(), "Ping", "catalog", "1.0"));
    }

    @Test
    void aDocumentThatNoLongerMatchesTheSchemaIsStillReportedIncompleteDespiteARequiredVariable() {
        GraphQLSchema driftedSchema = schema("""
                type Query { thing(id: ID!): Thing }
                type Thing { id: ID! }
                """);

        UsageIndex index = UsageIndex.of(
                driftedSchema, manifestOf(OPERATION_WITH_REQUIRED_VARIABLE), provenanceOf(OPERATION_WITH_REQUIRED_VARIABLE));

        assertThat(index.isComplete()).isFalse();
        assertThat(index.incomplete()).hasSize(1);
        assertThat(index.incomplete().get(0).operationId()).isEqualTo(OPERATION_WITH_REQUIRED_VARIABLE.id());
    }

    @Test
    void aVariableDeclaringATypeTheSchemaNoLongerHasIsReportedIncomplete() {
        ManifestOperation operation = ManifestOperation.of(
                "Q", "query", "query Q($filter: MissingInput!) { thing { name } }");
        GraphQLSchema schema = schema("""
                type Query { thing: Thing }
                type Thing { name: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isFalse();
        assertThat(index.incomplete()).hasSize(1);
        assertThat(index.incomplete().get(0).operationId()).isEqualTo(operation.id());
    }

    @Test
    void aVariableSuppliedInputObjectArgumentAttributesEveryFieldItsDeclaredTypeCanCarry() {
        ManifestOperation operation = ManifestOperation.of(
                "Q", "query", "query Q($filter: CustomerFilter!) { things(filter: $filter) { id } }");
        GraphQLSchema schema = schema("""
                type Query { things(filter: CustomerFilter!): [Thing!]! }
                type Thing { id: ID! }
                input CustomerFilter { email: String name: String status: Status }
                enum Status { ACTIVE INACTIVE }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.forInputField("CustomerFilter", "email")).hasSize(1);
        assertThat(index.forInputField("CustomerFilter", "name")).hasSize(1);
        assertThat(index.forInputField("CustomerFilter", "status")).hasSize(1);
    }

    @Test
    void anInlineInputObjectLiteralAttributesOnlyThePathsWrittenInTheDocument() {
        ManifestOperation operation = ManifestOperation.of(
                "Q", "query", "query Q { things(filter: { email: \"a@b.com\" }) { id } }");
        GraphQLSchema schema = schema("""
                type Query { things(filter: CustomerFilter!): [Thing!]! }
                type Thing { id: ID! }
                input CustomerFilter { email: String name: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.forInputField("CustomerFilter", "email")).hasSize(1);
        assertThat(index.forInputField("CustomerFilter", "name")).isEmpty();
    }

    @Test
    void aVariableNestedInsideAnInlineInputObjectLiteralAttributesEveryFieldItsOwnTypeCanCarry() {
        ManifestOperation operation = ManifestOperation.of(
                "Q", "query", "query Q($contact: ContactInput!) { things(filter: { contact: $contact }) { id } }");
        GraphQLSchema schema = schema("""
                type Query { things(filter: CustomerFilter!): [Thing!]! }
                type Thing { id: ID! }
                input CustomerFilter { name: String contact: ContactInput }
                input ContactInput { email: String phone: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.forInputField("CustomerFilter", "contact")).hasSize(1);
        assertThat(index.forInputField("CustomerFilter", "name")).isEmpty();
        assertThat(index.forInputField("ContactInput", "email")).hasSize(1);
        assertThat(index.forInputField("ContactInput", "phone")).hasSize(1);
    }

    @Test
    void aVariableReusedAcrossTwoArgumentsAttributesUsageAtBothLocations() {
        ManifestOperation operation = ManifestOperation.of(
                "Q",
                "query",
                "query Q($filter: CustomerFilter!) { a: things(filter: $filter) { id } b: otherThings(filter: $filter) { id } }");
        GraphQLSchema schema = schema("""
                type Query {
                  things(filter: CustomerFilter!): [Thing!]!
                  otherThings(filter: CustomerFilter!): [Thing!]!
                }
                type Thing { id: ID! }
                input CustomerFilter { email: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.forInputField("CustomerFilter", "email")).hasSize(1);
        assertThat(index.forArgument("Query", "things", "filter")).hasSize(1);
        assertThat(index.forArgument("Query", "otherThings", "filter")).hasSize(1);
    }

    @Test
    void anUnusedVariableDefinitionIsIgnored() {
        ManifestOperation operation = ManifestOperation.of(
                "Q", "query", "query Q($unused: CustomerFilter) { things { id } }");
        GraphQLSchema schema = schema("""
                type Query { things: [Thing!]! }
                type Thing { id: ID! }
                input CustomerFilter { email: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.forInputField("CustomerFilter", "email")).isEmpty();
    }

    @Test
    void anUnsuppliedOptionalArgumentIsNotAttributed() {
        ManifestOperation operation = ManifestOperation.of("Q", "query", "query Q { things { id } }");
        GraphQLSchema schema = schema("""
                type Query { things(filter: CustomerFilter): [Thing!]! }
                type Thing { id: ID! }
                input CustomerFilter { email: String }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.forInputField("CustomerFilter", "email")).isEmpty();
        assertThat(index.forArgument("Query", "things", "filter")).isEmpty();
    }

    @Test
    void aSelfReferentialRequiredInputVariableDoesNotOverflowTheStackAndIsNotMarkedIncomplete() {
        ManifestOperation operation = ManifestOperation.of(
                "Q", "query", "query Q($filter: Filter!) { things(filter: $filter) { id } }");
        GraphQLSchema schema = schema("""
                type Query { things(filter: Filter!): [Thing!]! }
                type Thing { id: ID! }
                input Filter { and: [Filter!]! }
                """);

        UsageIndex index = UsageIndex.of(schema, manifestOf(operation), provenanceOf(operation));

        assertThat(index.isComplete()).isTrue();
        assertThat(index.incomplete()).isEmpty();
        assertThat(index.forField("Query", "things")).hasSize(1);
        assertThat(index.forArgument("Query", "things", "filter")).hasSize(1);
        assertThat(index.forInputField("Filter", "and")).hasSize(1);
    }

    private static OperationManifest manifestOf(ManifestOperation operation) {
        return OperationManifest.of(List.of(operation));
    }

    private static List<OperationProvenance> provenanceOf(ManifestOperation operation) {
        return List.of(new OperationProvenance(operation.id(), List.of(new ClientRelease("catalog", "1.0"))));
    }

    private static GraphQLSchema schema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }
}
