package io.quarkiverse.operatorsdk.bundle.deployment;

import io.quarkiverse.operatorsdk.runtime.CRDInfos;
import io.quarkus.builder.item.SimpleBuildItem;

public final class UnownedCRDInfoBuildItem extends SimpleBuildItem {
    private final CRDInfos crds;

    public UnownedCRDInfoBuildItem(CRDInfos crds) {
        this.crds = crds;
    }

    public CRDInfos getCRDs() {
        return crds;
    }
}
