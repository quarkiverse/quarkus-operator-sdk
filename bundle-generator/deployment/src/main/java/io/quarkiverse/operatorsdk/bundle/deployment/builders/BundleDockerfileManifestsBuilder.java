package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.bundle.deployment.AugmentedResourceInfo;

public class BundleDockerfileManifestsBuilder extends ManifestsBuilder {

    private static final String DOCKERFILE = "bundle.Dockerfile";

    private final Map<String, String> bundleLabels;

    public BundleDockerfileManifestsBuilder(AugmentedResourceInfo cri, Map<String, String> bundleLabels) {
        super(cri);

        this.bundleLabels = bundleLabels;
    }

    @Override
    public Path getFileName() {
        return Path.of(DOCKERFILE);
    }

    @Override
    public byte[] getYAMLData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles, List<Deployment> deployments) {
        StringBuilder sb = new StringBuilder();
        sb.append("FROM scratch" + System.lineSeparator());

        for (Map.Entry<String, String> label : bundleLabels.entrySet()) {
            sb.append("LABEL " + label.getKey() + "=" + label.getValue() + System.lineSeparator());
        }

        sb.append("COPY manifests /manifests/" + System.lineSeparator());
        sb.append("COPY metadata /metadata/" + System.lineSeparator());

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
