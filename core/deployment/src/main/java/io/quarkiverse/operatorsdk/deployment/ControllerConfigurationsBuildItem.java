package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkus.builder.item.SimpleBuildItem;

@SuppressWarnings("rawtypes")
public final class ControllerConfigurationsBuildItem extends SimpleBuildItem {

    private final Map<String, QuarkusControllerConfiguration<?>> controllerConfigs;

    public ControllerConfigurationsBuildItem(List<QuarkusControllerConfiguration> controllerConfigs) {
        this.controllerConfigs = new HashMap<>(controllerConfigs.size());

        controllerConfigs.forEach(c -> this.controllerConfigs.put(c.getName(), c));
    }

    public Map<String, QuarkusControllerConfiguration<?>> getControllerConfigs() {
        return controllerConfigs;
    }
}
