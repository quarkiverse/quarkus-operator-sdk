package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.function.Supplier;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ConfigurationServiceRecorder {

    public Supplier<QuarkusConfigurationService> configurationServiceSupplier(
            Version version,
            List<ControllerConfiguration> configurations,
            boolean validateCustomResources) {
        return () -> new QuarkusConfigurationService(
                version,
                configurations,
                Arc.container().instance(KubernetesClient.class).get(),
                validateCustomResources);
    }

    public void updateConfigurations(Supplier<QuarkusConfigurationService> service,
            RunTimeOperatorConfiguration runTimeConfiguration) {
        service.get().configurations().forEach(c -> {
            final var extConfig = runTimeConfiguration.controllers.get(c.getName());
            if (extConfig != null) {
                extConfig.finalizer.ifPresent(c::setFinalizer);
                extConfig.namespaces.ifPresent(c::setNamespaces);
                c.setRetryConfiguration(RetryConfigurationResolver.resolve(extConfig.retry));
            }
        });

    }
}
