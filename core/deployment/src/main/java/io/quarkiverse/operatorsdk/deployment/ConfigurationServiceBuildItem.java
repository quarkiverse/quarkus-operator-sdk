package io.quarkiverse.operatorsdk.deployment;

import java.util.List;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ConfigurationServiceBuildItem extends SimpleBuildItem {

    private final Version version;
    private final List<QuarkusControllerConfiguration> controllerConfigs;

    public ConfigurationServiceBuildItem(Version version, List<QuarkusControllerConfiguration> controllerConfigs) {
        this.version = version;
        this.controllerConfigs = controllerConfigs;
    }

    public Version getVersion() {
        return version;
    }

    public List<QuarkusControllerConfiguration> getControllerConfigs() {
        return controllerConfigs;
    }
}
