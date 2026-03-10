package io.quarkiverse.operatorsdk.runtime.config;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceFactory;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowFactory;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.DependentResourceSpecMetadata;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;

public class OverriddenConfigurationService implements QuarkusConfigurationService {
    private final QuarkusConfigurationService delegate;
    private final ConfigurationService overridden;

    public OverriddenConfigurationService(QuarkusConfigurationService delegate, ConfigurationService overridden) {
        this.delegate = delegate;
        this.overridden = overridden;
    }

    @Override
    public boolean checkCRDAndValidateLocalModel() {
        return overridden.checkCRDAndValidateLocalModel();
    }

    @Override
    public int concurrentReconciliationThreads() {
        return overridden.concurrentReconciliationThreads();
    }

    @Override
    public int concurrentWorkflowExecutorThreads() {
        return overridden.concurrentWorkflowExecutorThreads();
    }

    @Override
    public Metrics getMetrics() {
        return overridden.getMetrics();
    }

    @Override
    public ExecutorService getExecutorService() {
        return overridden.getExecutorService();
    }

    @Override
    public ExecutorService getWorkflowExecutorService() {
        return overridden.getWorkflowExecutorService();
    }

    @Override
    public boolean closeClientOnStop() {
        return overridden.closeClientOnStop();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public DependentResourceFactory dependentResourceFactory() {
        return delegate.dependentResourceFactory();
    }

    @Override
    public Optional<LeaderElectionConfiguration> getLeaderElectionConfiguration() {
        return overridden.getLeaderElectionConfiguration();
    }

    @Override
    public boolean stopOnInformerErrorDuringStartup() {
        return overridden.stopOnInformerErrorDuringStartup();
    }

    @Override
    public Duration cacheSyncTimeout() {
        return overridden.cacheSyncTimeout();
    }

    @Override
    public Duration reconciliationTerminationTimeout() {
        return overridden.reconciliationTerminationTimeout();
    }

    @Override
    public Optional<InformerStoppedHandler> getInformerStoppedHandler() {
        return overridden.getInformerStoppedHandler();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ManagedWorkflowFactory getWorkflowFactory() {
        return delegate.getWorkflowFactory();
    }

    @Override
    public ExecutorServiceManager getExecutorServiceManager() {
        return overridden.getExecutorServiceManager();
    }

    @Override
    public boolean ssaBasedCreateUpdateMatchForDependentResources() {
        return overridden.ssaBasedCreateUpdateMatchForDependentResources();
    }

    @Override
    public <R extends HasMetadata, P extends HasMetadata> boolean shouldUseSSA(
            KubernetesDependentResource<R, P> dependentResource) {
        return delegate.shouldUseSSA(dependentResource);
    }

    @Override
    public boolean shouldUseSSA(Class<? extends KubernetesDependentResource> dependentResourceType,
            Class<? extends HasMetadata> resourceType, KubernetesDependentResourceConfig<? extends HasMetadata> config) {
        return delegate.shouldUseSSA(dependentResourceType, resourceType, config);
    }

    @Override
    public Set<Class<? extends HasMetadata>> defaultNonSSAResources() {
        return overridden.defaultNonSSAResources();
    }

    @Override
    public boolean useSSAToPatchPrimaryResource() {
        return overridden.useSSAToPatchPrimaryResource();
    }

    @Override
    public boolean cloneSecondaryResourcesWhenGettingFromCache() {
        return overridden.cloneSecondaryResourcesWhenGettingFromCache();
    }

    @Override
    public CRDGenerationInfo getCRDGenerationInfo() {
        return delegate.getCRDGenerationInfo();
    }

    @Override
    public boolean shouldStartOperator() {
        return delegate.shouldStartOperator();
    }

    @Override
    public boolean isAsyncStart() {
        return delegate.isAsyncStart();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public DependentResourceSpecMetadata getDependentByName(String controllerName, String dependentName) {
        return delegate.getDependentByName(controllerName, dependentName);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ManagedWorkflow workflowByName(String name) {
        return delegate.workflowByName(name);
    }

    @Override
    public <R extends HasMetadata> QuarkusControllerConfiguration<R> getConfigurationFor(Reconciler<R> reconciler) {
        return delegate.getConfigurationFor(reconciler);
    }

    @Override
    public Set<String> getKnownReconcilerNames() {
        return delegate.getKnownReconcilerNames();
    }

    @Override
    public Version getVersion() {
        return delegate.getVersion();
    }
}
