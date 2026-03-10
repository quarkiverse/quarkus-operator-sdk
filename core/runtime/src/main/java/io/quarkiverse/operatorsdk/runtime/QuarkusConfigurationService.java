package io.quarkiverse.operatorsdk.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;

public interface QuarkusConfigurationService extends ConfigurationService {
    CRDGenerationInfo getCRDGenerationInfo();

    boolean shouldStartOperator();

    boolean isAsyncStart();

    @SuppressWarnings({ "rawtypes" })
    DependentResourceSpecMetadata getDependentByName(String controllerName, String dependentName);

    @SuppressWarnings("rawtypes")
    ManagedWorkflow workflowByName(String name);

    @Override
    <R extends HasMetadata> QuarkusControllerConfiguration<R> getConfigurationFor(Reconciler<R> reconciler);
}
