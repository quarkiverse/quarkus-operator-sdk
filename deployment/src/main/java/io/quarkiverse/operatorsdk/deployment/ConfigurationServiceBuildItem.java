package io.quarkiverse.operatorsdk.deployment;

import java.util.List;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ConfigurationServiceBuildItem extends SimpleBuildItem {

    private final Version version;
    private final List<QuarkusControllerConfiguration> controllerConfigs;
    private final boolean validateCustomResources;

    public ConfigurationServiceBuildItem(Version version,
            List<QuarkusControllerConfiguration> controllerConfigs, boolean validateCustomResources) {
        this.version = version;
        this.controllerConfigs = controllerConfigs;
        this.validateCustomResources = validateCustomResources;
    }

    public Version getVersion() {
        return version;
    }

    public List<QuarkusControllerConfiguration> getControllerConfigs() {
        return controllerConfigs;
    }

    public boolean isValidateCustomResources() {
        return validateCustomResources;
    }
}
