package io.quarkiverse.operatorsdk.common;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkus.builder.item.SimpleBuildItem;

public final class DeserializedKubernetesResourcesBuildItem extends SimpleBuildItem {
    private final List<HasMetadata> resources;

    public DeserializedKubernetesResourcesBuildItem(List<HasMetadata> resources) {
        this.resources = resources;
    }

    /**
     * Note that these resources are "live" so any modification made to them will be propagated anywhere this method is called
     * and a reference on the result is kept so if you don't want local changes to be propagated, make a copy of the resources
     * you interact with, first.
     */
    public List<HasMetadata> getResources() {
        return resources;
    }
}
