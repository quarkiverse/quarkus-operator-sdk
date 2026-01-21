package io.quarkiverse.operatorsdk.runtime;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.processing.event.rate.LinearRateLimiter;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusBuildTimeControllerConfiguration<R extends HasMetadata> implements ControllerConfiguration<R> {
    private static final Logger log = Logger.getLogger(QuarkusBuildTimeControllerConfiguration.class);

    private final String associatedReconcilerClassName;
    private final String name;
    private final String resourceTypeName;
    private final boolean generationAware;
    private final boolean statusPresentAndNotVoid;
    private final Class<R> resourceClass;
    private final List<PolicyRule> additionalRBACRules;
    private final List<RoleRef> additionalRBACRoleRefs;
    private final String fieldManager;
    private final boolean triggerReconcilerOnAllEvents;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<Duration> maxReconciliationInterval;
    private String finalizer;
    private boolean wereNamespacesSet;
    private final Retry retry;
    private final RateLimiter rateLimiter;
    private QuarkusManagedWorkflow<R> workflow;
    private BuildTimeConfigurationService parent;
    private QuarkusInformerConfiguration<R> informerConfig;
    private Set<String> namespaces;
    private String labelSelector;
    private QuarkusFieldSelector fieldSelector;

    @RecordableConstructor
    @SuppressWarnings("unchecked")
    public QuarkusBuildTimeControllerConfiguration(
            String associatedReconcilerClassName,
            String name,
            String resourceTypeName,
            boolean generationAware,
            Class resourceClass,
            boolean wereNamespacesSet,
            String finalizerName,
            boolean statusPresentAndNotVoid,
            Duration maxReconciliationInterval,
            Retry retry, RateLimiter rateLimiter,
            List<PolicyRule> additionalRBACRules,
            List<RoleRef> additionalRBACRoleRefs,
            String fieldManager,
            QuarkusInformerConfiguration<R> informerConfig,
            boolean triggerReconcilerOnAllEvents) {
        this.informerConfig = informerConfig;
        this.associatedReconcilerClassName = associatedReconcilerClassName;
        this.name = name;
        this.resourceTypeName = resourceTypeName;
        this.generationAware = generationAware;
        this.resourceClass = resourceClass;
        this.additionalRBACRules = additionalRBACRules;
        this.additionalRBACRoleRefs = additionalRBACRoleRefs;
        this.wereNamespacesSet = wereNamespacesSet;
        setFinalizer(finalizerName);
        this.statusPresentAndNotVoid = statusPresentAndNotVoid;
        this.maxReconciliationInterval = maxReconciliationInterval != null ? Optional.of(maxReconciliationInterval)
                : ControllerConfiguration.super.maxReconciliationInterval();
        this.retry = retry == null ? new GenericRetry() : retry;
        this.rateLimiter = rateLimiter == null ? new DefaultRateLimiter() : rateLimiter;
        this.fieldManager = fieldManager != null ? fieldManager : ControllerConfiguration.super.fieldManager();
        this.triggerReconcilerOnAllEvents = triggerReconcilerOnAllEvents;
    }

    @Override
    @IgnoreProperty
    public BuildTimeConfigurationService getConfigurationService() {
        return parent;
    }

    public void setParent(BuildTimeConfigurationService parent) {
        this.parent = parent;
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

    @Override
    public String getFinalizerName() {
        return finalizer;
    }

    public void setFinalizer(String finalizer) {
        this.finalizer = finalizer != null && !finalizer.isBlank() ? finalizer
                : ReconcilerUtilsInternal.getDefaultFinalizerName(resourceTypeName);
    }

    @Override
    public boolean isGenerationAware() {
        return generationAware;
    }

    @Override
    public String getAssociatedReconcilerClassName() {
        return associatedReconcilerClassName;
    }

    void setNamespaces(Set<String> namespaces) {
        if (!namespaces.equals(informerConfig.getNamespaces())) {
            this.namespaces = namespaces;
            wereNamespacesSet = true;
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

    void setLabelSelector(String labelSelector) {
        if (!Objects.equals(informerConfig.getLabelSelector(), labelSelector)) {
            this.labelSelector = labelSelector;
        }
    }

    void setFieldSelector(List<String> fieldSelectors) {
        if (!Objects.equals(informerConfig.getFieldSelector(), fieldSelectors)) {
            this.fieldSelector = QuarkusFieldSelector.from(fieldSelectors, resourceClass, parent);
        }
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
    @SuppressWarnings("unchecked")
    public Object getConfigurationFor(DependentResourceSpec dependentResourceSpec) {
        return dependentResourceSpec.getConfiguration().orElse(null);
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

    void setMaxReconciliationInterval(Duration duration) {
        maxReconciliationInterval = Optional.of(duration);
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnAddFilter<? super R> getOnAddFilter() {
        return getInformerConfig().getOnAddFilter();
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public OnUpdateFilter<? super R> getOnUpdateFilter() {
        return getInformerConfig().getOnUpdateFilter();
    }

    // for Quarkus' RecordableConstructor
    @SuppressWarnings("unused")
    public GenericFilter<? super R> getGenericFilter() {
        return getInformerConfig().getGenericFilter();
    }

    public Map<String, DependentResourceSpecMetadata> dependentsMetadata() {
        return workflow.getSpec().map(QuarkusWorkflowSpec::getDependentResourceSpecMetadata).orElse(Collections.emptyMap());
    }

    void updateRetryConfiguration(ExternalGradualRetryConfiguration externalGradualRetryConfiguration) {
        // override with configuration from application.properties (if it exists) for GradualRetry
        if (externalGradualRetryConfiguration != null) {
            if (!(retry instanceof GenericRetry genericRetry)) {
                log.warnf(
                        "Retry configuration in application.properties is only appropriate when using the GenericRetry implementation, yet your Reconciler is configured to use %s as Retry implementation. Configuration from application.properties will therefore be ignored.",
                        retry.getClass().getName());
                return;
            }
            // configurable should be a GenericRetry as validated by RetryResolver
            externalGradualRetryConfiguration.maxAttempts().ifPresent(genericRetry::setMaxAttempts);
            final var intervalConfiguration = externalGradualRetryConfiguration.interval();
            intervalConfiguration.initial().ifPresent(genericRetry::setInitialInterval);
            intervalConfiguration.max().ifPresent(genericRetry::setMaxInterval);
            intervalConfiguration.multiplier().ifPresent(genericRetry::setIntervalMultiplier);
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

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public ItemStore<R> getNullableItemStore() {
        return getInformerConfig().getItemStore();
    }

    @Override
    public InformerConfiguration<R> getInformerConfig() {
        if (labelSelector != null || fieldSelector != null || namespaces != null) {
            final var builder = InformerConfiguration.builder(informerConfig);
            if (namespaces != null) {
                builder.withNamespaces(namespaces);
            }
            if (fieldSelector != null) {
                builder.withFieldSelector(fieldSelector);
            }
            if (labelSelector != null) {
                builder.withLabelSelector(labelSelector);
            }
            informerConfig = new QuarkusInformerConfiguration<>(builder.buildForController());
            // reset so that we know that we don't need to regenerate the informer config next time if these values haven't changed since
            labelSelector = null;
            fieldSelector = null;
            namespaces = null;
        }
        return informerConfig;
    }

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

    @SuppressWarnings("unused")
    // this is needed by Quarkus for the RecordableConstructor
    public boolean isTriggerReconcilerOnAllEvents() {
        return triggerReconcilerOnAllEvents;
    }
}
