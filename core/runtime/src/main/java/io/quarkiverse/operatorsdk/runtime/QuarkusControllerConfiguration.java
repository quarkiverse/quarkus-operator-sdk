package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.AnnotationConfigurable;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
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
    private final Optional<Long> informerListLimit;
    private final Optional<Duration> maxReconciliationInterval;
    private final Optional<OnAddFilter<? super R>> onAddFilter;
    private final Optional<OnUpdateFilter<? super R>> onUpdateFilter;
    private final Optional<GenericFilter<? super R>> genericFilter;
    private final List<PolicyRule> additionalRBACRules;
    private final List<RoleRef> additionalRBACRoleRefs;
    private final String fieldManager;
    private final Optional<ItemStore<R>> itemStore;
    private Class<? extends Annotation> retryConfigurationClass;
    private Class<? extends Retry> retryClass;
    private Class<? extends Annotation> rateLimiterConfigurationClass;
    private Class<? extends RateLimiter> rateLimiterClass;
    private String finalizer;
    private Set<String> namespaces;
    private boolean wereNamespacesSet;
    private String labelSelector;
    private Retry retry;
    private RateLimiter rateLimiter;
    private QuarkusManagedWorkflow<R> workflow;
    private QuarkusConfigurationService parent;
    private ExternalGradualRetryConfiguration gradualRetry;

    @RecordableConstructor
    @SuppressWarnings("unchecked")
    public QuarkusControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            String crVersion, boolean generationAware,
            Class resourceClass,
            Long nullableInformerListLimit,
            Set<String> namespaces,
            boolean wereNamespacesSet,
            String finalizerName, String labelSelector,
            boolean statusPresentAndNotVoid,
            Duration maxReconciliationInterval,
            OnAddFilter<R> onAddFilter, OnUpdateFilter<R> onUpdateFilter, GenericFilter<R> genericFilter,
            Class<? extends Retry> retryClass, Class<? extends Annotation> retryConfigurationClass,
            Class<? extends RateLimiter> rateLimiterClass, Class<? extends Annotation> rateLimiterConfigurationClass,
            List<PolicyRule> additionalRBACRules, List<RoleRef> additionalRBACRoleRefs, String fieldManager,
            ItemStore<R> nullableItemStore) {
        this.associatedReconcilerClassName = associatedReconcilerClassName;
        this.name = name;
        this.resourceTypeName = resourceTypeName;
        this.crVersion = crVersion;
        this.generationAware = generationAware;
        this.resourceClass = resourceClass;
        this.informerListLimit = Optional.ofNullable(nullableInformerListLimit);
        this.additionalRBACRules = additionalRBACRules;
        this.additionalRBACRoleRefs = additionalRBACRoleRefs;
        setNamespaces(namespaces);
        this.wereNamespacesSet = wereNamespacesSet;
        setFinalizer(finalizerName);
        this.labelSelector = labelSelector;
        this.statusPresentAndNotVoid = statusPresentAndNotVoid;
        this.maxReconciliationInterval = maxReconciliationInterval != null ? Optional.of(maxReconciliationInterval)
                : ControllerConfiguration.super.maxReconciliationInterval();
        this.onAddFilter = Optional.ofNullable(onAddFilter);
        this.onUpdateFilter = Optional.ofNullable(onUpdateFilter);
        this.genericFilter = Optional.ofNullable(genericFilter);

        this.retryClass = retryClass;
        this.retry = GenericRetry.class.equals(retryClass) ? new GenericRetry() : null;
        this.retryConfigurationClass = retryConfigurationClass;

        this.rateLimiterClass = rateLimiterClass;
        this.rateLimiter = DefaultRateLimiter.class.equals(rateLimiterClass) ? new DefaultRateLimiter() : null;
        this.rateLimiterConfigurationClass = rateLimiterConfigurationClass;

        this.fieldManager = fieldManager != null ? fieldManager : ControllerConfiguration.super.fieldManager();
        this.itemStore = Optional.ofNullable(nullableItemStore);
    }

    @Override
    @IgnoreProperty
    public ConfigurationService getConfigurationService() {
        return parent;
    }

    public void setParent(QuarkusConfigurationService parent) {
        this.parent = parent;
    }

    @Override
    public Class<R> getResourceClass() {
        return resourceClass;
    }

    @Override
    public Optional<Long> getInformerListLimit() {
        return informerListLimit;
    }

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public Long getNullableInformerListLimit() {
        return informerListLimit.orElse(null);
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

    void setNamespaces(Set<String> namespaces) {
        if (!namespaces.equals(this.namespaces)) {
            this.namespaces = sanitizeNamespaces(namespaces);
            wereNamespacesSet = true;
            // propagate namespace changes to the dependents' config if needed
            propagateNamespacesToDependents();
        }
    }

    private static Set<String> sanitizeNamespaces(Set<String> namespaces) {
        return namespaces.stream().map(String::trim).collect(Collectors.toSet());
    }

    /**
     * Record potentially user-set namespaces, updating the dependent resources, which should have been set before this method
     * is called. Note that this method won't affect the status of whether the namespaces were set by the user or not, as this
     * should have been recorded already when the instance was created.
     * This method, while public for visibility purpose from the deployment module, should be considered internal and *NOT* be
     * called from user code.
     */
    @SuppressWarnings("unchecked")
    public void propagateNamespacesToDependents() {
        if (workflow != null) {
            dependentsMetadata().forEach((unused, spec) -> {
                final var config = spec.getDependentResourceConfig();
                if (config instanceof QuarkusKubernetesDependentResourceConfig qConfig) {
                    qConfig.setNamespaces(namespaces);
                }
            });
        }
    }

    public boolean isWereNamespacesSet() {
        return wereNamespacesSet;
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
        return dependentsMetadata().keySet().parallelStream().anyMatch(changedClasses::contains);
    }

    public boolean needsDependentBeansCreation() {
        final var dependentsMetadata = dependentsMetadata();
        return dependentsMetadata != null && !dependentsMetadata.isEmpty();
    }

    public QuarkusManagedWorkflow<R> getWorkflow() {
        return workflow;
    }

    public void setWorkflow(QuarkusManagedWorkflow<R> workflow) {
        this.workflow = workflow;
    }

    @Override
    public Object getConfigurationFor(DependentResourceSpec dependentResourceSpec) {
        return ((DependentResourceSpecMetadata) dependentResourceSpec).getDependentResourceConfig();
    }

    @Override
    public Optional<WorkflowSpec> getWorkflowSpec() {
        return workflow.getGenericSpec();
    }

    @Override
    public Retry getRetry() {
        return retry;
    }

    @Override
    public RateLimiter getRateLimiter() {
        return rateLimiter;
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
    public OnAddFilter<? super R> getOnAddFilter() {
        return onAddFilter.orElse(null);
    }

    @Override
    public Optional<OnAddFilter<? super R>> onAddFilter() {
        return onAddFilter;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnUpdateFilter<? super R> getOnUpdateFilter() {
        return onUpdateFilter.orElse(null);
    }

    @Override
    public Optional<OnUpdateFilter<? super R>> onUpdateFilter() {
        return onUpdateFilter;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public GenericFilter<? super R> getGenericFilter() {
        return genericFilter.orElse(null);
    }

    @Override
    public Optional<GenericFilter<? super R>> genericFilter() {
        return genericFilter;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Class<? extends Retry> getRetryClass() {
        return retryClass;
    }

    void setGradualRetryConfiguration(ExternalGradualRetryConfiguration gradualRetry) {
        this.gradualRetry = gradualRetry;
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public Class<? extends RateLimiter> getRateLimiterClass() {
        return rateLimiterClass;
    }

    public Map<String, DependentResourceSpecMetadata> dependentsMetadata() {
        return workflow.getSpec().map(QuarkusWorkflowSpec::getDependentResourceSpecMetadata).orElse(Collections.emptyMap());
    }

    void initAnnotationConfigurables(Reconciler<R> reconciler) {
        final Class<? extends Reconciler> reconcilerClass = reconciler.getClass();
        if (retryConfigurationClass != null || gradualRetry != null) {
            if (retry == null) {
                retry = ClassLoadingUtils.instantiate(retryClass);
            }
            configure(reconcilerClass, retryConfigurationClass, (AnnotationConfigurable) retry);
            // override with configuration from application.properties (if it exists) for GradualRetry
            if (gradualRetry != null) {
                // configurable should be a GenericRetry as validated by RetryResolver
                final var genericRetry = (GenericRetry) retry;
                gradualRetry.maxAttempts.ifPresent(genericRetry::setMaxAttempts);
                gradualRetry.interval.initial.ifPresent(genericRetry::setInitialInterval);
                gradualRetry.interval.max.ifPresent(genericRetry::setMaxInterval);
                gradualRetry.interval.multiplier.ifPresent(genericRetry::setIntervalMultiplier);
            }
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

    public List<PolicyRule> getAdditionalRBACRules() {
        return additionalRBACRules;
    }

    public List<RoleRef> getAdditionalRBACRoleRefs() {
        return additionalRBACRoleRefs;
    }

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public String getFieldManager() {
        return fieldManager;
    }

    @Override
    public String fieldManager() {
        return fieldManager;
    }

    @Override
    public Optional<ItemStore<R>> getItemStore() {
        return itemStore;
    }

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public ItemStore<R> getNullableItemStore() {
        return itemStore.orElse(null);
    }
}
