package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class DependentResourceSpecMetadata<R, P extends HasMetadata, C> {

    private final Class<? extends DependentResource<R, P>> dependentResourceClass;
    private final Class<? extends Annotation> annotationConfigClass;
    private final C dependentResourceConfig;

    private final String name;

    private final Set<String> dependsOn;

    private final Condition<?, ?> readyCondition;

    private final Condition<?, ?> reconcileCondition;

    private final Condition<?, ?> deletePostCondition;

    private final String useEventSourceWithName;

    @RecordableConstructor
    public DependentResourceSpecMetadata(Class<? extends DependentResource<R, P>> dependentResourceClass,
            Class<? extends Annotation> annotationConfigClass,
            C dependentResourceConfig,
            String name, Set<String> dependsOn,
            Condition<?, ?> readyCondition,
            Condition<?, ?> reconcileCondition,
            Condition<?, ?> deletePostCondition,
            String useEventSourceWithName) {
        this.dependentResourceClass = dependentResourceClass;
        this.annotationConfigClass = annotationConfigClass;
        this.dependentResourceConfig = dependentResourceConfig;
        this.name = name;
        this.dependsOn = dependsOn;
        this.readyCondition = readyCondition;
        this.reconcileCondition = reconcileCondition;
        this.deletePostCondition = deletePostCondition;
        this.useEventSourceWithName = useEventSourceWithName;
    }

    // Needed for the recordable constructor
    public C getDependentResourceConfig() {
        return dependentResourceConfig;
    }

    // Needed for the recordable constructor
    public Class<? extends DependentResource<R, P>> getDependentResourceClass() {
        return dependentResourceClass;
    }

    // Needed for the recordable constructor
    public Class<? extends Annotation> getAnnotationConfigClass() {
        return annotationConfigClass;
    }

    // Needed for the recordable constructor
    public String getName() {
        return name;
    }

    // Needed for the recordable constructor
    public Set<String> getDependsOn() {
        return dependsOn;
    }

    // Needed for the recordable constructor
    public Condition<?, ?> getReadyCondition() {
        return readyCondition;
    }

    // Needed for the recordable constructor
    public Condition<?, ?> getReconcileCondition() {
        return reconcileCondition;
    }

    // Needed for the recordable constructor
    public Condition<?, ?> getDeletePostCondition() {
        return deletePostCondition;
    }

    // Needed for the recordable constructor
    public String getUseEventSourceWithName() {
        return useEventSourceWithName;
    }
}
