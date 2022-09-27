package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ExternalDependentResource extends KubernetesDependentResource<External, Third> {

    public ExternalDependentResource() {
        super(External.class);
    }
}
