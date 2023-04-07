package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;

public class BundleDockerfileManifestsBuilder extends ManifestsBuilder {

    private static final String DOCKERFILE = "bundle.Dockerfile";
    private static final String LABEL = "LABEL ";
    private static final char EQUALS = '=';
    private static final String PREFIX = "FROM scratch\n\n# Core bundle labels.\n";
    private static final String SUFFIX = "\n# Copy files to locations specified by labels.\nCOPY manifests /manifests/\nCOPY metadata /metadata/\n";
    private static final char LINE_BREAK = '\n';

    private final SortedMap<String, String> bundleLabels;

    public BundleDockerfileManifestsBuilder(CSVMetadataHolder metadata, SortedMap<String, String> bundleLabels) {
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
        final var sb = new StringBuilder(1024);

        sb.append(PREFIX);
        bundleLabels.forEach((key, value) -> sb.append(LABEL).append(key).append(EQUALS).append(value).append(LINE_BREAK));
        sb.append(SUFFIX);

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String getManifestType() {
        return "bundle";
    }
}
