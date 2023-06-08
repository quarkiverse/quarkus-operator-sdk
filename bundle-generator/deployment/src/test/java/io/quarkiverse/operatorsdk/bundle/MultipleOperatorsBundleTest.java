package io.quarkiverse.operatorsdk.bundle;

import static io.quarkiverse.operatorsdk.bundle.Utils.assertFileExistsIn;
import static io.quarkiverse.operatorsdk.bundle.Utils.checkBundleFor;
import static io.quarkiverse.operatorsdk.bundle.Utils.getCRDNameFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.quarkiverse.operatorsdk.bundle.sources.*;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MultipleOperatorsBundleTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, FirstReconciler.class,
                            Second.class, SecondReconciler.class,
                            Third.class, External.class, SecondExternal.class, ThirdReconciler.class,
                            ExternalDependentResource.class, PodDependentResource.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.generate-all", "true");

    @SuppressWarnings("unused")
    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleForTheOperators() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(Utils.BUNDLE);
        checkBundleFor(bundle, "first-operator", First.class);
        checkBundleFor(bundle, "second-operator", Second.class);

        checkBundleFor(bundle, "third-operator", Third.class);
        final var thirdManifests = bundle.resolve("third-operator").resolve("manifests");
        assertFileExistsIn(thirdManifests.resolve(getCRDNameFor(External.class)), thirdManifests);
        final var csvAsString = Files.readString(thirdManifests.resolve("third-operator.clusterserviceversion.yaml"));
        final var csv = Serialization.unmarshal(csvAsString, ClusterServiceVersion.class);
        final var crds = csv.getSpec().getCustomresourcedefinitions();
        assertEquals(HasMetadata.getFullResourceName(Third.class), crds.getOwned().get(0).getName());
        // CRDs should be alphabetically ordered
        assertEquals(HasMetadata.getFullResourceName(External.class), crds.getRequired().get(0).getName());
        assertEquals(HasMetadata.getFullResourceName(SecondExternal.class), crds.getRequired().get(1).getName());
        // should list native APIs as well
        final var podGVK = csv.getSpec().getNativeAPIs().get(0);
        assertEquals(HasMetadata.getGroup(Pod.class), podGVK.getGroup());
        assertEquals(HasMetadata.getKind(Pod.class), podGVK.getKind());
        assertEquals(HasMetadata.getVersion(Pod.class), podGVK.getVersion());
        assertEquals("1.0.0", csv.getSpec().getReplaces());
        assertEquals(">=1.0.0 <1.0.3", csv.getMetadata().getAnnotations().get("olm.skipRange"));
        assertEquals("Test", csv.getMetadata().getAnnotations().get("capabilities"));
        assertEquals("bar", csv.getMetadata().getAnnotations().get("foo"));
    }
}
