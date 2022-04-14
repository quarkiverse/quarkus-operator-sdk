package io.quarkiverse.operatorsdk.bundle;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.FirstReconciler;
import io.quarkiverse.operatorsdk.bundle.sources.Second;
import io.quarkiverse.operatorsdk.bundle.sources.SecondReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

@Disabled
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
        final var bundle = prodModeTestResults.getBuildDir().resolve(BUNDLE);
        checkBundleFor(bundle, "first-operator", First.class);
        checkBundleFor(bundle, "second-operator", Second.class);
    }

    private void checkBundleFor(Path bundle, String operatorName, Class<? extends HasMetadata> resourceClass) {
        final var operatorManifests = bundle.resolve(operatorName);
        Assertions.assertTrue(Files.exists(operatorManifests));
        Assertions.assertTrue(Files.exists(operatorManifests.resolve("bundle.Dockerfile")));
        final var manifests = operatorManifests.resolve("manifests");
        Assertions.assertTrue(Files.exists(manifests));
        Assertions.assertTrue(Files.exists(manifests.resolve(operatorName + ".csv.yml")));
        Assertions.assertTrue(Files.exists(manifests.resolve(HasMetadata.getFullResourceName(
                resourceClass) + "-v1.crd.yml")));
        final var metadata = operatorManifests.resolve("metadata");
        Assertions.assertTrue(Files.exists(metadata));
        Assertions.assertTrue(Files.exists(metadata.resolve("annotations.yaml")));
    }

}
