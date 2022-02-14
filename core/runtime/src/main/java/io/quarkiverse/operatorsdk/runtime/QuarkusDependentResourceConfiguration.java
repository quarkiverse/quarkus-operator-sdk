package io.quarkiverse.operatorsdk.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusDependentResourceConfiguration<R, P extends HasMetadata> implements
        DependentResourceConfiguration<R, P> {

    private final String dependentResourceClassName;
    private Class<? extends DependentResource<R, P, ? extends DependentResourceConfiguration<R, P>>> dependentResourceClass;
    private final String resourceClassName;
    private Class<R> resourceClass;

    @RecordableConstructor
    public QuarkusDependentResourceConfiguration(String dependentResourceClassName,
            String resourceClassName) {
        this.dependentResourceClassName = dependentResourceClassName;
        this.resourceClassName = resourceClassName;
    }

    @Override
    public Class<? extends DependentResource<R, P, ? extends DependentResourceConfiguration<R, P>>> getDependentResourceClass() {
        return dependentResourceClass = ClassUtils.loadClassIfNeeded(dependentResourceClassName,
                dependentResourceClass);
    }

    @Override
    public Class<R> getResourceClass() {
        return resourceClass = ClassUtils.loadClass(resourceClassName, resourceClass);
    }

    public String getDependentResourceClassName() {
        return dependentResourceClassName;
    }

    public String getResourceClassName() {
        return resourceClassName;
    }
}
