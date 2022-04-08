package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;

public class CustomResourceManifestsBuilder extends ManifestsBuilder {

    private static final String MANIFESTS = "manifests";

    private final CRDInfo crd;

    public CustomResourceManifestsBuilder(CSVMetadataHolder metadata, CRDInfo crd) {
        super(metadata);

        this.crd = crd;
    }

    @Override
    public Path getFileName() {
        return Path.of(MANIFESTS, crd.getCrdName());
    }

    @Override
    public byte[] getManifestData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles, List<Deployment> deployments)
            throws IOException {
        return FileUtils.readFileToByteArray(new File(crd.getFilePath()));
    }

    @Override
    public String getManifestType() {
        return "Custom Resource Definition";
    }
}
