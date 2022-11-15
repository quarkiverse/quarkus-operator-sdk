package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilters;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusControllerConfiguration<R extends HasMetadata> extends
        ResolvedControllerConfiguration<R> implements ControllerConfiguration<R> {

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
        @SuppressWarnings("unused")
        public DefaultRateLimiter() {
            super();
        }

        @RecordableConstructor
        @SuppressWarnings("unused")
        public DefaultRateLimiter(Duration refreshPeriod, int limitForPeriod) {
            super(refreshPeriod, limitForPeriod);
        }
    }

    private final String crVersion;
    private final boolean statusPresentAndNotVoid;
    private final Class<? extends Annotation> retryConfigurationClass;
    private final Class<? extends Annotation> rateLimiterConfigurationClass;
    private RetryConfiguration retryConfiguration;
    @IgnoreProperty
    private boolean namespaceExpansionRequired;

    @RecordableConstructor
    @SuppressWarnings("unused")
    public QuarkusControllerConfiguration(
            Class<R> resourceClass, String name, boolean generationAware, String associatedReconcilerClassName,
            Retry retry, RateLimiter rateLimiter, Duration maxReconciliationInterval,
            OnAddFilter<R> onAddFilter, OnUpdateFilter<R> onUpdateFilter,
            GenericFilter<R> genericFilter, List<DependentResourceSpec> dependentResources,
            Set<String> namespaces, String finalizerName, String labelSelector,
            String crVersion,
            boolean statusPresentAndNotVoid,
            Class<? extends Annotation> retryConfigurationClass,
            Class<? extends Annotation> rateLimiterConfigurationClass,
            ResourceEventFilter<R> eventFilter) {
        super(resourceClass, name, generationAware, associatedReconcilerClassName, retry, rateLimiter,
                maxReconciliationInterval, onAddFilter, onUpdateFilter, genericFilter, dependentResources, namespaces,
                finalizerName, labelSelector);
        this.crVersion = crVersion;
        this.retryConfiguration = getRetryConfiguration();
        setNamespaces(namespaces);
        setFinalizer(finalizerName);
        this.statusPresentAndNotVoid = statusPresentAndNotVoid;
        setEventFilter(eventFilter);
        this.retryConfigurationClass = retryConfigurationClass;
        this.rateLimiterConfigurationClass = rateLimiterConfigurationClass;
    }

    public QuarkusControllerConfiguration(Class<R> resourceClass, ControllerConfiguration<R> other,
            String crVersion, boolean statusPresentAndNotVoid,
            Class<? extends Annotation> retryConfigurationClass,
            Class<? extends Annotation> rateLimiterConfigurationClass) {
        super(resourceClass, other);
        this.crVersion = crVersion;
        this.statusPresentAndNotVoid = statusPresentAndNotVoid;
        this.retryConfigurationClass = retryConfigurationClass;
        this.rateLimiterConfigurationClass = rateLimiterConfigurationClass;
    }

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public String getCrVersion() {
        return crVersion;
    }

    protected void setNamespaces(Collection<String> namespaces) {
        super.setNamespaces(namespaces);
        if (namespaces != null && !namespaces.isEmpty()) {
            namespaceExpansionRequired = namespaces.stream().anyMatch(ns -> ns.contains("${"));
        } else {
            namespaceExpansionRequired = false;
        }
    }

    public boolean isNamespaceExpansionRequired() {
        return namespaceExpansionRequired;
    }

    @Override
    public RetryConfiguration getRetryConfiguration() {
        return retryConfiguration;
    }

    void setRetryConfiguration(RetryConfiguration retryConfiguration) {
        this.retryConfiguration = retryConfiguration != null ? retryConfiguration
                : super.getRetryConfiguration();
    }

    @IgnoreProperty
    @Override
    public Set<String> getEffectiveNamespaces() {
        return super.getEffectiveNamespaces();
    }

    public boolean isStatusPresentAndNotVoid() {
        return statusPresentAndNotVoid;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Duration getMaxReconciliationInterval() {
        return maxReconciliationInterval().orElseThrow();
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnAddFilter<R> getOnAddFilter() {
        return onAddFilter().orElse(null);
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnUpdateFilter<R> getOnUpdateFilter() {
        return onUpdateFilter().orElse(null);
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public GenericFilter<R> getGenericFilter() {
        return genericFilter().orElse(null);
    }

    void initAnnotationConfigurables(Reconciler<R> reconciler) {
        // todo: investigate if/how this could be done at build time
        final Class<? extends Reconciler> reconcilerClass = reconciler.getClass();
        if (retryConfigurationClass != null) {
            configure(reconcilerClass, retryConfigurationClass, (AnnotationConfigurable) getRetry());
        }

        if (rateLimiterConfigurationClass != null) {
            configure(reconcilerClass, rateLimiterConfigurationClass, (AnnotationConfigurable) getRateLimiter());
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
            configurable.initFrom(annotation);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setEventFilter(ResourceEventFilter<R> eventFilter) {
        if (eventFilter == null || ResourceEventFilters.passthrough().equals(eventFilter)) {
            eventFilter = DEFAULT;
        }
        super.setEventFilter(eventFilter);
    }

    @Override
    public void setFinalizer(String finalizer) {
        super.setFinalizer(finalizer);
    }

    @Override
    public void setLabelSelector(String labelSelector) {
        super.setLabelSelector(labelSelector);
    }

}
