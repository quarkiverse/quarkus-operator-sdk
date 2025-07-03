package io.quarkiverse.operatorsdk.deployment;

import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.kubernetes.spi.KubernetesEnvBuildItem;

public class AddNamespaceEnvVarBuildStep {
    @BuildStep
    void addNamespaceEnvVar(ControllerConfigurationsBuildItem controllers,
            BuildProducer<KubernetesEnvBuildItem> envVarProducer) {
        controllers.getControllerConfigs().values().forEach(config -> {
            final var key = ConfigurationUtils.getNamespacesPropertyName(config.getName(), true);
            final var value = String.join(",", config.getInformerConfig().getNamespaces());
            envVarProducer.produce(KubernetesEnvBuildItem.createSimpleVar(key, value, null));
        });
    }
}
