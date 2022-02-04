package io.quarkiverse.operatorsdk.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.KubernetesDependentResourceController;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceController<R extends HasMetadata, P extends HasMetadata> extends
        KubernetesDependentResourceController<R, P> {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceController(
            DependentResource<R, P> delegate,
            KubernetesDependentResourceConfiguration<R, P> configuration) {
        super(delegate, configuration);
    }
}
