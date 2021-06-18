package io.quarkiverse.operatorsdk.deployment;

import java.util.List;

import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.QuarkusControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ConfigurationServiceBuildItem extends SimpleBuildItem {

    private final Version version;
    private final List<QuarkusControllerConfiguration> controllerConfigs;
    private final CRDGenerationInfo crdInfo;

    public ConfigurationServiceBuildItem(Version version,
            List<QuarkusControllerConfiguration> controllerConfigs, CRDGenerationInfo crdInfo) {
        this.version = version;
        this.controllerConfigs = controllerConfigs;
        this.crdInfo = crdInfo;
    }

    public Version getVersion() {
        return version;
    }

    public List<QuarkusControllerConfiguration> getControllerConfigs() {
        return controllerConfigs;
    }

    public CRDGenerationInfo getCRDGenerationInfo() {
        return crdInfo;
    }
}
