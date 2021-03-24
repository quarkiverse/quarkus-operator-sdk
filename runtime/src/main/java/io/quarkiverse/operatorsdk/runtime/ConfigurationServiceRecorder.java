package io.quarkiverse.operatorsdk.runtime;

import java.util.List;
import java.util.function.Supplier;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.RetryConfiguration;
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
                extConfig.retry.ifPresent(r -> {
                    long initialInterval = RetryConfiguration.DEFAULT_INITIAL_INTERVAL;
                    double intervalMultiplier = RetryConfiguration.DEFAULT_MULTIPLIER;
                    long maxInterval = RetryConfiguration.DEFAULT.getMaxInterval();
                    if (r.interval.isPresent()) {
                        final var intervalConfiguration = r.interval.get();
                        initialInterval = intervalConfiguration.initial.orElse(RetryConfiguration.DEFAULT_INITIAL_INTERVAL);
                        intervalMultiplier = intervalConfiguration.multiplier.orElse(RetryConfiguration.DEFAULT_MULTIPLIER);
                        maxInterval = intervalConfiguration.max.orElse(RetryConfiguration.DEFAULT.getMaxInterval());
                    }
                    final var retry = new PlainRetryConfiguration(
                            r.maxAttempts.orElse(RetryConfiguration.DEFAULT_MAX_ATTEMPTS), initialInterval,
                            intervalMultiplier, maxInterval);
                    c.setRetryConfiguration(retry);
                });
            }
        });

    }
}
