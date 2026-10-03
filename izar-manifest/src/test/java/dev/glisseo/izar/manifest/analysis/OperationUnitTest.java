package dev.glisseo.izar.manifest.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationUnitTest {
    private static final OperationRef V1 = new OperationRef("op", "GetProfile", "portal", "1.0");
    private static final OperationRef V2 = new OperationRef("op", "GetProfile", "portal", "1.10");
    private static final OperationRef OTHER_CLIENT = new OperationRef("op", "GetProfile", "mobile", "1.0");

    @Test
    void latestAndDedupedCountAnOperationOncePerClientAndKeepTheNewestRelease() {
        for (ReleaseScope scope : List.of(ReleaseScope.LATEST, ReleaseScope.DEDUPED)) {
            assertThat(OperationUnit.representatives(List.of(V1, V2, OTHER_CLIENT), scope))
                    .containsExactly(OTHER_CLIENT, V2);
        }
    }

    @Test
    void allCountsAnOperationOncePerRelease() {
        assertThat(OperationUnit.representatives(List.of(V2, V1, V1, OTHER_CLIENT), ReleaseScope.ALL))
                .containsExactly(OTHER_CLIENT, V1, V2);
    }

    @Test
    void operationNameIsNotPartOfTheUnit() {
        OperationRef renamed = new OperationRef("op", "Renamed", "portal", "1.0");
        assertThat(OperationUnit.representatives(List.of(V1, renamed), ReleaseScope.ALL)).hasSize(1);
    }
}
