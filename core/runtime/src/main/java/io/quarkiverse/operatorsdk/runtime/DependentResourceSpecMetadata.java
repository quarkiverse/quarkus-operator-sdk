package io.quarkiverse.operatorsdk.runtime;

import java.lang.annotation.Annotation;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class DependentResourceSpecMetadata<R, P extends HasMetadata, C> extends
        DependentResourceSpec<R, P> {
    private final Class<? extends Annotation> annotationConfigClass;
    private final C dependentResourceConfig;

    @RecordableConstructor
    public DependentResourceSpecMetadata(Class<? extends DependentResource<R, P>> dependentResourceClass,
            Class<? extends Annotation> annotationConfigClass,
            C dependentResourceConfig,
            String name, Set<String> dependsOn,
            Condition<?, ?> readyCondition,
            Condition<?, ?> reconcileCondition,
            Condition<?, ?> deletePostCondition,
            String quarkusUseEventSourceWithName) {
        super(dependentResourceClass, name, dependsOn, readyCondition, reconcileCondition, deletePostCondition,
                quarkusUseEventSourceWithName);
        this.annotationConfigClass = annotationConfigClass;
        this.dependentResourceConfig = dependentResourceConfig;
    }

    // Needed for the recordable constructor
    public C getDependentResourceConfig() {
        return dependentResourceConfig;
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public Class<? extends Annotation> getAnnotationConfigClass() {
        return annotationConfigClass;
    }

    // Needed for the recordable constructor
    @SuppressWarnings("unused")
    public String getQuarkusUseEventSourceWithName() {
        return getUseEventSourceWithName().orElse(null);
    }
}
