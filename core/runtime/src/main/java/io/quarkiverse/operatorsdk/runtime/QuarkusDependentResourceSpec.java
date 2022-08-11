package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusDependentResourceSpec<T extends DependentResource<?, ?>, C> extends DependentResourceSpec<T, C> {

    @RecordableConstructor
    public QuarkusDependentResourceSpec(Class<T> dependentResourceClass, C dependentResourceConfig,
            String name, Set<String> dependsOn,
            Condition<?, ?> readyCondition,
            Condition<?, ?> reconcileCondition,
            Condition<?, ?> deletePostCondition) {
        super(dependentResourceClass, dependentResourceConfig, name, dependsOn, readyCondition,
                reconcileCondition, deletePostCondition);
    }

    // For Quarkus RecordableConstructor
    @SuppressWarnings("unused")
    public C getDependentResourceConfig() {
        return super.getDependentResourceConfiguration().orElse(null);
    }
}
