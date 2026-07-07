package io.quarkiverse.operatorsdk.helm.deployment.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleStatus;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.WatchCurrentNamespaceReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

/**
 * Verifies that the base RoleBinding/ClusterRoleBinding derived from the plain generated Kubernetes manifests is not
 * duplicated as an additional Helm template.
 */
class HelmChartNoDuplicateRoleBindingTest {

    private static final String APP_NAME = "helm-chart-test-no-duplicate-role-binding";
    private static final String ROLE_BINDING_NAME = WatchCurrentNamespaceReconciler.NAME + "-role-binding";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APP_NAME)
            .withApplicationRoot(
                    (jar) -> jar.addClasses(WatchCurrentNamespaceReconciler.class, SimpleCR.class, SimpleSpec.class,
                            SimpleStatus.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    void baseRoleBindingIsExcludedInFavorOfTheConditionalTemplate() throws IOException {
        Path templatesDir = prodModeTestResults.getBuildDir().resolve("helm")
                .resolve("kubernetes")
                .resolve(APP_NAME)
                .resolve("templates");

        Path baseRoleBindingFile = templatesDir.resolve("rolebinding.yaml");
        if (Files.exists(baseRoleBindingFile)) {
            assertThat(Files.readString(baseRoleBindingFile), not(containsString(ROLE_BINDING_NAME)));
        }

        Path conditionalTemplateFile = templatesDir
                .resolve(WatchCurrentNamespaceReconciler.NAME + "-crd-role-binding.yaml");
        assertThat(Files.readString(conditionalTemplateFile), containsString(ROLE_BINDING_NAME));
    }

}
