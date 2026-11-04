package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

public abstract class ManifestsBuilder {

    protected static final ObjectMapper YAML_MAPPER;

    static {
        YAML_MAPPER = YAMLMapper.builder(YAMLFactory.builder().enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER).build())
                .configureForJackson2()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .changeDefaultPropertyInclusion(inclusion -> JsonInclude.Value.construct(
                        JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY))
                .build();
    }

    protected final CSVMetadataHolder metadata;

    public ManifestsBuilder(CSVMetadataHolder metadata) {
        this.metadata = metadata;
    }

    public abstract Path getFileName();

    public abstract byte[] getManifestData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            List<Deployment> deployments) throws IOException;

    public abstract String getManifestType();

    public String getName() {
        return metadata.bundleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ManifestsBuilder that = (ManifestsBuilder) o;

        return getFileName().equals(that.getFileName());
    }

    @Override
    public int hashCode() {
        return getFileName().hashCode();
    }
}
