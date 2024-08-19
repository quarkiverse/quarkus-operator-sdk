package io.quarkiverse.operatorsdk.bundle;

import static io.quarkiverse.operatorsdk.bundle.Utils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.quarkiverse.operatorsdk.bundle.sources.*;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class MultipleOperatorsBundleTest {

    private static final String VERSION = "test-version";
    public static final String BUNDLE_PACKAGE = "olm-package";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationVersion(VERSION)
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, FirstReconciler.class,
                            Second.class, SecondReconciler.class,
                            Third.class, External.class, SecondExternal.class, ThirdReconciler.class,
                            ExternalDependentResource.class, PodDependentResource.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.generate-all", "true")
            .overrideConfigKey("quarkus.operator-sdk.bundle.replaces", FirstReconciler.REPLACES)
            .overrideConfigKey("quarkus.operator-sdk.bundle.package-name", BUNDLE_PACKAGE);

    @SuppressWarnings("unused")
    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleForTheOperators() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(Utils.BUNDLE);
        checkBundleFor(bundle, "first-operator", First.class);
        // check that version is properly overridden
        var csv = getCSVFor(bundle, "first-operator");
        assertEquals(FirstReconciler.VERSION, csv.getSpec().getVersion());
        assertEquals(FirstReconciler.REPLACES, csv.getSpec().getReplaces());
        var bundleMeta = getAnnotationsFor(bundle, "first-operator");
        assertEquals(BUNDLE_PACKAGE, bundleMeta.getAnnotations().get("operators.operatorframework.io.bundle.package.v1"));

        checkBundleFor(bundle, "second-operator", Second.class);
        csv = getCSVFor(bundle, "second-operator");
        final var permissions = csv.getSpec().getInstall().getSpec().getPermissions();
        assertEquals(1, permissions.size());
        assertTrue(permissions.get(0).getRules().contains(new PolicyRuleBuilder()
                .addToApiGroups(SecondReconciler.RBAC_RULE_GROUP)
                .addToResources(SecondReconciler.RBAC_RULE_RES)
                .addToVerbs(SecondReconciler.RBAC_RULE_VERBS)
                .build()));

        checkBundleFor(bundle, "third-operator", Third.class);
        // also check that external CRD is present
        final var thirdManifests = bundle.resolve("third-operator").resolve("manifests");
        assertFileExistsIn(thirdManifests.resolve(getCRDNameFor(External.class)), thirdManifests);

        csv = getCSVFor(bundle, "third-operator");
        final var crds = csv.getSpec().getCustomresourcedefinitions();
        final var thirdCRD = crds.getOwned().get(0);
        assertEquals(HasMetadata.getFullResourceName(Third.class), thirdCRD.getName());
        assertEquals(Third.DISPLAY, thirdCRD.getDisplayName());
        assertEquals(Third.DESCRIPTION, thirdCRD.getDescription());
        // CRDs should be alphabetically ordered
        final var externalCRD = crds.getRequired().get(0);
        assertEquals(HasMetadata.getFullResourceName(External.class), externalCRD.getName());
        assertEquals(External.DISPLAY_NAME, externalCRD.getDisplayName());
        assertEquals(External.DESCRIPTION, externalCRD.getDescription());
        assertEquals(HasMetadata.getFullResourceName(SecondExternal.class), crds.getRequired().get(1).getName());
        // should list native APIs as well
        final var spec = csv.getSpec();
        final var nativeAPIs = spec.getNativeAPIs();
        assertEquals(1, nativeAPIs.size());
        final var podGVK = nativeAPIs.get(0);
        assertEquals(HasMetadata.getGroup(Pod.class), podGVK.getGroup());
        assertEquals(HasMetadata.getKind(Pod.class), podGVK.getKind());
        assertEquals(HasMetadata.getVersion(Pod.class), podGVK.getVersion());
        assertEquals("1.0.0", spec.getReplaces());
        final var metadata = csv.getMetadata();
        assertEquals(">=1.0.0 <1.0.3", metadata.getAnnotations().get("olm.skipRange"));
        assertEquals("Test", metadata.getAnnotations().get("capabilities"));
        assertEquals("bar", metadata.getAnnotations().get("foo"));
        // version should be the default application's version since it's not provided for this reconciler
        assertEquals(VERSION, spec.getVersion());

        // check that the env variable to set the reconciler namespaces is properly set

        //disabled because of https://github.com/quarkusio/quarkus/issues/36041
        final var firstContainer = spec.getInstall().getSpec().getDeployments().get(0).getSpec().getTemplate().getSpec()
                .getContainers().get(0);
        assertTrue(firstContainer.getEnv().stream()
                .anyMatch(envVar -> envVar.getName()
                        .equals(ConfigurationUtils.getNamespacesPropertyName(ThirdReconciler.NAME, true))));

    }
}
