package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.function.Supplier;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ConfigurationServiceRecorder {

    public Supplier<ConfigurationService> configurationServiceSupplier(
            Version version,
            boolean validateCustomResources) {
        return () -> new QuarkusConfigurationService(
                version,
                Arc.container().instance(KubernetesClient.class).get(),
                validateCustomResources);
    }

    public void registerControllerConfigurations(QuarkusConfigurationService service,
            List<ControllerConfiguration> controllerConfigs) {
        service.register(controllerConfigs);
    }
}
