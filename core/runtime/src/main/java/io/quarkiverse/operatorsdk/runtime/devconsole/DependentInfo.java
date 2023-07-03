package io.quarkiverse.operatorsdk.runtime.devconsole;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.arc.Arc;

@SuppressWarnings({ "unused", "rawtypes" })
public class DependentInfo<R, P extends HasMetadata> implements Comparable<DependentInfo> {
    private final DependentResourceSpec<R, P> spec;

    public DependentInfo(DependentResourceSpec<R, P> spec) {
        this.spec = spec;
    }

    public String getResourceClass() {
        try (final var dependent = Arc.container().instance(spec.getDependentResourceClass())) {
            return dependent.get().resourceType().getName();
        }
    }

    public String getName() {
        return spec.getName();
    }

    public Set<String> getDependsOn() {
        return spec.getDependsOn();
    }

    public boolean getHasConditions() {
        return getReadyCondition() != null || getReconcileCondition() != null || getDeletePostCondition() != null;
    }

    public String getReadyCondition() {
        return getConditionClassName(spec.getReadyCondition());
    }

    public String getReconcileCondition() {
        return getConditionClassName(spec.getReconcileCondition());
    }

    public String getDeletePostCondition() {
        return getConditionClassName(spec.getDeletePostCondition());
    }

    private String getConditionClassName(Condition condition) {
        return condition != null ? condition.getClass().getName() : null;
    }

    public String getUseEventSourceWithName() {
        return spec.getUseEventSourceWithName().orElse(null);
    }

    public String getType() {
        return spec.getDependentResourceClass().getName();
    }

    @Override
    public int compareTo(DependentInfo other) {
        return getName().compareTo(other.getName());
    }
}
