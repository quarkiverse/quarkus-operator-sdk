package io.quarkiverse.operatorsdk.deployment;

import java.util.function.Supplier;

import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ConfigurationServiceBuildItem extends SimpleBuildItem {
    private final Supplier<QuarkusConfigurationService> supplier;

    public ConfigurationServiceBuildItem(Supplier<QuarkusConfigurationService> supplier) {
        this.supplier = supplier;
    }

    public Supplier<QuarkusConfigurationService> getConfigurationService() {
        return supplier;
    }
}
