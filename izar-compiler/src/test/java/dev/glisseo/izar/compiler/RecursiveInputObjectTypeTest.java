package dev.glisseo.izar.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles generated mutations over a directly self-referential input type ({@code
 * CategoryInput}) and a mutually recursive pair ({@code NodeAInput}/{@code NodeBInput}) with
 * {@code javac}, then drives their builders through reflection. This verifies that
 * that a recursive input type generates a working Java model and builder, distinguishing
 * omitted, explicit-null, and explicit-value states for its own fields exactly like any other
 * generated input builder.
 */
class RecursiveInputObjectTypeTest {

    private Class<?> categoryInputClass;
    private Class<?> nodeAInputClass;
    private Class<?> nodeBInputClass;

    @Test
    void selfReferentialInputTypeBuildsATreeAndEncodesEachLevel(@TempDir Path tempDir) throws Exception {
        load(tempDir);

        Object leaf = invoke(invoke(newBuilder(categoryInputClass), "name", String.class, "Leaf"), "build");
        Object root =
                invoke(
                        invoke(
                                invoke(newBuilder(categoryInputClass), "name", String.class, "Root"),
                                "children",
                                List.class,
                                List.of(leaf)),
                        "build");

        @SuppressWarnings("unchecked")
        Map<String, Object> encodedRoot = (Map<String, Object>) invoke(root, "encode");
        assertThat(encodedRoot).containsEntry("name", "Root");

        @SuppressWarnings("unchecked")
        List<Object> encodedChildren = (List<Object>) encodedRoot.get("children");
        assertThat(encodedChildren).hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> encodedLeaf = (Map<String, Object>) encodedChildren.get(0);
        assertThat(encodedLeaf).containsEntry("name", "Leaf");
        assertThat(encodedLeaf).doesNotContainKey("children");

        // The accessor exposes the self-referential field at the recursive type's own Java type.
        @SuppressWarnings("unchecked")
        List<Object> childrenAccessor = (List<Object>) invoke(root, "children");
        assertThat(childrenAccessor).hasSize(1);
        assertThat(categoryInputClass.isInstance(childrenAccessor.get(0))).isTrue();
    }

    @Test
    void distinguishesOmissionFromExplicitNullOnTheRecursiveField(@TempDir Path tempDir) throws Exception {
        load(tempDir);

        Object untouched = invoke(invoke(newBuilder(categoryInputClass), "name", String.class, "Root"), "build");
        Object explicitNull =
                invoke(
                        invoke(
                                invoke(newBuilder(categoryInputClass), "name", String.class, "Root"),
                                "children",
                                List.class,
                                null),
                        "build");

        assertThat(encodedOf(untouched)).doesNotContainKey("children");
        assertThat(encodedOf(explicitNull)).containsKey("children");
        assertThat(encodedOf(explicitNull).get("children")).isNull();
    }

    @Test
    void mutuallyRecursiveInputTypesEncodeEachOthersReferences(@TempDir Path tempDir) throws Exception {
        load(tempDir);

        Object nodeB = invoke(invoke(newBuilder(nodeBInputClass), "label", String.class, "B"), "build");
        Object nodeA =
                invoke(
                        invoke(
                                invoke(newBuilder(nodeAInputClass), "label", String.class, "A"),
                                "partner",
                                nodeBInputClass,
                                nodeB),
                        "build");

        @SuppressWarnings("unchecked")
        Map<String, Object> encodedA = (Map<String, Object>) invoke(nodeA, "encode");
        assertThat(encodedA).containsEntry("label", "A");

        @SuppressWarnings("unchecked")
        Map<String, Object> encodedB = (Map<String, Object>) encodedA.get("partner");
        assertThat(encodedB).containsEntry("label", "B");
        assertThat(encodedB).doesNotContainKey("partner");

        assertThat(nodeBInputClass.isInstance(invoke(nodeA, "partner"))).isTrue();
    }

    private void load(Path tempDir) throws Exception {
        Path schema = tempDir.resolve("schema.graphqls");
        Files.writeString(schema, Fixtures.RECURSIVE_INPUT_SCHEMA);

        Path saveCategoryOperation = tempDir.resolve("SaveCategory.graphql");
        Files.writeString(saveCategoryOperation, Fixtures.SAVE_CATEGORY_OPERATION);
        Path linkNodesOperation = tempDir.resolve("LinkNodes.graphql");
        Files.writeString(linkNodesOperation, Fixtures.LINK_NODES_OPERATION);

        Path generatedSources = tempDir.resolve("generated-sources");
        new OperationCompiler()
                .generate(
                        List.of(schema),
                        List.of(saveCategoryOperation, linkNodesOperation),
                        generatedSources,
                        "generated.recursive");

        Path generatedPackage = generatedSources.resolve("generated").resolve("recursive");
        Path classesOutput = tempDir.resolve("classes");
        Files.createDirectories(classesOutput);
        compile(
                List.of(
                        generatedPackage.resolve("SaveCategoryMutation.java"),
                        generatedPackage.resolve("LinkNodesMutation.java")),
                classesOutput);

        URLClassLoader loader =
                new URLClassLoader(new URL[] {classesOutput.toUri().toURL()}, getClass().getClassLoader());
        categoryInputClass =
                Class.forName("generated.recursive.SaveCategoryMutation$CategoryInput", true, loader);
        nodeAInputClass = Class.forName("generated.recursive.LinkNodesMutation$NodeAInput", true, loader);
        nodeBInputClass = Class.forName("generated.recursive.LinkNodesMutation$NodeBInput", true, loader);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> encodedOf(Object builtInput) throws Exception {
        return (Map<String, Object>) invoke(builtInput, "encode");
    }

    private static Object newBuilder(Class<?> ownerClass) throws Exception {
        return invoke(ownerClass, "builder");
    }

    private static Object invoke(Class<?> ownerClass, String methodName) throws Exception {
        Method method = ownerClass.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Object invoke(Object receiver, String methodName) throws Exception {
        // encode() is deliberately package-private; loaded through a different ClassLoader than
        // this test, so even same-package access needs an explicit override here.
        Method method = receiver.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(receiver);
    }

    private static Object invoke(Object receiver, String methodName, Class<?> paramType, Object arg)
            throws Exception {
        Method method = receiver.getClass().getDeclaredMethod(methodName, paramType);
        method.setAccessible(true);
        return method.invoke(receiver, arg);
    }

    private static void compile(List<Path> javaFiles, Path classesOutput) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        List<String> args = new java.util.ArrayList<>();
        args.add("-d");
        args.add(classesOutput.toString());
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        for (Path javaFile : javaFiles) {
            args.add(javaFile.toString());
        }
        int result =
                compiler.run(
                        null, new PrintStream(diagnostics), new PrintStream(diagnostics), args.toArray(new String[0]));
        assertThat(result).as("javac output:%n%s", diagnostics).isZero();
    }
}
