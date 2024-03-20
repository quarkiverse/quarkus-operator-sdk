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
    private final Map<String, DependentResourceSpecMetadata> dependentResourceSpecMetadata;

    @RecordableConstructor
    public QuarkusWorkflowSpec(Map<String, DependentResourceSpecMetadata> dependentResourceSpecMetadata,
            boolean explicitInvocation) {
        this.dependentResourceSpecMetadata = dependentResourceSpecMetadata;
        this.explicitInvocation = explicitInvocation;
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
}
