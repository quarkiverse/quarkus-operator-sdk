package io.quarkiverse.operatorsdk.test;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assertions;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.quarkus.test.ProdModeTestResults;

class Manifests {
    private static final KubernetesSerialization serializer = new KubernetesSerialization();

    static Path kubernetesDir(ProdModeTestResults prodModeTestResults) {
        return prodModeTestResults.getBuildDir().resolve("kubernetes");
    }

    static List<? extends HasMetadata> unmarshallManifest(ProdModeTestResults prodModeTestResults) {
        final var kubernetesDir = kubernetesDir(prodModeTestResults);
        final var kubeManifest = kubernetesDir.resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));
        return unmarshall(kubeManifest);
    }

    static <T> T unmarshall(Path path) {
        try (final var kubeIS = new FileInputStream(path.toFile())) {
            return serializer.unmarshal(kubeIS);
        } catch (IOException e) {
            Assertions.fail(e);
            throw new RuntimeException(e);
        }
    }
}
