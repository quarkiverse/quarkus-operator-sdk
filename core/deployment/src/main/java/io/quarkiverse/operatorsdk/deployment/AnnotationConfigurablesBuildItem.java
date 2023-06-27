package io.quarkiverse.operatorsdk.deployment;

import java.util.Map;

import io.quarkiverse.operatorsdk.common.AnnotationConfigurableAugmentedClassInfo;
import io.quarkus.builder.item.SimpleBuildItem;

final class AnnotationConfigurablesBuildItem extends SimpleBuildItem {

    private final Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos;

    public AnnotationConfigurablesBuildItem(
            Map<String, AnnotationConfigurableAugmentedClassInfo> configurableInfos) {
        this.configurableInfos = configurableInfos;
    }

    public Map<String, AnnotationConfigurableAugmentedClassInfo> getConfigurableInfos() {
        return configurableInfos;
    }
}
