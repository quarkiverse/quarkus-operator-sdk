package io.quarkiverse.operatorsdk.deployment;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
        extends KubernetesDependentResource<R, P> {

    @RecordableConstructor
    public QuarkusKubernetesDependentResource() {
    }

    @Override
    protected void setKubernetesDependentResourceConfig(KubernetesDependentResourceConfig<R> config) {
        super.setKubernetesDependentResourceConfig(config);
    }

    @Override
    protected KubernetesDependentResourceConfig<R> getKubernetesDependentResourceConfig() {
        return super.getKubernetesDependentResourceConfig();
    }

    @Override
    protected void setResourceType(Class<R> resourceType) {
        super.setResourceType(resourceType);
    }

    @Override
    protected Class<R> getResourceType() {
        return super.getResourceType();
    }
}
