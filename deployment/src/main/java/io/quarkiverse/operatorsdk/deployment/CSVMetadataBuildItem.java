package io.quarkiverse.operatorsdk.deployment;

import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.CSVMetadataHolder;
import io.quarkus.builder.item.SimpleBuildItem;

public final class CSVMetadataBuildItem extends SimpleBuildItem {
    private final Map<String, CSVMetadataHolder> groupToMetadata;

    public CSVMetadataBuildItem(Map<String, CSVMetadataHolder> groupToMetadata) {
        this.groupToMetadata = groupToMetadata;
    }

    public Map<String, CSVMetadataHolder> getCSVMetadata() {
        return groupToMetadata;
    }
}
