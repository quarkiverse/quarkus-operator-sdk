package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfiguration<R extends HasMetadata, P extends HasMetadata> extends
        QuarkusDependentResourceConfiguration<R, P> implements
        KubernetesDependentResourceConfiguration<R, P> {

    private final String labelSelector;
    private final Set<String> namespaces;
    private final boolean owned;
    private ConfigurationService configurationService;

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfiguration(
            String dependentResourceClassName,
            String resourceClassName,
            String labelSelector,
            Set<String> namespaces,
            boolean owned) {
        super(dependentResourceClassName, resourceClassName);
        this.labelSelector = labelSelector;
        this.namespaces = namespaces;
        this.owned = owned;
    }

    @Override
    public String getLabelSelector() {
        return labelSelector;
    }

    @IgnoreProperty
    @Override
    public Set<String> getEffectiveNamespaces() {
        return KubernetesDependentResourceConfiguration.super.getEffectiveNamespaces();
    }

    @Override
    public Set<String> getNamespaces() {
        return namespaces;
    }

    @Override
    public boolean isOwned() {
        return owned;
    }

    @Override
    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    @Override
    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
}
