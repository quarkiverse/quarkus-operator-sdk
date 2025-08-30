package io.quarkiverse.operatorsdk.samples.pingpong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.StrategyDeploymentPermissions;

class AssertGeneratedResourcesIT {

    @Test
    void verifyPingPongClusterServiceVersion() {
        try (final var client = new KubernetesClientBuilder().build()) {
            final var csv = client.getKubernetesSerialization().unmarshal(new FileInputStream(
                    "target/bundle/pingpong-operator/manifests/pingpong-operator.clusterserviceversion.yaml"),
                    ClusterServiceVersion.class);
            // should have only one cluster rule permission
            List<StrategyDeploymentPermissions> clusterPermissions = csv.getSpec().getInstall().getSpec()
                    .getClusterPermissions();
            assertEquals(1, clusterPermissions.size());
            List<StrategyDeploymentPermissions> permissions = csv.getSpec().getInstall().getSpec().getPermissions();
            assertEquals(1, permissions.size());
        } catch (Exception e) {
            fail(e);
        }
    }
}
