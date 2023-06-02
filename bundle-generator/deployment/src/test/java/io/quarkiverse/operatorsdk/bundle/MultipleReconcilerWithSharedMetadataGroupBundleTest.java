package io.quarkiverse.operatorsdk.bundle;

import static io.quarkiverse.operatorsdk.bundle.Utils.checkBundleFor;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.sources.AReconciler;
import io.quarkiverse.operatorsdk.bundle.sources.BReconciler;
import io.quarkiverse.operatorsdk.bundle.sources.CReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MultipleReconcilerWithSharedMetadataGroupBundleTest {

    private static final String BUNDLE = "bundle";

    public static final String APPLICATION_NAME = "application-name";
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .withApplicationRoot((jar) -> jar.addClasses(AReconciler.class, BReconciler.class, CReconciler.class))
            .setApplicationName(APPLICATION_NAME);

    @SuppressWarnings("unused")
    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleForTheOperators() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(BUNDLE);
        checkBundleFor(bundle, "shared", null);
        // reconcilers with no csv metadata should use the application name
        checkBundleFor(bundle, APPLICATION_NAME, null);
    }
}
