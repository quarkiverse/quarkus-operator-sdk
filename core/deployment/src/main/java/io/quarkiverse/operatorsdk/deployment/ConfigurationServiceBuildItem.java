package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.builder.item.SimpleBuildItem;

@SuppressWarnings("rawtypes")
public final class ConfigurationServiceBuildItem extends SimpleBuildItem {

    private final Version version;
    private final Map<String, QuarkusControllerConfiguration> controllerConfigs;

    public ConfigurationServiceBuildItem(Version version, List<QuarkusControllerConfiguration> controllerConfigs) {
        this.version = version;
        this.controllerConfigs = new HashMap<>(controllerConfigs.size());

        controllerConfigs.forEach(c -> this.controllerConfigs.put(c.getName(), c));
    }

    public Version getVersion() {
        return version;
    }

    public Map<String, QuarkusControllerConfiguration> getControllerConfigs() {
        return controllerConfigs;
    }
}
