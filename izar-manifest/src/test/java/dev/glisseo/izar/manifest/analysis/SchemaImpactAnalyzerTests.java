package dev.glisseo.izar.manifest.analysis;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import static org.assertj.core.api.Assertions.assertThat;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaImpactAnalyzerTests {

    /**
     * Moving a field to a differently-named sibling type (query namespacing) made
     * GraphQL Java's schema diffing report the move as a "rename" carrying a field-type-modification
     * detail keyed by the field's new, post-rename name, a name {@code fromSchema}'s field
     * container never had, since it was still on the old name. That first crashed with an NPE
     * once the crash was fixed by cross-referencing the rename, the resulting "renamed
     * to a field of a completely different type" report was still confusing, since nothing about the
     * new field actually behaves like the old one. A rename entangled with a type or
     * argument change is now reported as a plain removal of the old field plus an addition of the
     * new one instead, with no residual argument-level detail for the removed field.
     */
    @Test
    void aFieldRenamedAndRetypedInTheSameChangeIsReportedAsRemovalPlusAddition() {
        GraphQLSchema from = schema("""
                type Query {
                    observations(locationId: ID!): [String!]!
                }
                """);
        GraphQLSchema to = schema("""
                type Query {
                    observationsNamespaced: QueryTest
                }
                type QueryTest {
                    observations(locationId: ID!): [String!]
                }
                """);

        List<ImpactReport.SchemaChange> changes = SchemaImpactAnalyzer.analyze(from, to, emptyUsage(from));

        assertThat(changes).extracting(ImpactReport.SchemaChange::category)
                .doesNotContain(ImpactReport.Category.FIELD_RENAMED, ImpactReport.Category.FIELD_TYPE_CHANGED,
                        ImpactReport.Category.ARGUMENT_REMOVED);
        assertThat(changes).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ImpactReport.Category.FIELD_REMOVED);
            assertThat(change.typeName()).isEqualTo("Query");
            assertThat(change.fieldName()).isEqualTo("observations");
        });
        assertThat(changes).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ImpactReport.Category.FIELD_ADDED);
            assertThat(change.typeName()).isEqualTo("Query");
            assertThat(change.fieldName()).isEqualTo("observationsNamespaced");
        });
    }

    /**
     * The same GraphQL Java quirk also surfaces through an argument's type modification: the
     * detail's field name is the field's new, post-rename spelling. Same treatment: removal plus
     * addition, not a rename carrying a mismatched argument-type-change detail.
     */
    @Test
    void anArgumentRetypedOnAFieldThatWasAlsoRenamedIsReportedAsRemovalPlusAddition() {
        GraphQLSchema from = schema("""
                type Query {
                    foo(x: String!): String!
                    bar: Int
                }
                """);
        GraphQLSchema to = schema("""
                type Query {
                    foo2(x: Int!): String!
                    bar: Int
                }
                """);

        List<ImpactReport.SchemaChange> changes = SchemaImpactAnalyzer.analyze(from, to, emptyUsage(from));

        assertThat(changes).extracting(ImpactReport.SchemaChange::category)
                .doesNotContain(ImpactReport.Category.FIELD_RENAMED, ImpactReport.Category.ARGUMENT_TYPE_CHANGED);
        assertThat(changes).extracting(ImpactReport.SchemaChange::category)
                .contains(ImpactReport.Category.FIELD_REMOVED, ImpactReport.Category.FIELD_ADDED);
    }

    /** A plain rename, no accompanying type or argument change, is still reported as a rename. */
    @Test
    void aPlainRenameWithNoOtherChangeIsStillReportedAsARename() {
        GraphQLSchema from = schema("""
                type Query {
                    thing(id: ID!): String!
                }
                """);
        GraphQLSchema to = schema("""
                type Query {
                    renamedThing(id: ID!): String!
                }
                """);

        List<ImpactReport.SchemaChange> changes = SchemaImpactAnalyzer.analyze(from, to, emptyUsage(from));

        assertThat(changes).extracting(ImpactReport.SchemaChange::category)
                .doesNotContain(ImpactReport.Category.FIELD_REMOVED, ImpactReport.Category.FIELD_ADDED);
        assertThat(changes).anySatisfy(change -> {
            assertThat(change.category()).isEqualTo(ImpactReport.Category.FIELD_RENAMED);
            assertThat(change.fieldName()).isEqualTo("thing");
        });
    }

    private static UsageIndex emptyUsage(GraphQLSchema schema) {
        return UsageIndex.of(schema, OperationManifest.of(List.of()), List.of());
    }

    private static GraphQLSchema schema(String sdl) {
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }
}
