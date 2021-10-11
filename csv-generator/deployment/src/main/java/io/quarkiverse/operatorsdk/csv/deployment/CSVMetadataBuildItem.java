package io.quarkiverse.operatorsdk.csv.deployment;

import java.util.Map;

import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;
import io.quarkus.builder.item.SimpleBuildItem;

public final class CSVMetadataBuildItem extends SimpleBuildItem {
    private final Map<String, CSVMetadataHolder> groupToMetadata;
    private final Map<String, AugmentedCustomResourceInfo> crdNameToAugmentedCRInfo;

    public CSVMetadataBuildItem(Map<String, CSVMetadataHolder> groupToMetadata,
            Map<String, AugmentedCustomResourceInfo> crdNameToAugmentedCRInfo) {
        this.groupToMetadata = groupToMetadata;
        this.crdNameToAugmentedCRInfo = crdNameToAugmentedCRInfo;
    }

    public Map<String, CSVMetadataHolder> getCSVMetadata() {
        return groupToMetadata;
    }

    public Map<String, AugmentedCustomResourceInfo> getAugmentedCustomResourceInfos() {
        return crdNameToAugmentedCRInfo;
    }
}
