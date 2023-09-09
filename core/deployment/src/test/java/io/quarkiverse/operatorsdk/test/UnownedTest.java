package io.quarkiverse.operatorsdk.test;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.test.sources.*;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class UnownedTest {
    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(UnownedReconciler.NAME)
            .withApplicationRoot(
                    (jar) -> jar.addClasses(UnownedReconciler.class, TestCR.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.generate-all", "true")
            .overrideConfigKey("quarkus.operator-sdk.controllers." + UnownedReconciler.NAME + ".unowned-primary", "true");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldNotGenerateUnownedPrimary() throws IOException {
        final var kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        final var kubeManifest = kubernetesDir.resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));

        // checks that no CRD has been generated
        Assertions.assertFalse(Files.exists(kubernetesDir.resolve(CustomResource.getCRDName(TestCR.class) + "-v1.yml")));
    }
}
