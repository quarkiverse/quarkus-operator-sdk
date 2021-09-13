package io.quarkiverse.operatorsdk.deployment;

import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkus.builder.item.SimpleBuildItem;

public final class GeneratedCRDInfoBuildItem extends SimpleBuildItem {
    private final CRDGenerationInfo crdInfo;

    public GeneratedCRDInfoBuildItem(CRDGenerationInfo crdInfo) {
        this.crdInfo = crdInfo;
    }

    public CRDGenerationInfo getCRDGenerationInfo() {
        return crdInfo;
    }
}
