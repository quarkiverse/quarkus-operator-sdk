package io.quarkiverse.operatorsdk.deployment;

import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.builder.item.SimpleBuildItem;

public final class VersionBuildItem extends SimpleBuildItem {
    private final Version version;

    public VersionBuildItem(Version version) {
        this.version = version;
    }

    public Version getVersion() {
        return version;
    }
}
