package io.quarkiverse.operatorsdk.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusControllerConfiguration<R extends HasMetadata> implements ControllerConfiguration<R> {

    private final String associatedReconcilerClassName;
    private final String name;
    private final String resourceTypeName;
    private final String crVersion;
    private final boolean generationAware;
    private final boolean statusPresentAndNotVoid;
    private final List<DependentResourceSpec> dependentResources;
    private final Class<R> resourceClass;
    private String finalizer;
    private Set<String> namespaces;
    private RetryConfiguration retryConfiguration;
    private String labelSelector;

    @RecordableConstructor
    public QuarkusControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            String crVersion, boolean generationAware,
            Class<R> resourceClass, Set<String> namespaces, String finalizerName, String labelSelector,
            boolean statusPresentAndNotVoid,
            List<DependentResourceSpec> dependentResources) {
        this.associatedReconcilerClassName = associatedReconcilerClassName;
        this.name = name;
        this.resourceTypeName = resourceTypeName;
        this.crVersion = crVersion;
        this.generationAware = generationAware;
        this.resourceClass = resourceClass;
        this.retryConfiguration = ControllerConfiguration.super.getRetryConfiguration();
        setNamespaces(namespaces);
        setFinalizer(finalizerName);
        this.labelSelector = labelSelector;
        this.statusPresentAndNotVoid = statusPresentAndNotVoid;
        this.dependentResources = dependentResources;
    }

    @Override
    public Class<R> getResourceClass() {
        return resourceClass;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getResourceTypeName() {
        return resourceTypeName;
    }

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public String getCrVersion() {
        return crVersion;
    }

    @Override
    public String getFinalizerName() {
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
        this.namespaces = namespaces != null && !namespaces.isEmpty() ? Set.copyOf(namespaces)
                : Constants.DEFAULT_NAMESPACES_SET;
    }

    @Override
    public RetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
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

    public boolean isStatusPresentAndNotVoid() {
        return statusPresentAndNotVoid;
    }

    @Override
    public List<DependentResourceSpec> getDependentResources() {
        return dependentResources;
    }
}
