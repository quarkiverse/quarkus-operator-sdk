package io.quarkiverse.operatorsdk.bundle;

import java.nio.file.Files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.FirstReconciler;
import io.quarkiverse.operatorsdk.bundle.sources.Second;
import io.quarkiverse.operatorsdk.bundle.sources.SecondReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MultipleOperatorsBundleTest {

    private static final String BUNDLE = "bundle";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, FirstReconciler.class,
                            Second.class, SecondReconciler.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleForTheTwoOperators() {
        Assertions.assertTrue(Files.exists(prodModeTestResults.getBuildDir().resolve(BUNDLE).resolve("first-operator")));
        Assertions.assertTrue(Files.exists(prodModeTestResults.getBuildDir().resolve(BUNDLE).resolve("second-operator")));
    }

}
