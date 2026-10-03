package dev.glisseo.izar.manifest.analysis;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.diffing.SchemaDiffing;
import graphql.schema.diffing.ana.EditOperationAnalysisResult;
import graphql.schema.diffing.ana.SchemaDifference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Classifies the structural diff between two schema versions, computed with GraphQL Java's own
 * {@link SchemaDiffing}, never hand-rolled SDL diffing, into breaking and non-breaking
 * {@link ImpactReport.SchemaChange} entries, each cross-referenced against a {@link UsageIndex} of
 * the "from" schema.
 *
 * <p>An enum value or an input object field is never itself selected in an operation document.
 * A change to one is
 * attributed to whichever clients use the enclosing enum or input type at all, the finest
 * resolution the usage index can see, rather than a per-value or per-field usage it was never
 * built to track. Directive and applied-directive differences are
 * schema-authoring concerns, not operation-facing ones, and are not reported here.
 *
 * <p>Object and interface fields are diffed with GraphQL Java's two independent, structurally
 * identical detail hierarchies ({@code SchemaDifference.Object*}/{@code Interface*} share no
 * common method-bearing type). {@link #toDetail} adapts each into the local {@link Detail} shape
 * so {@link #classifyFieldContainer} - the classification logic, including the distinction
 * between field and argument usage - is written once and shared by both.
 */
public final class SchemaImpactAnalyzer {
    private SchemaImpactAnalyzer() {}

    public static List<ImpactReport.SchemaChange> analyze(GraphQLSchema fromSchema, GraphQLSchema toSchema, UsageIndex usage) {
        EditOperationAnalysisResult result;
        try {
            result = new SchemaDiffing().diffAndAnalyze(fromSchema, toSchema);
        } catch (Exception e) {
            throw new RuntimeException("Schema diffing failed.", e);
        }

        boolean reportIncomplete = !usage.isComplete();
        List<ImpactReport.SchemaChange> changes = new ArrayList<>();
        result.getObjectDifferences().forEach((name, diff) -> object(name, diff, fromSchema, toSchema, usage, reportIncomplete, changes));
        result.getInterfaceDifferences().forEach((name, diff) -> interfaceType(name, diff, fromSchema, toSchema, usage, reportIncomplete, changes));
        result.getUnionDifferences().forEach((name, diff) -> union(name, diff, usage, reportIncomplete, changes));
        result.getEnumDifferences().forEach((name, diff) -> enumType(name, diff, usage, reportIncomplete, changes));
        result.getInputObjectDifferences().forEach((name, diff) -> inputObject(name, diff, fromSchema, toSchema, usage, reportIncomplete, changes));
        result.getScalarDifferences().forEach((name, diff) -> scalar(name, diff, usage, reportIncomplete, changes));

        changes.sort(Comparator.comparing(ImpactReport.SchemaChange::severity)
                .thenComparing(ImpactReport.SchemaChange::typeName)
                .thenComparing(change -> Objects.toString(change.fieldName(), "")));
        return changes;
    }

    /** The local shape both {@code Object*} and {@code Interface*} modification details adapt to. */
    private sealed interface Detail {}

    private record FieldAdded(String name) implements Detail {}

    private record FieldDeleted(String name) implements Detail {}

    private record FieldRenamed(String oldName, String newName) implements Detail {}

    private record FieldTypeChanged(String fieldName, String oldType, String newType) implements Detail {}

    private record ArgumentAdded(String fieldName, String argumentName) implements Detail {}

    private record ArgumentDeleted(String fieldName, String argumentName) implements Detail {}

    private record ArgumentTypeChanged(String fieldName, String argumentName, String oldType, String newType) implements Detail {}

    private record ArgumentRenamed(String fieldName, String oldName, String newName) implements Detail {}

    private static void object(String name, SchemaDifference.ObjectDifference diff, GraphQLSchema fromSchema,
            GraphQLSchema toSchema, UsageIndex usage, boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        if (diff instanceof SchemaDifference.ObjectAddition) {
            changes.add(typeChange(ImpactReport.Category.TYPE_ADDED, ImpactReport.Severity.NON_BREAKING,
                    name + " was added.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.ObjectDeletion) {
            changes.add(typeChange(ImpactReport.Category.TYPE_REMOVED, ImpactReport.Severity.BREAKING,
                    name + " was removed.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.ObjectModification modification) {
            List<Detail> details = splitEntangledRenames(modification.getDetails().stream()
                    .map(SchemaImpactAnalyzer::toDetail).filter(Objects::nonNull).toList());
            classifyFieldContainer(name, details, fromSchema.getObjectType(name), toSchema.getObjectType(name), usage, reportIncomplete, changes);
        }
    }

    private static void interfaceType(String name, SchemaDifference.InterfaceDifference diff, GraphQLSchema fromSchema,
            GraphQLSchema toSchema, UsageIndex usage, boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        if (diff instanceof SchemaDifference.InterfaceAddition) {
            changes.add(typeChange(ImpactReport.Category.TYPE_ADDED, ImpactReport.Severity.NON_BREAKING,
                    name + " was added.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.InterfaceDeletion) {
            changes.add(typeChange(ImpactReport.Category.TYPE_REMOVED, ImpactReport.Severity.BREAKING,
                    name + " was removed.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.InterfaceModification modification) {
            List<Detail> details = splitEntangledRenames(modification.getDetails().stream()
                    .map(SchemaImpactAnalyzer::toDetail).filter(Objects::nonNull).toList());
            classifyFieldContainer(name, details, (GraphQLFieldsContainer) fromSchema.getType(name),
                    (GraphQLFieldsContainer) toSchema.getType(name), usage, reportIncomplete, changes);
        }
    }

    private static @Nullable Detail toDetail(SchemaDifference.ObjectModificationDetail detail) {
        if (detail instanceof SchemaDifference.ObjectFieldAddition d) return new FieldAdded(d.getName());
        if (detail instanceof SchemaDifference.ObjectFieldDeletion d) return new FieldDeleted(d.getName());
        if (detail instanceof SchemaDifference.ObjectFieldRename d) return new FieldRenamed(d.getOldName(), d.getNewName());
        if (detail instanceof SchemaDifference.ObjectFieldTypeModification d) return new FieldTypeChanged(d.getFieldName(), d.getOldType(), d.getNewType());
        if (detail instanceof SchemaDifference.ObjectFieldArgumentAddition d) return new ArgumentAdded(d.getFieldName(), d.getName());
        if (detail instanceof SchemaDifference.ObjectFieldArgumentDeletion d) return new ArgumentDeleted(d.getFieldName(), d.getName());
        if (detail instanceof SchemaDifference.ObjectFieldArgumentTypeModification d) return new ArgumentTypeChanged(d.getFieldName(), d.getArgumentName(), d.getOldType(), d.getNewType());
        if (detail instanceof SchemaDifference.ObjectFieldArgumentRename d) return new ArgumentRenamed(d.getFieldName(), d.getOldName(), d.getNewName());
        // Default-value modifications and interface-implementation changes have no operation-facing
        // shape to report: a stored document never observes an argument's default value or which
        // interfaces an object declares, only the fields it selects.
        return null;
    }

    private static @Nullable Detail toDetail(SchemaDifference.InterfaceModificationDetail detail) {
        if (detail instanceof SchemaDifference.InterfaceFieldAddition d) return new FieldAdded(d.getName());
        if (detail instanceof SchemaDifference.InterfaceFieldDeletion d) return new FieldDeleted(d.getName());
        if (detail instanceof SchemaDifference.InterfaceFieldRename d) return new FieldRenamed(d.getOldName(), d.getNewName());
        if (detail instanceof SchemaDifference.InterfaceFieldTypeModification d) return new FieldTypeChanged(d.getFieldName(), d.getOldType(), d.getNewType());
        if (detail instanceof SchemaDifference.InterfaceFieldArgumentAddition d) return new ArgumentAdded(d.getFieldName(), d.getName());
        if (detail instanceof SchemaDifference.InterfaceFieldArgumentDeletion d) return new ArgumentDeleted(d.getFieldName(), d.getName());
        if (detail instanceof SchemaDifference.InterfaceFieldArgumentTypeModification d) return new ArgumentTypeChanged(d.getFieldName(), d.getArgumentName(), d.getOldType(), d.getNewType());
        if (detail instanceof SchemaDifference.InterfaceFieldArgumentRename d) return new ArgumentRenamed(d.getFieldName(), d.getOldName(), d.getNewName());
        return null;
    }

    /**
     * Rewrites a {@link FieldRenamed} that shares its field with any other detail in the same
     * modification, a type change, or an argument addition/removal/rename/type-change, into a
     * plain {@link FieldDeleted}(old name) plus {@link FieldAdded}(new name), dropping the other
     * entangled details for that field.
     *
     * <p>GraphQL Java's schema diffing pairs an old and a new field by overall graph structure, not
     * name or shape: it will call an old field "renamed" to a new one even when the new field's type
     * is unrelated to the old one's (for example, moving a field to a differently named object,
     * query namespacing, reports the old field as "renamed" to a new field that returns the new
     * wrapper type). A rename is only a simple, safe-to-report-as-such edit when nothing else about
     * the field changed; once a type or argument also changed, describing it as one field that
     * mutated in place is misleading, and relying on cross-referencing the diffing engine's
     * inconsistent field-naming between detail kinds can fail. Reporting the
     * old field as removed and the new field as added instead needs no such cross-referencing: each
     * side is classified independently, using only the schema it actually belongs to.
     *
     * <p>A rename with no other detail on the same field (the ordinary case: a field kept its type
     * and arguments and just changed name) is left as a plain rename.
     */
    private static List<Detail> splitEntangledRenames(List<Detail> details) {
        List<FieldRenamed> entangled = details.stream()
                .filter(FieldRenamed.class::isInstance).map(FieldRenamed.class::cast)
                .filter(rename -> details.stream().anyMatch(other -> other != rename && referencesField(other, rename.oldName(), rename.newName())))
                .toList();
        if (entangled.isEmpty()) return details;

        List<Detail> result = new ArrayList<>();
        for (Detail detail : details) {
            boolean subsumed = detail instanceof FieldRenamed renamed
                    ? entangled.contains(renamed)
                    : entangled.stream().anyMatch(rename -> referencesField(detail, rename.oldName(), rename.newName()));
            if (!subsumed) result.add(detail);
        }
        for (FieldRenamed rename : entangled) {
            result.add(new FieldDeleted(rename.oldName()));
            result.add(new FieldAdded(rename.newName()));
        }
        return result;
    }

    private static boolean referencesField(Detail detail, String oldName, String newName) {
        String fieldName = switch (detail) {
            case FieldAdded d -> d.name();
            case FieldDeleted d -> d.name();
            case FieldRenamed d -> null;
            case FieldTypeChanged d -> d.fieldName();
            case ArgumentAdded d -> d.fieldName();
            case ArgumentDeleted d -> d.fieldName();
            case ArgumentTypeChanged d -> d.fieldName();
            case ArgumentRenamed d -> d.fieldName();
        };
        return fieldName != null && (fieldName.equals(oldName) || fieldName.equals(newName));
    }

    /**
     * The classification logic shared by object and interface types: field additions/removals/
     * renames/type-changes are attributed to whichever clients call the field at all
     * ({@link UsageIndex#forField}), but argument removals, renames, and type changes are
     * attributed only to clients that actually supply that argument ({@link UsageIndex#forArgument},
     * finer than field-level usage, since a client can call a field without ever passing one of
     * its optional arguments). A newly added argument is deliberately the one exception: it uses
     * field-level usage, because nobody could already "use" an argument that did not exist yet:
     * what matters there is who calls the field at all and will need to add it.
     */
    private static void classifyFieldContainer(String typeName, List<Detail> details, GraphQLFieldsContainer fromType,
            GraphQLFieldsContainer toType, UsageIndex usage, boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        for (Detail detail : details) {
            switch (detail) {
                case FieldAdded d -> changes.add(fieldChange(ImpactReport.Category.FIELD_ADDED, ImpactReport.Severity.NON_BREAKING,
                        typeName + "." + d.name() + " was added.", typeName, d.name(), null, usage, reportIncomplete));
                case FieldDeleted d -> changes.add(fieldChange(ImpactReport.Category.FIELD_REMOVED, ImpactReport.Severity.BREAKING,
                        typeName + "." + d.name() + " was removed.", typeName, d.name(), null, usage, reportIncomplete));
                case FieldRenamed d -> changes.add(fieldChange(ImpactReport.Category.FIELD_RENAMED, ImpactReport.Severity.BREAKING,
                        typeName + "." + d.oldName() + " was renamed to " + d.newName() + ".", typeName, d.oldName(), null, usage, reportIncomplete));
                case FieldTypeChanged d -> {
                    GraphQLFieldDefinition oldField = fromType.getFieldDefinition(d.fieldName());
                    GraphQLFieldDefinition newField = toType.getFieldDefinition(d.fieldName());
                    boolean safeNarrowing = oldField != null && newField != null
                            && isSafeOutputFieldNarrowing(oldField.getType(), newField.getType());
                    changes.add(fieldChange(ImpactReport.Category.FIELD_TYPE_CHANGED,
                            safeNarrowing ? ImpactReport.Severity.NON_BREAKING : ImpactReport.Severity.BREAKING,
                            typeName + "." + d.fieldName() + " changed type from " + d.oldType() + " to " + d.newType() + "."
                                    + (safeNarrowing ? " Narrowed to non-null; existing clients already tolerate a value here." : ""),
                            typeName, d.fieldName(), null, usage, reportIncomplete));
                }
                case ArgumentAdded d -> {
                    GraphQLFieldDefinition field = toType.getFieldDefinition(d.fieldName());
                    boolean required = field != null && isRequiredArgument(field, d.argumentName());
                    changes.add(fieldChange(
                            required ? ImpactReport.Category.ARGUMENT_ADDED_REQUIRED : ImpactReport.Category.ARGUMENT_ADDED_OPTIONAL,
                            required ? ImpactReport.Severity.BREAKING : ImpactReport.Severity.NON_BREAKING,
                            "Argument " + d.argumentName() + " was added to " + typeName + "." + d.fieldName()
                                    + (required ? ", required." : ", optional."),
                            typeName, d.fieldName(), d.argumentName(), usage, reportIncomplete));
                }
                case ArgumentDeleted d -> changes.add(argumentChange(ImpactReport.Category.ARGUMENT_REMOVED, ImpactReport.Severity.BREAKING,
                        "Argument " + d.argumentName() + " was removed from " + typeName + "." + d.fieldName() + ".",
                        typeName, d.fieldName(), d.argumentName(), usage, reportIncomplete));
                case ArgumentTypeChanged d -> {
                    GraphQLFieldDefinition oldField = fromType.getFieldDefinition(d.fieldName());
                    GraphQLFieldDefinition newField = toType.getFieldDefinition(d.fieldName());
                    GraphQLArgument oldArgument = oldField == null ? null : oldField.getArgument(d.argumentName());
                    GraphQLArgument newArgument = newField == null ? null : newField.getArgument(d.argumentName());
                    boolean safeLoosening = oldArgument != null && newArgument != null
                            && isSafeInputPositionLoosening(oldArgument.getType(), newArgument.getType());
                    changes.add(argumentChange(ImpactReport.Category.ARGUMENT_TYPE_CHANGED,
                            safeLoosening ? ImpactReport.Severity.NON_BREAKING : ImpactReport.Severity.BREAKING,
                            "Argument " + d.argumentName() + " of " + typeName + "." + d.fieldName() + " changed type from "
                                    + d.oldType() + " to " + d.newType() + "." + safeLoosening(safeLoosening),
                            typeName, d.fieldName(), d.argumentName(), usage, reportIncomplete));
                }
                case ArgumentRenamed d -> changes.add(argumentChange(ImpactReport.Category.ARGUMENT_RENAMED, ImpactReport.Severity.BREAKING,
                        "Argument " + d.oldName() + " of " + typeName + "." + d.fieldName() + " was renamed to " + d.newName() + ".",
                        typeName, d.fieldName(), d.oldName(), usage, reportIncomplete));
            }
        }
    }

    private static void union(String name, SchemaDifference.UnionDifference diff, UsageIndex usage,
            boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        if (diff instanceof SchemaDifference.UnionAddition) {
            changes.add(typeChange(ImpactReport.Category.TYPE_ADDED, ImpactReport.Severity.NON_BREAKING,
                    name + " was added.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.UnionDeletion) {
            changes.add(typeChange(ImpactReport.Category.TYPE_REMOVED, ImpactReport.Severity.BREAKING,
                    name + " was removed.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.UnionModification modification) {
            for (var detail : modification.getDetails()) {
                if (detail instanceof SchemaDifference.UnionMemberAddition addition) {
                    changes.add(typeChange(ImpactReport.Category.UNION_MEMBER_ADDED, ImpactReport.Severity.NON_BREAKING,
                            addition.getName() + " was added as a member of " + name + ".", addition.getName(), usage, reportIncomplete));
                } else if (detail instanceof SchemaDifference.UnionMemberDeletion deletion) {
                    changes.add(typeChange(ImpactReport.Category.UNION_MEMBER_REMOVED, ImpactReport.Severity.BREAKING,
                            deletion.getName() + " was removed as a member of " + name + ".", deletion.getName(), usage, reportIncomplete));
                }
            }
        }
    }

    private static void enumType(String name, SchemaDifference.EnumDifference diff, UsageIndex usage,
            boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        if (diff instanceof SchemaDifference.EnumAddition) {
            changes.add(typeChange(ImpactReport.Category.TYPE_ADDED, ImpactReport.Severity.NON_BREAKING,
                    name + " was added.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.EnumDeletion) {
            changes.add(typeChange(ImpactReport.Category.TYPE_REMOVED, ImpactReport.Severity.BREAKING,
                    name + " was removed.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.EnumModification modification) {
            for (var detail : modification.getDetails()) {
                if (detail instanceof SchemaDifference.EnumValueAddition addition) {
                    changes.add(typeChange(ImpactReport.Category.ENUM_VALUE_ADDED, ImpactReport.Severity.NON_BREAKING,
                            "Enum value " + addition.getName() + " was added to " + name + ".", name, usage, reportIncomplete));
                } else if (detail instanceof SchemaDifference.EnumValueDeletion deletion) {
                    changes.add(typeChange(ImpactReport.Category.ENUM_VALUE_REMOVED, ImpactReport.Severity.BREAKING,
                            "Enum value " + deletion.getName() + " was removed from " + name + ".", name, usage, reportIncomplete));
                } else if (detail instanceof SchemaDifference.EnumValueRenamed rename) {
                    changes.add(typeChange(ImpactReport.Category.ENUM_VALUE_RENAMED, ImpactReport.Severity.BREAKING,
                            "Enum value " + rename.getOldName() + " of " + name + " was renamed to " + rename.getNewName() + ".",
                            name, usage, reportIncomplete));
                }
            }
        }
    }

    private static void inputObject(String name, SchemaDifference.InputObjectDifference diff, GraphQLSchema fromSchema,
            GraphQLSchema toSchema, UsageIndex usage, boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        if (diff instanceof SchemaDifference.InputObjectAddition) {
            changes.add(typeChange(ImpactReport.Category.TYPE_ADDED, ImpactReport.Severity.NON_BREAKING,
                    name + " was added.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.InputObjectDeletion) {
            changes.add(typeChange(ImpactReport.Category.TYPE_REMOVED, ImpactReport.Severity.BREAKING,
                    name + " was removed.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.InputObjectModification modification) {
            for (var detail : modification.getDetails()) {
                if (detail instanceof SchemaDifference.InputObjectFieldAddition addition) {
                    GraphQLInputObjectType type = (GraphQLInputObjectType) toSchema.getType(name);
                    GraphQLInputObjectField field = type.getFieldDefinition(addition.getName());
                    boolean required = field != null && GraphQLTypeUtil.isNonNull(field.getType()) && !field.hasSetDefaultValue();
                    changes.add(typeChange(
                            required ? ImpactReport.Category.ARGUMENT_ADDED_REQUIRED : ImpactReport.Category.ARGUMENT_ADDED_OPTIONAL,
                            required ? ImpactReport.Severity.BREAKING : ImpactReport.Severity.NON_BREAKING,
                            "Field " + addition.getName() + " was added to " + name + (required ? ", required." : ", optional."),
                            name, usage, reportIncomplete));
                } else if (detail instanceof SchemaDifference.InputObjectFieldDeletion deletion) {
                    changes.add(typeChange(ImpactReport.Category.FIELD_REMOVED, ImpactReport.Severity.BREAKING,
                            "Field " + deletion.getName() + " was removed from " + name + ".", name, usage, reportIncomplete));
                } else if (detail instanceof SchemaDifference.InputObjectFieldRename rename) {
                    changes.add(typeChange(ImpactReport.Category.FIELD_RENAMED, ImpactReport.Severity.BREAKING,
                            "Field " + rename.getOldName() + " of " + name + " was renamed to " + rename.getNewName() + ".",
                            name, usage, reportIncomplete));
                } else if (detail instanceof SchemaDifference.InputObjectFieldTypeModification typeMod) {
                    GraphQLInputObjectType fromType = (GraphQLInputObjectType) fromSchema.getType(name);
                    GraphQLInputObjectType toType = (GraphQLInputObjectType) toSchema.getType(name);
                    GraphQLInputType oldType = fromType.getFieldDefinition(typeMod.getFieldName()).getType();
                    GraphQLInputType newType = toType.getFieldDefinition(typeMod.getFieldName()).getType();
                    boolean safeLoosening = isSafeInputPositionLoosening(oldType, newType);
                    changes.add(typeChange(ImpactReport.Category.FIELD_TYPE_CHANGED,
                            safeLoosening ? ImpactReport.Severity.NON_BREAKING : ImpactReport.Severity.BREAKING,
                            "Field " + typeMod.getFieldName() + " of " + name + " changed type from "
                                    + typeMod.getOldType() + " to " + typeMod.getNewType() + "." + safeLoosening(safeLoosening),
                            name, usage, reportIncomplete));
                }
            }
        }
    }

    private static void scalar(String name, SchemaDifference.ScalarDifference diff, UsageIndex usage,
            boolean reportIncomplete, List<ImpactReport.SchemaChange> changes) {
        if (diff instanceof SchemaDifference.ScalarAddition) {
            changes.add(typeChange(ImpactReport.Category.TYPE_ADDED, ImpactReport.Severity.NON_BREAKING,
                    name + " was added.", name, usage, reportIncomplete));
        } else if (diff instanceof SchemaDifference.ScalarDeletion) {
            changes.add(typeChange(ImpactReport.Category.TYPE_REMOVED, ImpactReport.Severity.BREAKING,
                    name + " was removed.", name, usage, reportIncomplete));
        }
    }

    private static boolean isRequiredArgument(GraphQLFieldDefinition field, String argumentName) {
        GraphQLArgument argument = field.getArgument(argumentName);
        return argument != null && GraphQLTypeUtil.isNonNull(argument.getType()) && !argument.hasSetDefaultValue();
    }

    /**
     * Whether narrowing an object/interface field's return type from {@code oldType} to
     * {@code newType} is safe for every existing client, following the same rule GraphQL
     * Inspector and Apollo's schema checks apply and graphql-js's {@code findBreakingChanges}
     * codifies as {@code isChangeSafeForObjectOrInterfaceField}: a response value the field could
     * already return under the old type must still be valid under the new one. Wrapping a
     * (possibly already-wrapped) type in non-null is always safe, at any nesting depth, e.g.
     * {@code [String]} to {@code [String]!} or {@code [String!]}: {@code null} was never a value
     * the new type could return anyway, so no existing client's handling of the field stops being
     * correct. Anything else, a genuine named-type change or removing non-null (widening), is not.
     */
    private static boolean isSafeOutputFieldNarrowing(GraphQLType oldType, GraphQLType newType) {
        if (GraphQLTypeUtil.isList(oldType)) {
            return (GraphQLTypeUtil.isList(newType)
                            && isSafeOutputFieldNarrowing(GraphQLTypeUtil.unwrapOne(oldType), GraphQLTypeUtil.unwrapOne(newType)))
                    || (GraphQLTypeUtil.isNonNull(newType)
                            && isSafeOutputFieldNarrowing(oldType, GraphQLTypeUtil.unwrapOne(newType)));
        }
        if (GraphQLTypeUtil.isNonNull(oldType)) {
            return GraphQLTypeUtil.isNonNull(newType)
                    && isSafeOutputFieldNarrowing(GraphQLTypeUtil.unwrapOne(oldType), GraphQLTypeUtil.unwrapOne(newType));
        }
        return (GraphQLTypeUtil.isNotWrapped(newType) && sameNamedType(oldType, newType))
                || (GraphQLTypeUtil.isNonNull(newType) && isSafeOutputFieldNarrowing(oldType, GraphQLTypeUtil.unwrapOne(newType)));
    }

    /**
     * The mirror-image rule for input position (input object fields and field arguments), matching
     * graphql-js's {@code isChangeSafeForInputObjectFieldOrFieldArg}: dropping non-null (loosening a
     * requirement) is safe, since every value an existing caller already supplies remains valid, and
     * the field simply also now accepts omission or {@code null}. Adding non-null (tightening) is
     * not, since a caller that previously omitted the value now fails validation.
     */
    private static boolean isSafeInputPositionLoosening(GraphQLType oldType, GraphQLType newType) {
        if (GraphQLTypeUtil.isList(oldType)) {
            return GraphQLTypeUtil.isList(newType)
                    && isSafeInputPositionLoosening(GraphQLTypeUtil.unwrapOne(oldType), GraphQLTypeUtil.unwrapOne(newType));
        }
        if (GraphQLTypeUtil.isNonNull(oldType)) {
            GraphQLType oldInner = GraphQLTypeUtil.unwrapOne(oldType);
            return (GraphQLTypeUtil.isNonNull(newType) && isSafeInputPositionLoosening(oldInner, GraphQLTypeUtil.unwrapOne(newType)))
                    || (!GraphQLTypeUtil.isNonNull(newType) && isSafeInputPositionLoosening(oldInner, newType));
        }
        return GraphQLTypeUtil.isNotWrapped(newType) && sameNamedType(oldType, newType);
    }

    private static boolean sameNamedType(GraphQLType a, GraphQLType b) {
        return GraphQLTypeUtil.unwrapAll(a).getName().equals(GraphQLTypeUtil.unwrapAll(b).getName());
    }

    private static String safeLoosening(boolean safe) {
        return safe ? " Loosened to nullable; existing callers can still supply a value." : "";
    }

    private static ImpactReport.SchemaChange typeChange(ImpactReport.Category category, ImpactReport.Severity severity,
            String description, String typeName, UsageIndex usage, boolean reportIncomplete) {
        List<OperationRef> usedBy = usage.forType(typeName);
        return new ImpactReport.SchemaChange(severity, category, description, typeName, null, null,
                distinctClients(usedBy), usedBy, ImpactReport.Granularity.TYPE, unconfirmed(usedBy, reportIncomplete));
    }

    private static ImpactReport.SchemaChange fieldChange(ImpactReport.Category category, ImpactReport.Severity severity,
            String description, String typeName, String fieldName, @Nullable String argumentName, UsageIndex usage,
            boolean reportIncomplete) {
        List<OperationRef> usedBy = usage.forField(typeName, fieldName);
        return new ImpactReport.SchemaChange(severity, category, description, typeName, fieldName, argumentName,
                distinctClients(usedBy), usedBy, ImpactReport.Granularity.FIELD, unconfirmed(usedBy, reportIncomplete));
    }

    private static ImpactReport.SchemaChange argumentChange(ImpactReport.Category category, ImpactReport.Severity severity,
            String description, String typeName, String fieldName, String argumentName, UsageIndex usage,
            boolean reportIncomplete) {
        List<OperationRef> usedBy = usage.forArgument(typeName, fieldName, argumentName);
        return new ImpactReport.SchemaChange(severity, category, description, typeName, fieldName, argumentName,
                distinctClients(usedBy), usedBy, ImpactReport.Granularity.ARGUMENT, unconfirmed(usedBy, reportIncomplete));
    }

    /**
     * A zero-usage finding cannot be read as "confirmed no dependency" when the analysis behind it
     * was incomplete: some operation's document could not be fully walked, so it might have
     * referenced exactly this type, field, or argument without that being recorded.
     */
    private static boolean unconfirmed(List<OperationRef> usedBy, boolean reportIncomplete) {
        return usedBy.isEmpty() && reportIncomplete;
    }

    private static int distinctClients(List<OperationRef> usedBy) {
        return (int) usedBy.stream().map(OperationRef::clientName).distinct().count();
    }
}
