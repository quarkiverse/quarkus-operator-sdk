package io.quarkiverse.operatorsdk.test;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.test.sources.*;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class ExcludedCRDGenerationTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("test")
            .withApplicationRoot(
                    (jar) -> jar.addClasses(SimpleReconciler.class, SimpleCR.class, SimpleSpec.class, SimpleStatus.class,
                            External.class, LabelAdderCRDPostProcessor.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.generate-all", "true")
            .overrideConfigKey("quarkus.operator-sdk.crd.exclude-resources", SimpleCR.class.getName())
            .overrideConfigKey("quarkus.operator-sdk.crd.post-processor", LabelAdderCRDPostProcessor.class.getName());

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldOnlyGenerateNonExcludedCRDs() {
        // checks that only External CRD is generated
        final var kubernetesDir = Manifests.kubernetesDir(prodModeTestResults);
        Assertions.assertFalse(Files.exists(kubernetesDir.resolve(CustomResource.getCRDName(SimpleCR.class) + "-v1.yml")));
        checkCRD(kubernetesDir.resolve(CustomResource.getCRDName(External.class) + "-v1.yml"));
    }

    private static void checkCRD(Path crdPath) {
        final CustomResourceDefinition crd = Manifests.unmarshall(crdPath);
        Assertions.assertEquals(LabelAdderCRDPostProcessor.LABEL_VALUE,
                crd.getMetadata().getLabels().get(LabelAdderCRDPostProcessor.LABEL_NAME));
    }
}
