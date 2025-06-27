package io.quarkiverse.operatorsdk.test;

import static io.quarkiverse.operatorsdk.deployment.helm.HelmChartProcessor.ADDITIONAL_CRD_ROLE_BINDING_YAML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.test.sources.SimpleReconciler;
import io.quarkiverse.operatorsdk.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.test.sources.SimpleStatus;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class HelmChartGeneratorTest {

    private static final String APP_NAME = "helm-chart-test";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APP_NAME)
            .withApplicationRoot(
                    (jar) -> jar.addClasses(SimpleReconciler.class, SimpleCR.class, SimpleSpec.class, SimpleStatus.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    void generatesHelmChart() {
        Path buildDir = prodModeTestResults.getBuildDir();
        var helmDir = buildDir.resolve("helm")
                .resolve("kubernetes")
                .resolve(APP_NAME).toFile();

        assertThat(new File(helmDir, "Chart.yaml"), FileMatchers.anExistingFile());
        assertThat(new File(helmDir, "values.yaml"), FileMatchers.anExistingFile());
        assertThat(Objects.requireNonNull(new File(helmDir, "templates").listFiles()).length,
                greaterThanOrEqualTo(7));
        assertThat(new File(helmDir, "templates/" + ADDITIONAL_CRD_ROLE_BINDING_YAML), FileMatchers.anExistingFile());
    }

}
