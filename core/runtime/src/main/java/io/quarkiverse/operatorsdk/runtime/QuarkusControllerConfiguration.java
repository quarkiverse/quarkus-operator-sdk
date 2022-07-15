package io.quarkiverse.operatorsdk.runtime;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusControllerConfiguration<R extends HasMetadata> implements ControllerConfiguration<R> {

    // we need to create this class because Quarkus cannot reference the default implementation that
    // JOSDK provides as it doesn't like lambdas at build time. The class also needs to be public
    // because otherwise Quarkus isn't able to access it… :(
    public final static class PassthroughResourceEventFilter implements ResourceEventFilter {

        @Override
        public boolean acceptChange(Controller controller, HasMetadata hasMetadata,
                HasMetadata p1) {
            return false;
        }
    }

    private final static ResourceEventFilter DEFAULT = new PassthroughResourceEventFilter();

    private final String associatedReconcilerClassName;
    private final String name;
    private final String resourceTypeName;
    private final String crVersion;
    private final boolean generationAware;
    private final boolean statusPresentAndNotVoid;
    private final List<DependentResourceSpec> dependentResources;
    private final Class<R> resourceClass;
    private final ResourceEventFilter<R> eventFilter;
    private final Optional<Duration> maxReconciliationInterval;
    private String finalizer;
    private Set<String> namespaces;
    private RetryConfiguration retryConfiguration;
    private String labelSelector;

    @RecordableConstructor
    @SuppressWarnings("unchecked")
    public QuarkusControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            String crVersion, boolean generationAware,
            Class<R> resourceClass, Set<String> namespaces, String finalizerName, String labelSelector,
            boolean statusPresentAndNotVoid,
            List<DependentResourceSpec> dependentResources, ResourceEventFilter<R> eventFilter,
            Duration maxReconciliationInterval) {
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
        this.eventFilter = eventFilter != null ? eventFilter : DEFAULT;
        this.maxReconciliationInterval = maxReconciliationInterval != null ? Optional.of(maxReconciliationInterval)
                : ControllerConfiguration.super.maxReconciliationInterval();
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

    @Override
    public Retry getRetry() {
        return ControllerConfiguration.super.getRetry();
    }

    @Override
    public RateLimiter getRateLimiter() {
        return ControllerConfiguration.super.getRateLimiter();
    }

    @Override
    public ResourceEventFilter<R> getEventFilter() {
        return eventFilter;
    }

    public Optional<Duration> maxReconciliationInterval() {
        return maxReconciliationInterval;
    }

    // for Quarkus' RecordableConstructor
    public Duration getMaxReconciliationInterval() {
        return maxReconciliationInterval.orElseThrow();
    }
}
