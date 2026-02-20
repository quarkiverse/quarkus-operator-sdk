package io.quarkiverse.operatorsdk.helm.deployment.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.hamcrest.io.FileMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.SimpleStatus;
import io.quarkiverse.operatorsdk.helm.deployment.test.sources.WatchAllNamespacesReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

class HelmChartWatchAllNamespacesGeneratorTest {

    private static final String APP_NAME = "helm-chart-test-all-namespaces";

    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APP_NAME)
            .withApplicationRoot(
                    (jar) -> jar.addClasses(WatchAllNamespacesReconciler.class, SimpleCR.class,
                            SimpleSpec.class, SimpleStatus.class))
            // Generate some custom resources to test that they get properly included in the Helm chart
            // ServiceAccount
            .overrideConfigKey("quarkus.kubernetes.rbac.service-accounts.service-account-custom.namespace", "custom-ns")
            // Role
            .overrideConfigKey("quarkus.kubernetes.rbac.roles.role-custom.policy-rules.0.api-groups", "extensions,apps")
            .overrideConfigKey("quarkus.kubernetes.rbac.roles.role-custom.policy-rules.0.resources", "deployments")
            .overrideConfigKey("quarkus.kubernetes.rbac.roles.role-custom.policy-rules.0.verbs", "get,watch,list")
            // RoleBinding
            .overrideConfigKey("quarkus.kubernetes.rbac.role-bindings.role-binding-custom.subjects.service-account-custom.kind",
                    "ServiceAccount")
            .overrideConfigKey(
                    "quarkus.kubernetes.rbac.role-bindings.role-binding-custom.subjects.service-account-custom.namespace",
                    "custom-ns")
            .overrideConfigKey("quarkus.kubernetes.rbac.role-bindings.role-binding-custom.role-name", "role-custom")
            // ClusterRole
            .overrideConfigKey("quarkus.kubernetes.rbac.cluster-roles.cluster-role-custom.policy-rules.0.api-groups",
                    "extensions,apps")
            .overrideConfigKey("quarkus.kubernetes.rbac.cluster-roles.cluster-role-custom.policy-rules.0.resources",
                    "deployments")
            .overrideConfigKey("quarkus.kubernetes.rbac.cluster-roles.cluster-role-custom.policy-rules.0.verbs",
                    "get,watch,list")
            // ClusterRoleBinding
            .overrideConfigKey(
                    "quarkus.kubernetes.rbac.cluster-role-bindings.cluster-role-binding-custom.subjects.manager.kind", "Group")
            .overrideConfigKey(
                    "quarkus.kubernetes.rbac.cluster-role-bindings.cluster-role-binding-custom.subjects.manager.api-group",
                    "rbac.authorization.k8s.io")
            .overrideConfigKey("quarkus.kubernetes.rbac.cluster-role-bindings.cluster-role-binding-custom.role-name",
                    "custom-cluster-role");

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

        File templatesDir = new File(helmDir, "templates");

        assertThat(Objects.requireNonNull(templatesDir.listFiles()).length, equalTo(10));

        File kubernetesYml = prodModeTestResults.getBuildDir().resolve("kubernetes").resolve("kubernetes.yml").toFile();
        assertThat(kubernetesYml, FileMatchers.anExistingFile());
        String kubernetesYmlContents = Files.readString(kubernetesYml.toPath());
        @SuppressWarnings("unchecked")
        List<HasMetadata> resource = (List<HasMetadata>) FileUtils.unmarshalFrom(
                kubernetesYmlContents.getBytes(StandardCharsets.UTF_8));
        assertThat(resource, hasSize(11));
        assertThat(resource, containsInAnyOrder(
                hasProperty("kind", equalTo("ServiceAccount")),
                hasProperty("kind", equalTo("ClusterRole")),
                hasProperty("kind", equalTo("ClusterRole")),
                hasProperty("kind", equalTo("ClusterRole")),
                hasProperty("kind", equalTo("ClusterRoleBinding")),
                hasProperty("kind", equalTo("ClusterRoleBinding")),
                hasProperty("kind", equalTo("ClusterRoleBinding")),
                hasProperty("kind", equalTo("Role")),
                hasProperty("kind", equalTo("RoleBinding")),
                hasProperty("kind", equalTo("Service")),
                hasProperty("kind", equalTo("Deployment"))));
    }

}
