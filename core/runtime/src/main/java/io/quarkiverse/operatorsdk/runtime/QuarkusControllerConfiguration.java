package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.common.ClassUtils.loadClass;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusControllerConfiguration<R extends HasMetadata> implements ControllerConfiguration<R> {
    // we need to create this class because Quarkus cannot reference the default implementation that
    // JOSDK provides as it doesn't like lambdas at build time. The class also needs to be public
    // because otherwise Quarkus isn't able to access it… :(
    public final static class PassthroughResourceEventFilter implements ResourceEventFilter {
        @Override
        public boolean acceptChange(ControllerConfiguration controllerConfiguration,
                HasMetadata hasMetadata, HasMetadata t1) {
            return true;
        }
    }

    private final static ResourceEventFilter DEFAULT = new PassthroughResourceEventFilter();

    private final String associatedReconcilerClassName;
    private final String name;
    private final String resourceTypeName;
    private final String crVersion;
    private final boolean generationAware;
    private final boolean registrationDelayed;
    private final String resourceClassName;
    private final Optional<String> specClassName;
    private final Optional<String> statusClassName;
    private final ResourceEventFilter<R> eventFilter;
    private final Optional<Duration> maxReconciliationInterval;
    private String finalizer;
    private Set<String> namespaces;
    private RetryConfiguration retryConfiguration;
    private Class<R> resourceClass;
    private String labelSelector;
    private ConfigurationService parent;

    @RecordableConstructor
    @SuppressWarnings("unchecked")
    public QuarkusControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            String crVersion, boolean generationAware,
            String resourceClassName,
            boolean registrationDelayed, Set<String> namespaces, String finalizer, String labelSelector,
            Optional<String> specClassName, Optional<String> statusClassName,
            ResourceEventFilter<R> eventFilter, Duration maxReconciliationInterval) {
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
        this.eventFilter = eventFilter != null ? eventFilter : DEFAULT;
        this.maxReconciliationInterval = maxReconciliationInterval != null ? Optional.of(maxReconciliationInterval)
                : ControllerConfiguration.super.reconciliationMaxInterval();
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
        if (resourceClass == null) {
            resourceClass = (Class<R>) loadClass(resourceClassName);
        }
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
    public ResourceEventFilter<R> getEventFilter() {
        return eventFilter;
    }

    @Override
    public Optional<Duration> reconciliationMaxInterval() {
        return maxReconciliationInterval();
    }

    public Optional<Duration> maxReconciliationInterval() {
        return maxReconciliationInterval;
    }

    // for Quarkus' RecordableConstructor
    public Duration getMaxReconciliationInterval() {
        return maxReconciliationInterval.orElseThrow();
    }
}
