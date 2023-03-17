package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusControllerConfiguration<R extends HasMetadata> implements ControllerConfiguration<R>,
        DependentResourceConfigurationProvider {
    // we need to create this class because Quarkus cannot reference the default implementation that
    // JOSDK provides as it doesn't like lambdas at build time. The class also needs to be public
    // because otherwise Quarkus isn't able to access it… :(
    public final static class PassthroughResourceEventFilter implements ResourceEventFilter {

        @Override
        public boolean acceptChange(Controller controller, HasMetadata hasMetadata,
                HasMetadata p1) {
            return true;
        }
    }

    private final static ResourceEventFilter DEFAULT = new PassthroughResourceEventFilter();

    // Needed by Quarkus because LinearRateLimiter doesn't expose setters for byte recording
    public final static class DefaultRateLimiter extends LinearRateLimiter {

        public DefaultRateLimiter() {
            super();
        }

        @RecordableConstructor
        @SuppressWarnings("unused")
        public DefaultRateLimiter(Duration refreshPeriod, int limitForPeriod) {
            super(refreshPeriod, limitForPeriod);
        }
    }

    private final String associatedReconcilerClassName;
    private final String name;
    private final String resourceTypeName;
    private final String crVersion;
    private final boolean generationAware;
    private final boolean statusPresentAndNotVoid;
    private final Class<R> resourceClass;
    private final ResourceEventFilter<R> eventFilter;
    private final Optional<Duration> maxReconciliationInterval;
    private final Optional<OnAddFilter<R>> onAddFilter;
    private final Optional<OnUpdateFilter<R>> onUpdateFilter;
    private final Optional<GenericFilter<R>> genericFilter;
    private Class<? extends Annotation> retryConfigurationClass;
    private Class<? extends Retry> retryClass;
    private Class<? extends Annotation> rateLimiterConfigurationClass;
    private Class<? extends RateLimiter> rateLimiterClass;
    private String finalizer;
    private Set<String> namespaces;
    private boolean wereNamespacesSet;
    private RetryConfiguration retryConfiguration;
    private String labelSelector;
    private final Map<String, DependentResourceSpecMetadata<?, ?, ?>> dependentsMetadata;
    @IgnoreProperty
    private boolean namespaceExpansionRequired;
    private Retry retry;
    private RateLimiter rateLimiter;
    private ManagedWorkflow<R> workflow;

    @RecordableConstructor
    @SuppressWarnings("unchecked")
    public QuarkusControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            String crVersion, boolean generationAware,
            Class resourceClass, Set<String> namespaces,
            boolean wereNamespacesSet,
            String finalizerName, String labelSelector,
            boolean statusPresentAndNotVoid, ResourceEventFilter eventFilter,
            Duration maxReconciliationInterval,
            OnAddFilter<R> onAddFilter, OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter,
            Class<? extends Retry> retryClass, Class<? extends Annotation> retryConfigurationClass,
            Class<? extends RateLimiter> rateLimiterClass, Class<? extends Annotation> rateLimiterConfigurationClass,
            Map<String, DependentResourceSpecMetadata<?, ?, ?>> dependentsMetadata,
            ManagedWorkflow<R> workflow) {
        this.associatedReconcilerClassName = associatedReconcilerClassName;
        this.name = name;
        this.resourceTypeName = resourceTypeName;
        this.crVersion = crVersion;
        this.generationAware = generationAware;
        this.resourceClass = resourceClass;
        this.dependentsMetadata = dependentsMetadata;
        this.workflow = workflow;
        this.retryConfiguration = ControllerConfiguration.super.getRetryConfiguration();
        setNamespaces(namespaces);
        this.wereNamespacesSet = wereNamespacesSet;
        setFinalizer(finalizerName);
        this.labelSelector = labelSelector;
        this.statusPresentAndNotVoid = statusPresentAndNotVoid;
        this.eventFilter = eventFilter != null ? eventFilter : DEFAULT;
        this.maxReconciliationInterval = maxReconciliationInterval != null ? Optional.of(maxReconciliationInterval)
                : ControllerConfiguration.super.maxReconciliationInterval();
        this.onAddFilter = Optional.ofNullable(onAddFilter);
        this.onUpdateFilter = Optional.ofNullable(onUpdateFilter);
        this.genericFilter = Optional.ofNullable(genericFilter);

        this.retryClass = retryClass;
        this.retry = GenericRetry.class.equals(retryClass) ? ControllerConfiguration.super.getRetry() : null;
        this.retryConfigurationClass = retryConfigurationClass;

        this.rateLimiterClass = rateLimiterClass;
        this.rateLimiter = DefaultRateLimiter.class.equals(rateLimiterClass) ? new DefaultRateLimiter() : null;
        this.rateLimiterConfigurationClass = rateLimiterConfigurationClass;
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
        if (namespaces != null && !namespaces.isEmpty()) {
            this.namespaces = Set.copyOf(namespaces);
            namespaceExpansionRequired = namespaces.stream().anyMatch(ns -> ns.contains("${"));
            wereNamespacesSet = true;
        } else {
            this.namespaces = Constants.DEFAULT_NAMESPACES_SET;
            namespaceExpansionRequired = false;
        }
    }

    public boolean isNamespaceExpansionRequired() {
        return namespaceExpansionRequired;
    }

    public boolean isWereNamespacesSet() {
        return wereNamespacesSet;
    }

    @Override
    public RetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
    }

    void setRetryConfiguration(RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration != null ? retryConfiguration
                : ControllerConfiguration.super.getRetryConfiguration();
        // reset retry if needed
        if (retry == null || retry instanceof GenericRetry) {
            retry = GenericRetry.fromConfiguration(retryConfiguration);
        }
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

    public boolean areDependentsImpactedBy(Set<String> changedClasses) {
        return dependentsMetadata.keySet().parallelStream().anyMatch(changedClasses::contains);
    }

    public boolean needsDependentBeansCreation() {
        return dependentsMetadata != null && !dependentsMetadata.isEmpty();
    }

    public ManagedWorkflow<R> getWorkflow() {
        return workflow;
    }

    public void setWorkflow(ManagedWorkflow<R> workflow) {
        this.workflow = workflow;
    }

    @Override
    public Object getConfigurationFor(DependentResourceSpec dependentResourceSpec) {
        return ((DependentResourceSpecMetadata) dependentResourceSpec).getDependentResourceConfig();
    }

    @Override
    public List<DependentResourceSpec> getDependentResources() {
        return dependentsMetadata.values().parallelStream().collect(Collectors.toList());
    }

    @Override
    public Retry getRetry() {
        return retry;
    }

    @Override
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    @Override
    public ResourceEventFilter<R> getEventFilter() {
        return eventFilter;
    }

    public Optional<Duration> maxReconciliationInterval() {
        return maxReconciliationInterval;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Duration getMaxReconciliationInterval() {
        return maxReconciliationInterval.orElseThrow();
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnAddFilter<R> getOnAddFilter() {
        return onAddFilter.orElse(null);
    }

    @Override
    public Optional<OnAddFilter<R>> onAddFilter() {
        return onAddFilter;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnUpdateFilter<R> getOnUpdateFilter() {
        return onUpdateFilter.orElse(null);
    }

    @Override
    public Optional<OnUpdateFilter<R>> onUpdateFilter() {
        return onUpdateFilter;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public GenericFilter<R> getGenericFilter() {
        return genericFilter.orElse(null);
    }

    @Override
    public Optional<GenericFilter<R>> genericFilter() {
        return genericFilter;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Class<? extends Retry> getRetryClass() {
        return retryClass;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Class<? extends RateLimiter> getRateLimiterClass() {
        return rateLimiterClass;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Map<String, DependentResourceSpecMetadata<?, ?, ?>> getDependentsMetadata() {
        return dependentsMetadata;
    }

    void initAnnotationConfigurables(Reconciler<R> reconciler) {
        final Class<? extends Reconciler> reconcilerClass = reconciler.getClass();
        if (retryConfigurationClass != null) {
            if (retry == null) {
                retry = ClassLoadingUtils.instantiate(retryClass);
            }
            configure(reconcilerClass, retryConfigurationClass, (AnnotationConfigurable) retry);
            retryClass = null;
            retryConfigurationClass = null;
        }

        if (rateLimiterClass != null) {
            if (rateLimiter == null) {
                rateLimiter = ClassLoadingUtils.instantiate(rateLimiterClass);
            }
            configure(reconcilerClass, rateLimiterConfigurationClass, (AnnotationConfigurable) rateLimiter);
            rateLimiterClass = null;
            rateLimiterConfigurationClass = null;
        }
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public Class<? extends Annotation> getRetryConfigurationClass() {
        return retryConfigurationClass;
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public Class<? extends Annotation> getRateLimiterConfigurationClass() {
        return rateLimiterConfigurationClass;
    }

    @SuppressWarnings("unchecked")
    private void configure(Class<? extends Reconciler> reconcilerClass, Class<? extends Annotation> configurationClass,
            AnnotationConfigurable configurable) {
        if (configurationClass != null) {
            var annotation = reconcilerClass.getAnnotation(configurationClass);
            if (annotation != null) {
                configurable.initFrom(annotation);
            }
        }
    }
}
