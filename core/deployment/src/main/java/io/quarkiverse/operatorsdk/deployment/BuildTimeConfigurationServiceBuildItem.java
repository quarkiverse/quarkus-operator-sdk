package io.quarkiverse.operatorsdk.deployment;

import io.quarkiverse.operatorsdk.runtime.BuildTimeConfigurationService;
import io.quarkus.builder.item.SimpleBuildItem;

public final class BuildTimeConfigurationServiceBuildItem extends SimpleBuildItem {
    private final BuildTimeConfigurationService service;

    BuildTimeConfigurationServiceBuildItem(BuildTimeConfigurationService service) {
        this.service = service;
    }

    public BuildTimeConfigurationService getConfigurationService() {
        return service;
    }
}
