package io.quarkiverse.operatorsdk.common;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkus.builder.item.SimpleBuildItem;

public final class DeserializedKubernetesResourcesBuildItem extends SimpleBuildItem {
    private final List<HasMetadata> resources;

    public DeserializedKubernetesResourcesBuildItem(List<HasMetadata> resources) {
        this.resources = resources;
    }

    public List<HasMetadata> getResources() {
        return resources;
    }
}
