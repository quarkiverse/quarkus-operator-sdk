package io.quarkiverse.operatorsdk.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.test.sources.SimpleStatus;
import io.quarkiverse.operatorsdk.test.sources.WatchAllNamespacesReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class HelmChartWatchAllNamespacesGeneratorTest {

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("helm-chart-test-all-namespaces")
            .withApplicationRoot(
                    (jar) -> jar.addClasses(WatchAllNamespacesReconciler.class, SimpleCR.class,
                            SimpleSpec.class, SimpleStatus.class))
            .overrideConfigKey("quarkus.operator-sdk.helm.enabled", "true");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    void generatesHelmChart() throws IOException {
        Path buildDir = prodModeTestResults.getBuildDir();
        var helmDir = new File(buildDir.toFile(), "helm");

        assertThat(new File(helmDir, "Chart.yaml"), FileMatchers.anExistingFile());
        assertThat(new File(helmDir, "values.yaml"), FileMatchers.anExistingFile());

        File templatesDir = new File(helmDir, "templates");

        assertThat(Objects.requireNonNull(templatesDir.listFiles()).length, equalTo(8));

        File kubernetesYml = new File(templatesDir, "kubernetes.yml");
        assertThat(kubernetesYml, FileMatchers.anExistingFile());
        String kubernetesYmlContents = Files.readString(kubernetesYml.toPath());
        HasMetadata resource = (HasMetadata) FileUtils.unmarshalFrom(
                kubernetesYmlContents.getBytes(StandardCharsets.UTF_8));
        assertThat(resource, instanceOf(ClusterRoleBinding.class));
        assertThat(resource.getMetadata().getName(),
                equalTo("all-namespaces-reconciler-cluster-role-binding"));
    }

}
