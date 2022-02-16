package io.quarkiverse.operatorsdk.csv.deployment.builders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.csv.deployment.AugmentedResourceInfo;

public class RbacManifestsBuilder extends ManifestsBuilder {

    private static final String SEPARATION = "---";

    private final String csvGroupName;

    public RbacManifestsBuilder(AugmentedResourceInfo cri) {
        super(cri);
        csvGroupName = cri.getCsvGroupName();
    }

    public String getFileName() {
        return csvGroupName + ".csv.rbac.yml";
    }

    public byte[] getYAMLData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            List<Deployment> deployments) {

        StringBuilder sb = new StringBuilder();
        serviceAccounts.forEach(sa -> writeValueAsString(sb, sa));
        clusterRoleBindings.forEach(crb -> writeValueAsString(sb, crb));
        clusterRoles.forEach(cr -> writeValueAsString(sb, cr));
        roleBindings.forEach(rb -> writeValueAsString(sb, rb));
        roles.forEach(r -> writeValueAsString(sb, r));

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void writeValueAsString(StringBuilder sb, HasMetadata object) {
        try {
            object.getMetadata().setNamespace(null);
            sb.append(SEPARATION + System.lineSeparator());
            sb.append(YAML_MAPPER.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
