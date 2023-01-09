package io.quarkiverse.operatorsdk.samples.pingpong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.StrategyDeploymentPermissions;

class AssertGeneratedResourcesIT {

    @Test
    void verifyPingPongClusterServiceVersion() throws FileNotFoundException {
        KubernetesList list = Serialization
                .unmarshalAsList(new FileInputStream(
                        "target/bundle/pingpong-operator/manifests/pingpong-operator.clusterserviceversion.yaml"));
        assertNotNull(list);
        ClusterServiceVersion csv = findFirst(list, ClusterServiceVersion.class).orElseThrow(IllegalStateException::new);
        // should have only one cluster rule permission
        List<StrategyDeploymentPermissions> clusterPermissions = csv.getSpec().getInstall().getSpec().getClusterPermissions();
        assertEquals(1, clusterPermissions.size());
        List<StrategyDeploymentPermissions> permissions = csv.getSpec().getInstall().getSpec().getPermissions();
        assertEquals(1, permissions.size());
    }

    <T extends HasMetadata> Optional<T> findFirst(KubernetesList list, Class<T> t) {
        return (Optional<T>) list.getItems().stream()
                .filter(i -> t.isInstance(i))
                .findFirst();
    }

}
