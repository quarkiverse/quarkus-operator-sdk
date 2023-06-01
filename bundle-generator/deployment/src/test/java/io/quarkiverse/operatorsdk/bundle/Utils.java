package io.quarkiverse.operatorsdk.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class Utils {

    static void checkBundleFor(Path bundle, String operatorName,
            Class<? extends HasMetadata> resourceClass) {
        final var operatorManifests = bundle.resolve(operatorName);
        assertFileExistsIn(operatorManifests, bundle);
        assertFileExistsIn(operatorManifests.resolve("bundle.Dockerfile"), bundle);
        final var manifests = operatorManifests.resolve("manifests");
        assertFileExistsIn(manifests, bundle);
        assertFileExistsIn(manifests.resolve(operatorName + ".clusterserviceversion.yaml"), manifests);
        if (resourceClass != null) {
            assertFileExistsIn(manifests.resolve(getCRDNameFor(resourceClass)), manifests);
        }
        final var metadata = operatorManifests.resolve("metadata");
        assertFileExistsIn(metadata, bundle);
        assertFileExistsIn(metadata.resolve("annotations.yaml"), metadata);
    }

    static String getCRDNameFor(Class<? extends HasMetadata> resourceClass) {
        return HasMetadata.getFullResourceName(resourceClass) + "-v1.crd.yml";
    }

    static void assertFileExistsIn(Path file, Path parent) {
        final var exists = Files.exists(file);
        if (!exists) {
            System.out.println("Couldn't find " + file.getFileName() + " in " + parent);
            System.out.println("Known files: ");
            try (final var list = Files.list(parent)) {
                list.forEach(f -> System.out.println("\t" + f));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        Assertions.assertTrue(exists);
    }

}
