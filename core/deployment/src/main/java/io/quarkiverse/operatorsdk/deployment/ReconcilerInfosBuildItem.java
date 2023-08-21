package io.quarkiverse.operatorsdk.deployment;

import java.util.Map;

import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ReconcilerInfosBuildItem extends SimpleBuildItem {
    private final Map<String, ReconcilerAugmentedClassInfo> reconcilers;

    public ReconcilerInfosBuildItem(Map<String, ReconcilerAugmentedClassInfo> reconcilers) {
        this.reconcilers = reconcilers;
    }

    public Map<String, ReconcilerAugmentedClassInfo> getReconcilers() {
        return reconcilers;
    }
}
