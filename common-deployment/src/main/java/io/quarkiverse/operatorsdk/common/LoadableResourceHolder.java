package io.quarkiverse.operatorsdk.common;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface LoadableResourceHolder<T extends HasMetadata> {
    String getAssociatedResourceTypeName();

    Class<T> loadAssociatedClass();
}
