package io.quarkiverse.operatorsdk.runtime;

import java.time.Duration;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public class QuarkusControllerConfiguration<P extends HasMetadata> implements ControllerConfiguration<P> {
    private final InformerConfiguration<P> informerConfig;
    private final String name;
    private final boolean generationAware;
    private final String associatedReconcilerClassName;
    private final Retry retry;
    private final RateLimiter<?> rateLimiter;
    private final Duration maxReconciliationInterval;
    private final String finalizer;
    private final String fieldManager;
    private final QuarkusManagedWorkflow<P> workflow;
    private final String resourceTypeName;
    private final Class<P> resourceClass;
    private QuarkusConfigurationService configurationService;

    @SuppressWarnings("rawtypes")
    public QuarkusControllerConfiguration(InformerConfiguration<P> informerConfig, String name, boolean generationAware,
            String associatedReconcilerClassName, Retry retry, RateLimiter rateLimiter, Duration maxReconciliationInterval,
            String finalizer, String fieldManager,
            QuarkusManagedWorkflow<P> workflow, String resourceTypeName, Class<P> resourceClass) {
        this.informerConfig = informerConfig;
        this.name = name;
        this.generationAware = generationAware;
        this.associatedReconcilerClassName = associatedReconcilerClassName;
        this.retry = retry;
        this.rateLimiter = rateLimiter;
        this.maxReconciliationInterval = maxReconciliationInterval;
        this.finalizer = finalizer;
        this.fieldManager = fieldManager;
        this.workflow = workflow;
        this.resourceTypeName = resourceTypeName;
        this.resourceClass = resourceClass;
    }

    protected void setParent(QuarkusConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFinalizerName() {
        return finalizer;
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
    public Retry getRetry() {
        return retry;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    @Override
    public Optional<WorkflowSpec> getWorkflowSpec() {
        return workflow.getGenericSpec();
    }

    @Override
    public Optional<Duration> maxReconciliationInterval() {
        return Optional.of(maxReconciliationInterval);
    }

    @Override
    public ConfigurationService getConfigurationService() {
        return configurationService;
    }

    @Override
    public String fieldManager() {
        return fieldManager;
    }

    @Override
    public <C> C getConfigurationFor(DependentResourceSpec<?, P, C> dependentResourceSpec) {
        return dependentResourceSpec.getConfiguration().orElse(null);
    }

    @Override
    public String getResourceTypeName() {
        return resourceTypeName;
    }

    @Override
    public InformerConfiguration<P> getInformerConfig() {
        return informerConfig;
    }

    @Override
    public Class<P> getResourceClass() {
        return resourceClass;
    }

    public QuarkusManagedWorkflow<P> getWorkflow() {
        return workflow;
    }
}
