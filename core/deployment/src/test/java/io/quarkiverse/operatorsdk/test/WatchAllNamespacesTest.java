package io.quarkiverse.operatorsdk.test;

import static io.quarkiverse.operatorsdk.annotations.RBACVerbs.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator;
import io.quarkiverse.operatorsdk.deployment.AddRoleBindingsDecorator;
import io.quarkiverse.operatorsdk.test.sources.WatchAllReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class WatchAllNamespacesTest {
    private static final String APPLICATION_NAME = "watch-all-namespaces-test";
    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APPLICATION_NAME)
            .withApplicationRoot((jar) -> jar.addClasses(WatchAllReconciler.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;
    private static final KubernetesSerialization serialization = new KubernetesSerialization();

    @Test
    public void shouldCreateRolesAndRoleBindings() throws IOException {
        final var kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        final var kubeManifest = kubernetesDir.resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));
        final var kubeIS = new FileInputStream(kubeManifest.toFile());
        // use unmarshall version with parameters map to ensure code goes through the proper processing wrt multiple documents
        @SuppressWarnings("unchecked")
        final var kubeResources = (List<HasMetadata>) serialization.unmarshal(kubeIS);

        // check cluster role
        final var clusterRoleName = AddClusterRolesDecorator.getClusterRoleName(WatchAllReconciler.NAME);
        //make sure the target role exists because otherwise the test will succeed without actually checking anything
        assertTrue(kubeResources.stream()
                .anyMatch(i -> clusterRoleName.equals(i.getMetadata().getName())));
        kubeResources.stream()
                .filter(i -> clusterRoleName.equals(i.getMetadata().getName()))
                .map(ClusterRole.class::cast)
                .forEach(cr -> {
                    final var rules = cr.getRules();
                    assertEquals(1, rules.size());

                    var rule = rules.get(0);
                    assertEquals(List.of(HasMetadata.getGroup(ConfigMap.class)), rule.getApiGroups());
                    final var resources = rule.getResources();
                    final var plural = HasMetadata.getPlural(ConfigMap.class);
                    // status is void so shouldn't be present in resources
                    assertEquals(List.of(plural, plural + "/finalizers"), resources);
                    assertEquals(Arrays.asList(ALL_COMMON_VERBS), rule.getVerbs());
                });

        // check that we have a cluster role binding that is mapped to the proper ClusterRole
        final var clusterRoleBindingName = AddRoleBindingsDecorator.getClusterRoleBindingName(WatchAllReconciler.NAME);
        assertTrue(kubeResources.stream().anyMatch(i -> clusterRoleBindingName.equals(i.getMetadata().getName())));
        kubeResources.stream()
                .filter(i -> clusterRoleBindingName.equals(i.getMetadata().getName()))
                .map(ClusterRoleBinding.class::cast)
                .forEach(rb -> assertEquals(clusterRoleName, rb.getRoleRef().getName()));
    }
}
