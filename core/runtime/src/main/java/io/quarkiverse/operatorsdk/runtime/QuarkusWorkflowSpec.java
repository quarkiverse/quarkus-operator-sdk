package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

@SuppressWarnings("rawtypes")
public class QuarkusWorkflowSpec implements WorkflowSpec {
    private final boolean explicitInvocation;
    private final boolean handleExceptionsInReconciler;
    private final Map<String, DependentResourceSpecMetadata> dependentResourceSpecMetadata;

    @RecordableConstructor
    public QuarkusWorkflowSpec(Map<String, DependentResourceSpecMetadata> dependentResourceSpecMetadata,
            boolean explicitInvocation, boolean handleExceptionsInReconciler) {
        this.dependentResourceSpecMetadata = dependentResourceSpecMetadata;
        this.explicitInvocation = explicitInvocation;
        this.handleExceptionsInReconciler = handleExceptionsInReconciler;
    }

    @IgnoreProperty
    @Override
    public List<DependentResourceSpec> getDependentResourceSpecs() {
        return dependentResourceSpecMetadata.values().stream().map(DependentResourceSpec.class::cast).toList();
    }

    public Map<String, DependentResourceSpecMetadata> getDependentResourceSpecMetadata() {
        return dependentResourceSpecMetadata;
    }

    @Override
    public boolean isExplicitInvocation() {
        return explicitInvocation;
    }

    @Override
    public boolean handleExceptionsInReconciler() {
        return false;
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public boolean isHandleExceptionsInReconciler() {
        return handleExceptionsInReconciler;
    }
}
