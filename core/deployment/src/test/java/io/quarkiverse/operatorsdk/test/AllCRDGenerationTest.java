package io.quarkiverse.operatorsdk.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.quarkiverse.operatorsdk.test.sources.*;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class AllCRDGenerationTest {

    public static final String V1_CRD_SUFFIX = "-v1.yml";
    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("test")
            .withApplicationRoot(
                    (jar) -> jar.addClasses(SimpleCR.class, SimpleSpec.class, SimpleStatus.class,
                            External.class, ExternalV1.class, SimpleCRV2.class, SimpleReconcilerV2.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.generate-all", "true");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    private static final KubernetesSerialization serializer = new KubernetesSerialization();

    @Test
    public void shouldGenerateAllCRDs() throws IOException {
        final var kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        final var kubeManifest = kubernetesDir.resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));

        // checks that CRDs are all generated
        Assertions.assertEquals(2, Files.list(kubernetesDir)
                .filter(path -> path.getFileName().toFile().getName().endsWith(V1_CRD_SUFFIX))
                .count());

        checkCRD(kubernetesDir.resolve(getV1CRDFileName(SimpleCR.class)), SimpleCR.VERSION, SimpleCRV2.VERSION);
        checkCRD(kubernetesDir.resolve(getV1CRDFileName(External.class)), External.VERSION, ExternalV1.VERSION);
    }

    private static void checkCRD(Path crdPath, String version1, String version2) throws FileNotFoundException {
        Assertions.assertTrue(Files.exists(crdPath));
        final var simpleCRDIS = new FileInputStream(crdPath.toFile());
        final var simpleCRD = (CustomResourceDefinition) serializer.unmarshal(simpleCRDIS);
        final List<CustomResourceDefinitionVersion> versions = simpleCRD.getSpec().getVersions().stream().toList();
        Assertions.assertEquals(2, versions.size());
        Assertions.assertTrue(versions.stream().anyMatch(v -> v.getName().equals(version1)));
        Assertions.assertTrue(versions.stream().anyMatch(v -> v.getName().equals(version2)));
    }

    private static String getV1CRDFileName(Class<? extends CustomResource<?, ?>> crClass) {
        return CustomResource.getCRDName(crClass) + V1_CRD_SUFFIX;
    }
}
