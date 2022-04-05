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
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;

public class BundleDockerfileManifestsBuilder extends ManifestsBuilder {

    private static final String DOCKERFILE = "bundle.Dockerfile";

    private final Map<String, String> bundleLabels;

    public BundleDockerfileManifestsBuilder(CSVMetadataHolder metadata, Map<String, String> bundleLabels) {
        super(metadata);

        this.bundleLabels = bundleLabels;
    }

    @Override
    public Path getFileName() {
        return Path.of(DOCKERFILE);
    }

    @Override
    public byte[] getManifestData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles, List<Deployment> deployments) {
        StringBuilder sb = new StringBuilder();
        final var lineSeparator = System.lineSeparator();
        sb.append("FROM scratch").append(lineSeparator);

        for (Map.Entry<String, String> label : bundleLabels.entrySet()) {
            sb.append("LABEL ").append(label.getKey()).append("=").append(label.getValue())
                    .append(lineSeparator);
        }

        sb.append("COPY manifests /manifests/").append(lineSeparator);
        sb.append("COPY metadata /metadata/").append(lineSeparator);

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getManifestType() {
        return "bundle";
    }
}
