package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ConfigurationServiceRecorder {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Supplier<QuarkusConfigurationService> configurationServiceSupplier(Version version,
            Map<String, QuarkusControllerConfiguration> configurations,
            CRDGenerationInfo crdInfo, RunTimeOperatorConfiguration runTimeConfiguration,
            BuildTimeOperatorConfiguration buildTimeConfiguration, LaunchMode launchMode) {
        final var maxThreads = runTimeConfiguration.concurrentReconciliationThreads
                .orElse(ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER);
        final var timeout = runTimeConfiguration.terminationTimeoutSeconds
                .orElse(ConfigurationService.DEFAULT_TERMINATION_TIMEOUT_SECONDS);

        configurations.forEach((name, c) -> {
            final var extConfig = runTimeConfiguration.controllers.get(name);
            // first use the operator-level configuration if set
            runTimeConfiguration.namespaces.ifPresent(c::setNamespaces);

            // then override with controller-specific configuration if present
            if (extConfig != null) {
                extConfig.finalizer.ifPresent(c::setFinalizer);
                extConfig.namespaces.ifPresent(c::setNamespaces);
                extConfig.selector.ifPresent(c::setLabelSelector);
                c.setRetryConfiguration(RetryConfigurationResolver.resolve(extConfig.retry));
            }
        });

        return () -> new QuarkusConfigurationService(
                version,
                configurations.values(),
                Arc.container().instance(KubernetesClient.class).get(),
                crdInfo,
                maxThreads,
                timeout,
                Arc.container().instance(ObjectMapper.class).get(),
                Arc.container().instance(Metrics.class).get(),
                shouldStartOperator(buildTimeConfiguration.startOperator, launchMode));
    }

    static boolean shouldStartOperator(Optional<Boolean> fromConfiguration, LaunchMode launchMode) {
        if (fromConfiguration == null || fromConfiguration.isEmpty()) {
            return LaunchMode.TEST != launchMode;
        } else {
            return fromConfiguration.orElse(true);
        }
    }
}
