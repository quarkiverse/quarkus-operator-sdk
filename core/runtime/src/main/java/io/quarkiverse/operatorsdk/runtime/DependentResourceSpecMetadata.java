package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.runtime.annotations.IgnoreProperty;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class DependentResourceSpecMetadata<R, P extends HasMetadata, C> extends
        DependentResourceSpec<R, P> {
    private final C dependentResourceConfig;
    private Class<R> dependentType;
    private final String dependentTypeName;

    @RecordableConstructor
    public DependentResourceSpecMetadata(Class<? extends DependentResource<R, P>> dependentResourceClass,
            C dependentResourceConfig,
            String name, Set<String> dependsOn,
            Condition<?, ?> readyCondition,
            Condition<?, ?> reconcileCondition,
            Condition<?, ?> deletePostCondition,
            String quarkusUseEventSourceWithName,
            String dependentTypeName) {
        super(dependentResourceClass, name, dependsOn, readyCondition, reconcileCondition, deletePostCondition,
                quarkusUseEventSourceWithName);
        this.dependentResourceConfig = dependentResourceConfig;
        this.dependentTypeName = dependentTypeName;
    }

    // Needed for the recordable constructor
    public C getDependentResourceConfig() {
        return dependentResourceConfig;
    }

    @SuppressWarnings("unchecked")
    @IgnoreProperty
    public Class<R> getDependentType() {
        if (dependentType == null) {
            try {
                dependentType = (Class<R>) Thread.currentThread().getContextClassLoader().loadClass(dependentTypeName);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return dependentType;
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public String getDependentTypeName() {
        return dependentTypeName;
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public String getQuarkusUseEventSourceWithName() {
        return getUseEventSourceWithName().orElse(null);
    }
}
