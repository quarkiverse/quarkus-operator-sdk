package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusDependentResourceSpec<R, P extends HasMetadata, C> extends DependentResourceSpec<R, P, C> {

    @RecordableConstructor
    // Important note: parameters starting with `quarkus` are named such to be able to provide a matching getter that doesn't conflict with an already existing one but with a different return type
    public QuarkusDependentResourceSpec(DependentResource<R, P> dependentResource, String name, Set<String> dependsOn,
            Condition<?, ?> readyCondition,
            Condition<?, ?> reconcileCondition,
            Condition<?, ?> deletePostCondition, String quarkusUseEventSourceWithName) {
        super(dependentResource, name, dependsOn, readyCondition, reconcileCondition, deletePostCondition,
                quarkusUseEventSourceWithName);
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public String getQuarkusUseEventSourceWithName() {
        return getUseEventSourceWithName().orElse(null);
    }
}
