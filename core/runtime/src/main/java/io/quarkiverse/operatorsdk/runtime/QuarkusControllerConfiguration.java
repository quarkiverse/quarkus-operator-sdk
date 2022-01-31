package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.common.ClassUtils.loadClassIfNeeded;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusControllerConfiguration<R extends HasMetadata> implements ControllerConfiguration<R> {

    private final String associatedReconcilerClassName;
    private final String name;
    private final String resourceTypeName;
    private final String crVersion;
    private final boolean generationAware;
    private final boolean registrationDelayed;
    private final String resourceClassName;
    private final Optional<String> specClassName;
    private final Optional<String> statusClassName;
    private List<DependentResourceConfiguration> dependentResources;
    private String finalizer;
    private Set<String> namespaces;
    private RetryConfiguration retryConfiguration;
    private Class<R> resourceClass;
    private String labelSelector;
    private ConfigurationService parent;

    @RecordableConstructor
    public QuarkusControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            String crVersion, boolean generationAware,
            String resourceClassName,
            boolean registrationDelayed, Set<String> namespaces, String finalizer, String labelSelector,
            Optional<String> specClassName, Optional<String> statusClassName,
            List<DependentResourceConfiguration> dependentResources) {
        this.associatedReconcilerClassName = associatedReconcilerClassName;
        this.name = name;
        this.resourceTypeName = resourceTypeName;
        this.crVersion = crVersion;
        this.generationAware = generationAware;
        this.resourceClassName = resourceClassName;
        this.registrationDelayed = registrationDelayed;
        this.retryConfiguration = ControllerConfiguration.super.getRetryConfiguration();
        setNamespaces(namespaces);
        setFinalizer(finalizer);
        this.labelSelector = labelSelector;
        this.specClassName = specClassName;
        this.statusClassName = statusClassName;
        this.dependentResources = dependentResources;
    }

    public static Set<String> asSet(String[] namespaces) {
        return namespaces == null || namespaces.length == 0
                ? Collections.emptySet()
                : Set.of(namespaces);
    }

    public String getResourceClassName() {
        return resourceClassName;
    }

    public boolean isRegistrationDelayed() {
        return registrationDelayed;
    }

    @Override
    @IgnoreProperty
    public Class<R> getResourceClass() {
        return resourceClass = loadClassIfNeeded(resourceClassName, resourceClass);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getResourceTypeName() {
        return resourceTypeName;
    }

    public String getCrVersion() {
        return crVersion;
    }

    @Override
    public String getFinalizer() {
        return finalizer;
    }

    public void setFinalizer(String finalizer) {
        this.finalizer = finalizer != null && !finalizer.isBlank() ? finalizer
                : ReconcilerUtils.getDefaultFinalizerName(resourceTypeName);
    }

    @Override
    public boolean isGenerationAware() {
        return generationAware;
    }

    @Override
    public String getAssociatedReconcilerClassName() {
        return associatedReconcilerClassName;
    }

    @Override
    public Set<String> getNamespaces() {
        return namespaces;
    }

    void setNamespaces(Collection<String> namespaces) {
        this.namespaces = namespaces != null && !namespaces.isEmpty() ? Set.copyOf(namespaces) : Collections.emptySet();
    }

    @Override
    public RetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
    }

    @Override
    public ConfigurationService getConfigurationService() {
        return parent;
    }

    @Override
    public void setConfigurationService(ConfigurationService configurationService) {
        this.parent = configurationService;
        // todo: do we also need to set the configuration service on the dependent resource configs?
    }

    void setRetryConfiguration(RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration != null ? retryConfiguration
                : ControllerConfiguration.super.getRetryConfiguration();
    }

    @IgnoreProperty
    @Override
    public Set<String> getEffectiveNamespaces() {
        return ControllerConfiguration.super.getEffectiveNamespaces();
    }

    @Override
    public String getLabelSelector() {
        return labelSelector;
    }

    public void setLabelSelector(String labelSelector) {
        this.labelSelector = labelSelector;
    }

    public Optional<String> getSpecClassName() {
        return specClassName;
    }

    public Optional<String> getStatusClassName() {
        return statusClassName;
    }

    @Override
    public List<DependentResourceConfiguration> getDependentResources() {
        return dependentResources;
    }
}
