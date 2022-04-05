package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.List;
import java.util.Map;

import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkus.builder.item.SimpleBuildItem;

public final class CSVMetadataBuildItem extends SimpleBuildItem {
    private final Map<CSVMetadataHolder, List<AugmentedResourceInfo>> csvGroups;

    public CSVMetadataBuildItem(Map<CSVMetadataHolder, List<AugmentedResourceInfo>> csvGroups) {
        this.csvGroups = csvGroups;
    }

    public Map<CSVMetadataHolder, List<AugmentedResourceInfo>> getCsvGroups() {
        return csvGroups;
    }
}
