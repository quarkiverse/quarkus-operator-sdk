package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.bundle.deployment.AugmentedResourceInfo;

public class AnnotationsManifestsBuilder extends ManifestsBuilder {

    private static final String METADATA = "metadata";
    private static final String ANNOTATIONS = "annotations";

    private final Map<String, String> bundleLabels;

    public AnnotationsManifestsBuilder(AugmentedResourceInfo cri, Map<String, String> bundleLabels) {
        super(cri);

        this.bundleLabels = bundleLabels;
    }

    @Override
    public Path getFileName() {
        return Path.of(METADATA, ANNOTATIONS + ".yaml");
    }

    @Override
    public byte[] getManifestData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles, List<Deployment> deployments)
            throws IOException {

        return YAML_MAPPER.writeValueAsBytes(Collections.singletonMap(ANNOTATIONS, bundleLabels));
    }
}
