package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class DependentResourceSpecMetadata<R, P extends HasMetadata, C> extends
        DependentResourceSpec<R, P, C> {
    private final Class<R> resourceClass;

    @RecordableConstructor
    public DependentResourceSpecMetadata(Class<? extends DependentResource<R, P>> dependentResourceClass,
            String name, Set<String> dependsOn,
            Condition<?, ?> readyCondition,
            Condition<?, ?> reconcileCondition,
            Condition<?, ?> deletePostCondition,
            Condition<?, ?> activationCondition,
            String quarkusUseEventSourceWithName,
            Class<R> resourceClass) {
        super(dependentResourceClass, name, dependsOn, readyCondition, reconcileCondition, deletePostCondition,
                activationCondition,
                quarkusUseEventSourceWithName);
        this.resourceClass = resourceClass;
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public String getQuarkusUseEventSourceWithName() {
        return getUseEventSourceWithName().orElse(null);
    }

    // Getter required for Quarkus' byte recording to work
    @SuppressWarnings("unused")
    public C getNullableConfiguration() {
        return getConfiguration().orElse(null);
    }

    // Setter required for Quarkus' byte recording to work
    @SuppressWarnings("unused")
    @Override
    public void setNullableConfiguration(C configuration) {
        super.setNullableConfiguration(configuration);
    }

    public Class<R> getResourceClass() {
        return resourceClass;
    }
}
