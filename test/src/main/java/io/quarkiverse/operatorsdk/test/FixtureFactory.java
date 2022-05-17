package io.quarkiverse.operatorsdk.test;

import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface FixtureFactory {
    default List<? extends HasMetadata> build(String namespace) {
        return Collections.emptyList();
    }
}
