package io.quarkiverse.operatorsdk.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.quarkiverse.operatorsdk.bundle.sources.External;
import io.quarkiverse.operatorsdk.bundle.sources.ExternalDependentResource;
import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.FirstReconciler;
import io.quarkiverse.operatorsdk.bundle.sources.Second;
import io.quarkiverse.operatorsdk.bundle.sources.SecondExternal;
import io.quarkiverse.operatorsdk.bundle.sources.SecondReconciler;
import io.quarkiverse.operatorsdk.bundle.sources.Third;
import io.quarkiverse.operatorsdk.bundle.sources.ThirdReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MultipleOperatorsBundleTest {

    private static final String BUNDLE = "bundle";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, FirstReconciler.class,
                            Second.class, SecondReconciler.class,
                            Third.class, External.class, SecondExternal.class, ThirdReconciler.class,
                            ExternalDependentResource.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.generate-all", "true");

    @SuppressWarnings("unused")
    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleForTheOperators() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(BUNDLE);
        checkBundleFor(bundle, "first-operator", First.class);
        checkBundleFor(bundle, "second-operator", Second.class);

        checkBundleFor(bundle, "third-operator", Third.class);
        final var thirdManifests = bundle.resolve("third-operator").resolve("manifests");
        assertFileExistsIn(thirdManifests.resolve(getCRDNameFor(External.class)), thirdManifests);
        final var csvAsString = Files.readString(thirdManifests.resolve("third-operator.clusterserviceversion.yaml"));
        final var csv = Serialization.unmarshal(csvAsString, ClusterServiceVersion.class);
        assertEquals(HasMetadata.getFullResourceName(Third.class),
                csv.getSpec().getCustomresourcedefinitions().getOwned().get(0).getName());
        assertTrue(csvSpecHasRequired(csv, HasMetadata.getFullResourceName(External.class)));
        assertTrue(csvSpecHasRequired(csv, HasMetadata.getFullResourceName(SecondExternal.class)));
    }

    private static boolean csvSpecHasRequired(ClusterServiceVersion csv, String externalName) {
        return csv.getSpec().getCustomresourcedefinitions().getRequired().stream()
                .anyMatch(desc -> externalName.equals(desc.getName()));
    }

    private void checkBundleFor(Path bundle, String operatorName, Class<? extends HasMetadata> resourceClass) {
        final var operatorManifests = bundle.resolve(operatorName);
        assertFileExistsIn(operatorManifests, bundle);
        assertFileExistsIn(operatorManifests.resolve("bundle.Dockerfile"), bundle);
        final var manifests = operatorManifests.resolve("manifests");
        assertFileExistsIn(manifests, bundle);
        assertFileExistsIn(manifests.resolve(operatorName + ".clusterserviceversion.yaml"), manifests);
        assertFileExistsIn(manifests.resolve(getCRDNameFor(resourceClass)), manifests);
        final var metadata = operatorManifests.resolve("metadata");
        assertFileExistsIn(metadata, bundle);
        assertFileExistsIn(metadata.resolve("annotations.yaml"), metadata);
    }

    private static String getCRDNameFor(Class<? extends HasMetadata> resourceClass) {
        return HasMetadata.getFullResourceName(resourceClass) + "-v1.crd.yml";
    }

    private static void assertFileExistsIn(Path file, Path parent) {
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
