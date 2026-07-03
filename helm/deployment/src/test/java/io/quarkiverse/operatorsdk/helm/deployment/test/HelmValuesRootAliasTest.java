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
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleReconciler;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleStatus;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class HelmValuesRootAliasTest {

    private static final String APP_NAME = "helm-chart-test";
    private static final String CUSTOM_ALIAS = "myapp";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APP_NAME)
            .withApplicationRoot(
                    (jar) -> jar.addClasses(SimpleReconciler.class, SimpleCR.class, SimpleSpec.class, SimpleStatus.class))
            .overrideConfigKey("quarkus.helm.values-root-alias", CUSTOM_ALIAS);

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    void crdRoleBindingUsesCustomValuesRootAlias() throws IOException {
        Path buildDir = prodModeTestResults.getBuildDir();
        var templatesDir = buildDir.resolve("helm")
                .resolve("kubernetes")
                .resolve(APP_NAME)
                .resolve("templates");

        String crdRoleBindingContent = Files.readString(templatesDir.resolve("simple-crd-role-binding.yaml"));
        assertThat(crdRoleBindingContent, containsString("$.Values." + CUSTOM_ALIAS + ".envs."));
        assertThat(crdRoleBindingContent, not(containsString("$.Values.app.envs.")));
    }
}
