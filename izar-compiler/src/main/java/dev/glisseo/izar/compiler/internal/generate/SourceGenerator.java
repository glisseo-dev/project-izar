package dev.glisseo.izar.compiler.internal.generate;

import dev.glisseo.izar.compiler.GenerationMode;
import dev.glisseo.izar.compiler.internal.selection.FragmentInterface;
import dev.glisseo.izar.compiler.internal.selection.ObjectSelection;
import dev.glisseo.izar.compiler.internal.selection.OutputEnumType;
import dev.glisseo.izar.compiler.internal.variable.VariableAnalyzer;
import dev.glisseo.izar.operation.GraphQlOperationKind;
import java.util.List;

/** Dispatches each generation mode to its own source renderer. */
public final class SourceGenerator {

    private SourceGenerator() {}

    public static String generateFragmentInterface(
            String packageName, FragmentInterface fragmentInterface, GenerationMode generationMode) {
        return switch (generationMode) {
            case IZAR -> IzarSourceGenerator.generateFragmentInterface(packageName, fragmentInterface);
            case JACKSON -> JacksonSourceGenerator.generateFragmentInterface(packageName, fragmentInterface);
        };
    }

    public static String generate(
            String packageName,
            String className,
            String operationName,
            String documentText,
            String operationId,
            GraphQlOperationKind operationKind,
            ObjectSelection root,
            List<OutputEnumType> outputEnumTypes,
            VariableAnalyzer.Result variables,
            GenerationMode generationMode) {
        return switch (generationMode) {
            case IZAR -> IzarSourceGenerator.generate(
                    packageName,
                    className,
                    operationName,
                    documentText,
                    operationId,
                    operationKind,
                    root,
                    outputEnumTypes,
                    variables);
            case JACKSON -> JacksonSourceGenerator.generate(
                    packageName,
                    className,
                    operationName,
                    documentText,
                    operationId,
                    operationKind,
                    root,
                    variables);
        };
    }
}
