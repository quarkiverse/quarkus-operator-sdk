package io.quarkiverse.operatorsdk.bundle;

import java.nio.file.Files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.ReconcilerWithNoCsvMetadata;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class DefaultBundleWhenNoCsvMetadataTest {

    private static final String BUNDLE = "bundle";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("reconciler-with-no-csv-metadata")
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, ReconcilerWithNoCsvMetadata.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleEvenWhenCsvMetadataIsNotUsed() {
        Assertions.assertTrue(
                Files.exists(prodModeTestResults.getBuildDir().resolve(BUNDLE).resolve("reconciler-with-no-csv-metadata")));
    }

}
