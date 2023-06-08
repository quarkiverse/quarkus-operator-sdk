package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class PodDependentResource extends KubernetesDependentResource<Pod, Third> {

    public PodDependentResource() {
        super(Pod.class);
    }
}
