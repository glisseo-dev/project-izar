package dev.glisseo.izar.manifest.assembly;

import dev.glisseo.izar.manifest.ManifestOperation;
import dev.glisseo.izar.manifest.OperationManifest;
import dev.glisseo.izar.manifest.source.ManifestSources;
import dev.glisseo.izar.manifest.source.ManifestSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseAssemblerTest {

    private static final String GET_BOOK_DOC = "query GetBook { book { title } }";
    private static final String GET_AUTHOR_DOC = "query GetAuthor { author { name } }";
    private static final String GET_REVIEW_DOC = "query GetReview { review { text } }";
    private static final String GET_INVENTORY_DOC = "query GetInventory { inventory { count } }";

    private static final ManifestOperation GET_BOOK = ManifestOperation.of("GetBook", "query", GET_BOOK_DOC);
    private static final ManifestOperation GET_AUTHOR = ManifestOperation.of("GetAuthor", "query", GET_AUTHOR_DOC);
    private static final ManifestOperation GET_REVIEW = ManifestOperation.of("GetReview", "query", GET_REVIEW_DOC);
    private static final ManifestOperation GET_INVENTORY =
            ManifestOperation.of("GetInventory", "query", GET_INVENTORY_DOC);

    private static final ClientRelease WEB_APP_1_2_0 = new ClientRelease("web-app", "1.2.0");
    private static final ClientRelease WEB_APP_1_3_0 = new ClientRelease("web-app", "1.3.0");
    private static final ClientRelease BATCH_JOB = new ClientRelease("batch-job", "2026.1");

    @Test
    void unionsSharedAndReleaseSpecificOperationsWithFullProvenance(@TempDir Path dir) {
        ReleaseSelection selection = new ReleaseSelection(
                "nightsky",
                "production",
                List.of(
                        selected(WEB_APP_1_2_0, dir, GET_BOOK, GET_AUTHOR),
                        selected(WEB_APP_1_3_0, dir, GET_BOOK, GET_REVIEW),
                        selected(BATCH_JOB, dir, GET_BOOK, GET_INVENTORY)));

        AssembledManifest assembled = new ReleaseAssembler().assemble(selection);

        assertThat(assembled.manifest().operations())
                .extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor", "GetReview", "GetInventory");

        assertThat(provenanceFor(assembled, GET_BOOK.id())).containsExactly(BATCH_JOB, WEB_APP_1_2_0, WEB_APP_1_3_0);
        assertThat(provenanceFor(assembled, GET_AUTHOR.id())).containsExactly(WEB_APP_1_2_0);
        assertThat(provenanceFor(assembled, GET_REVIEW.id())).containsExactly(WEB_APP_1_3_0);
        assertThat(provenanceFor(assembled, GET_INVENTORY.id())).containsExactly(BATCH_JOB);

        assertThat(assembled.lock().graphId()).isEqualTo("nightsky");
        assertThat(assembled.lock().environment()).isEqualTo("production");
        assertThat(assembled.lock().releases())
                .extracting(LockedRelease::release)
                .containsExactly(BATCH_JOB, WEB_APP_1_2_0, WEB_APP_1_3_0);
    }

    @Test
    void identicalInputsProduceIdenticalOutputRegardlessOfSelectionOrder(@TempDir Path dir) {
        ReleaseSelection firstOrder = new ReleaseSelection(
                "nightsky",
                "production",
                List.of(selected(WEB_APP_1_2_0, dir, GET_BOOK, GET_AUTHOR), selected(BATCH_JOB, dir, GET_BOOK, GET_INVENTORY)));
        ReleaseSelection reversedOrder = new ReleaseSelection(
                "nightsky",
                "production",
                List.of(selected(BATCH_JOB, dir, GET_BOOK, GET_INVENTORY), selected(WEB_APP_1_2_0, dir, GET_BOOK, GET_AUTHOR)));

        AssembledManifest first = new ReleaseAssembler().assemble(firstOrder);
        AssembledManifest second = new ReleaseAssembler().assemble(reversedOrder);

        assertThat(second.manifest().toJson()).isEqualTo(first.manifest().toJson());
        assertThat(second.lock().toJson()).isEqualTo(first.lock().toJson());
        assertThat(second.provenance()).isEqualTo(first.provenance());
    }

    @Test
    void addingAReleasePreservesOlderSelectedReleases(@TempDir Path dir) {
        SelectedRelease older = selected(WEB_APP_1_2_0, dir, GET_BOOK, GET_AUTHOR);
        SelectedRelease newer = selected(WEB_APP_1_3_0, dir, GET_BOOK, GET_REVIEW);

        AssembledManifest beforeRollout =
                new ReleaseAssembler().assemble(new ReleaseSelection("nightsky", "production", List.of(older)));
        AssembledManifest afterRollout = new ReleaseAssembler()
                .assemble(new ReleaseSelection("nightsky", "production", List.of(older, newer)));

        assertThat(beforeRollout.manifest().operations()).extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor");
        assertThat(afterRollout.manifest().operations()).extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor", "GetReview");
    }

    @Test
    void explicitRetirementRemovesOnlyOperationsNoRemainingReleaseNeeds(@TempDir Path dir) {
        SelectedRelease retiring = selected(WEB_APP_1_2_0, dir, GET_BOOK, GET_AUTHOR);
        SelectedRelease staying = selected(WEB_APP_1_3_0, dir, GET_BOOK, GET_REVIEW);

        AssembledManifest beforeRetirement = new ReleaseAssembler()
                .assemble(new ReleaseSelection("nightsky", "production", List.of(retiring, staying)));
        AssembledManifest afterRetirement =
                new ReleaseAssembler().assemble(new ReleaseSelection("nightsky", "production", List.of(staying)));

        assertThat(beforeRetirement.manifest().operations()).extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetAuthor", "GetReview");
        // GetAuthor was only required by the retired 1.2.0 release; GetBook is still required by 1.3.0.
        assertThat(afterRetirement.manifest().operations()).extracting(ManifestOperation::name)
                .containsExactlyInAnyOrder("GetBook", "GetReview");
    }

    @Test
    void anInfrequentlyExecutedClientStaysIncludedWithoutAnyTrafficConcept(@TempDir Path dir) {
        // Assembly has no notion of traffic or execution activity at all: a release named in the
        // selection is included purely because it was selected, regardless of how often it runs.
        SelectedRelease twiceYearlyBatchJob = selected(BATCH_JOB, dir, GET_INVENTORY);

        AssembledManifest assembled = new ReleaseAssembler()
                .assemble(new ReleaseSelection("nightsky", "production", List.of(twiceYearlyBatchJob)));

        assertThat(assembled.manifest().operations()).extracting(ManifestOperation::name)
                .containsExactly("GetInventory");
    }

    @Test
    void deduplicatesTheSameReleaseSelectedTwiceWithIdenticalContent(@TempDir Path dir) {
        SelectedRelease first = selected(WEB_APP_1_2_0, dir, GET_BOOK);
        Path sameFile = dir.resolve(sourceFileName(first));
        SelectedRelease duplicate = new SelectedRelease(WEB_APP_1_2_0, ManifestSources.file(sameFile));

        AssembledManifest assembled = new ReleaseAssembler()
                .assemble(new ReleaseSelection("nightsky", "production", List.of(first, duplicate)));

        assertThat(assembled.lock().releases()).hasSize(1);
    }

    @Test
    void failsOnAMissingRelease(@TempDir Path dir) {
        ManifestSource missing = ManifestSources.file(dir.resolve("does-not-exist.json"));
        ReleaseSelection selection = new ReleaseSelection(
                "nightsky", "production", List.of(new SelectedRelease(WEB_APP_1_2_0, missing)));

        assertThatThrownBy(() -> new ReleaseAssembler().assemble(selection))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("web-app@1.2.0");
    }

    @Test
    void reportsEveryMissingReleaseNotJustTheFirst(@TempDir Path dir) {
        ManifestSource missingOne = ManifestSources.file(dir.resolve("missing-1.json"));
        ManifestSource missingTwo = ManifestSources.file(dir.resolve("missing-2.json"));
        ReleaseSelection selection = new ReleaseSelection(
                "nightsky",
                "production",
                List.of(
                        new SelectedRelease(WEB_APP_1_2_0, missingOne),
                        new SelectedRelease(BATCH_JOB, missingTwo)));

        assertThatThrownBy(() -> new ReleaseAssembler().assemble(selection))
                .hasMessageContaining("web-app@1.2.0")
                .hasMessageContaining("batch-job@2026.1");
    }

    @Test
    void failsWhenAReleaseIdentityIsReusedWithDifferentContent(@TempDir Path dir) {
        SelectedRelease firstContent = selected(WEB_APP_1_2_0, dir, "a.json", GET_BOOK);
        SelectedRelease conflictingContent = selected(WEB_APP_1_2_0, dir, "b.json", GET_AUTHOR);

        ReleaseSelection selection = new ReleaseSelection(
                "nightsky", "production", List.of(firstContent, conflictingContent));

        assertThatThrownBy(() -> new ReleaseAssembler().assemble(selection))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("web-app@1.2.0")
                .hasMessageContaining("different content");
    }

    @Test
    void failsOnUnsupportedManifestFormat(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("web-app.json");
        Files.writeString(file, "not a manifest at all");
        SelectedRelease invalid = new SelectedRelease(WEB_APP_1_2_0, ManifestSources.file(file));

        assertThatThrownBy(() -> new ReleaseAssembler()
                        .assemble(new ReleaseSelection("nightsky", "production", List.of(invalid))))
                .isInstanceOf(AssemblyException.class);
    }

    @Test
    void failsOnAHashMismatch(@TempDir Path dir) throws IOException {
        String tampered =
                """
                {
                  "format" : "apollo-persisted-query-manifest",
                  "version" : 1,
                  "operations" : [ {
                    "id" : "0000000000000000000000000000000000000000000000000000000000000000",
                    "body" : "query GetBook { book { title } }",
                    "name" : "GetBook",
                    "type" : "query"
                  } ]
                }
                """;
        Path file = dir.resolve("web-app.json");
        Files.writeString(file, tampered);
        SelectedRelease invalid = new SelectedRelease(WEB_APP_1_2_0, ManifestSources.file(file));

        assertThatThrownBy(() -> new ReleaseAssembler()
                        .assemble(new ReleaseSelection("nightsky", "production", List.of(invalid))))
                .isInstanceOf(AssemblyException.class);
    }

    @Test
    void failsWhenTwoReleasesDisagreeAboutOneOperationIdsMetadata(@TempDir Path dir) {
        // Same document, so the same id, but declared under two different names: this can only
        // happen if two releases' manifests disagree about what one operation ID actually is.
        ManifestOperation asGetBook = ManifestOperation.of("GetBook", "query", GET_BOOK_DOC);
        ManifestOperation asFetchBook = ManifestOperation.of("FetchBook", "query", GET_BOOK_DOC);

        SelectedRelease first = selected(WEB_APP_1_2_0, dir, "a.json", asGetBook);
        SelectedRelease second = selected(BATCH_JOB, dir, "b.json", asFetchBook);

        ReleaseSelection selection = new ReleaseSelection("nightsky", "production", List.of(first, second));

        assertThatThrownBy(() -> new ReleaseAssembler().assemble(selection))
                .isInstanceOf(AssemblyException.class)
                .hasMessageContaining("conflicting entries");
    }

    private static List<ClientRelease> provenanceFor(AssembledManifest assembled, String operationId) {
        return assembled.provenance().stream()
                .filter(p -> p.operationId().equals(operationId))
                .flatMap(p -> p.releases().stream())
                .collect(Collectors.toList());
    }

    private static SelectedRelease selected(ClientRelease release, Path dir, ManifestOperation... operations) {
        return selected(release, dir, release.clientName() + "-" + release.manifestVersion() + ".json", operations);
    }

    private static SelectedRelease selected(
            ClientRelease release, Path dir, String fileName, ManifestOperation... operations) {
        Path file = dir.resolve(fileName);
        try {
            Files.writeString(file, OperationManifest.of(List.of(operations)).toJson());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new SelectedRelease(release, ManifestSources.file(file));
    }

    private static String sourceFileName(SelectedRelease release) {
        return release.release().clientName() + "-" + release.release().manifestVersion() + ".json";
    }
}
