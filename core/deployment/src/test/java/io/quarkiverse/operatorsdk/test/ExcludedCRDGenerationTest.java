package io.quarkiverse.operatorsdk.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
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
    public void shouldOnlyGenerateNonExcludedCRDs() throws IOException {
        final var kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        final var kubeManifest = kubernetesDir.resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));

        // checks that only External CRD is generated
        Assertions.assertFalse(Files.exists(kubernetesDir.resolve(CustomResource.getCRDName(SimpleCR.class) + "-v1.yml")));
        checkCRD(kubernetesDir.resolve(CustomResource.getCRDName(External.class) + "-v1.yml"));
    }

    private static void checkCRD(Path crdPath) throws FileNotFoundException {
        final KubernetesSerialization serializer = new KubernetesSerialization();
        Assertions.assertTrue(Files.exists(crdPath));
        final var crdIS = new FileInputStream(crdPath.toFile());
        final var crd = (CustomResourceDefinition) serializer.unmarshal(crdIS);
        Assertions.assertEquals(LabelAdderCRDPostProcessor.LABEL_VALUE,
                crd.getMetadata().getLabels().get(LabelAdderCRDPostProcessor.LABEL_NAME));
    }
}
