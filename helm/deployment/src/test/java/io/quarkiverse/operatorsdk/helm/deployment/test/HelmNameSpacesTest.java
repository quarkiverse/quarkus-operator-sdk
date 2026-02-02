package io.quarkiverse.operatorsdk.helm.deployment.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleStatus;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SpacedReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class HelmNameSpacesTest {

    private static final String APP_NAME = "helm-chart-test";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APP_NAME)
            .withApplicationRoot(
                    (jar) -> jar.addClasses(SpacedReconciler.class, SimpleCR.class, SimpleSpec.class, SimpleStatus.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    void generatesHelmChart() throws IOException {
        Path buildDir = prodModeTestResults.getBuildDir();
        var helmDir = buildDir.resolve("helm")
                .resolve("kubernetes")
                .resolve(APP_NAME).toFile();

        assertThat(new File(helmDir, "Chart.yaml"), FileMatchers.anExistingFile());
        assertThat(new File(helmDir, "values.yaml"), FileMatchers.anExistingFile());
        var templates = new File(helmDir, "templates");
        assertThat(Objects.requireNonNull(templates.listFiles()).length,
                greaterThanOrEqualTo(7));
        String crdRoleBindingYamlContent = Files
                .readString(templates.toPath().resolve("name-with-spaces-crd-role-binding.yaml"));
        assertThat(crdRoleBindingYamlContent, containsString("QUARKUS_OPERATOR_SDK_CONTROLLERS_NAME_WITH_SPACES_NAMESPACES"));
    }

}
