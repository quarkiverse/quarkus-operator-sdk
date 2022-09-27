package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkus.builder.item.SimpleBuildItem;

public final class CSVMetadataBuildItem extends SimpleBuildItem {
    private final Map<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> csvGroups;

    public CSVMetadataBuildItem(Map<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> csvGroups) {
        this.csvGroups = csvGroups;
    }

    public Map<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> getCsvGroups() {
        return csvGroups;
    }
}
