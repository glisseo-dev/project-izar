package dev.glisseo.izar.compiler.internal.generate;

import dev.glisseo.izar.compiler.internal.selection.FieldSelection;
import dev.glisseo.izar.compiler.internal.selection.FieldShape;
import dev.glisseo.izar.compiler.internal.selection.ObjectSelection;
import dev.glisseo.izar.compiler.internal.selection.PolymorphicBranch;
import dev.glisseo.izar.compiler.internal.selection.PolymorphicSelection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Indexed object and polymorphic selections shared by the mode-specific renderers. */
record GeneratedSelections(
        List<ObjectSelection> objectSelections,
        List<PolymorphicSelection> polymorphicSelections,
        Map<String, String> implementsInterfaceByTypeName,
        Map<String, PolymorphicSelection> polymorphicByTypeName) {

    static GeneratedSelections from(ObjectSelection root) {
        List<ObjectSelection> objects = new ArrayList<>();
        List<PolymorphicSelection> polymorphic = new ArrayList<>();
        Deque<ObjectSelection> queue = new ArrayDeque<>();
        // Identity, not equals: SelectionAnalyzer reuses one ObjectSelection instance for a
        // polymorphic field's shared object-typed field across every branch (see its
        // nestedFieldCache), so the same node can be reachable from several branches' fields.
        // Only its first arrival here should be walked and rendered.
        Set<ObjectSelection> queued = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(root);
        queued.add(root);
        while (!queue.isEmpty()) {
            ObjectSelection current = queue.removeFirst();
            objects.add(current);
            for (FieldSelection field : current.fields()) {
                enqueueNestedSelections(field.shape(), queue, queued, polymorphic);
            }
        }

        Map<String, String> implementedInterfaces = new LinkedHashMap<>();
        Map<String, PolymorphicSelection> polymorphicByName = new LinkedHashMap<>();
        for (PolymorphicSelection selection : polymorphic) {
            for (PolymorphicBranch branch : selection.branches()) {
                implementedInterfaces.put(branch.selection().javaTypeName(), selection.javaTypeName());
                polymorphicByName.put(branch.selection().javaTypeName(), selection);
            }
            implementedInterfaces.put(selection.unrecognizedBranch().javaTypeName(), selection.javaTypeName());
            polymorphicByName.put(selection.unrecognizedBranch().javaTypeName(), selection);
        }
        return new GeneratedSelections(
                List.copyOf(objects),
                List.copyOf(polymorphic),
                Map.copyOf(implementedInterfaces),
                Map.copyOf(polymorphicByName));
    }

    private static void enqueueNestedSelections(
            FieldShape shape,
            Deque<ObjectSelection> queue,
            Set<ObjectSelection> queued,
            List<PolymorphicSelection> polymorphicSelections) {
        switch (shape) {
            case FieldShape.ObjectField object -> enqueueIfNew(object.selection(), queue, queued);
            case FieldShape.PolymorphicField polymorphic -> {
                polymorphicSelections.add(polymorphic.selection());
                for (PolymorphicBranch branch : polymorphic.selection().branches()) {
                    enqueueIfNew(branch.selection(), queue, queued);
                }
                enqueueIfNew(polymorphic.selection().unrecognizedBranch(), queue, queued);
            }
            case FieldShape.ListField list ->
                    enqueueNestedSelections(list.elementShape(), queue, queued, polymorphicSelections);
            case FieldShape.ScalarField ignored -> {}
            case FieldShape.EnumField ignored -> {}
        }
    }

    private static void enqueueIfNew(ObjectSelection selection, Deque<ObjectSelection> queue, Set<ObjectSelection> queued) {
        if (queued.add(selection)) {
            queue.addLast(selection);
        }
    }
}
