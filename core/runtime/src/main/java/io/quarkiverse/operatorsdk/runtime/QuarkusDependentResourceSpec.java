package io.quarkiverse.operatorsdk.runtime;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusDependentResourceSpec<T extends DependentResource<?, ?>, C> extends DependentResourceSpec<T, C> {

    @RecordableConstructor
    public QuarkusDependentResourceSpec(Class<T> dependentResourceClass, C dependentResourceConfig, String name) {
        super(dependentResourceClass, dependentResourceConfig, name);
    }

    public C getDependentResourceConfig() {
        return super.getDependentResourceConfiguration().orElse(null);
    }
}
