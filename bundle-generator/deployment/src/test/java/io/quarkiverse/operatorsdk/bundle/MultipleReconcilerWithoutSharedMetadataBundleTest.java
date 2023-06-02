package io.quarkiverse.operatorsdk.bundle;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.sources.DuplicatedBundleNameWithoutSharedCSVMetadata1;
import io.quarkiverse.operatorsdk.bundle.sources.DuplicatedBundleNameWithoutSharedCSVMetadata2;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MultipleReconcilerWithoutSharedMetadataBundleTest {

    public static final String APPLICATION_NAME = "incorrect-metadata-sharing";
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .withApplicationRoot((jar) -> jar.addClasses(
                    DuplicatedBundleNameWithoutSharedCSVMetadata1.class, DuplicatedBundleNameWithoutSharedCSVMetadata2.class))
            .setApplicationName(APPLICATION_NAME)
            .assertBuildException(e -> {
                // exception should be a runtime exception wrapping a build exception wrapping our own exception
                assertInstanceOf(IllegalStateException.class, e.getCause().getCause());
                assertTrue(e.getMessage().contains(DuplicatedBundleNameWithoutSharedCSVMetadata1.class.getName()));
                assertTrue(e.getMessage()
                        .contains(DuplicatedBundleNameWithoutSharedCSVMetadata2.class.getName()));
            });

    @SuppressWarnings("unused")
    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleForTheOperators() {
        fail("Should have failed at build time");
    }
}
