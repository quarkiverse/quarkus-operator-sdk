package io.quarkiverse.operatorsdk.runtime;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.SerializationFeature;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.quarkus.arc.Arc;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ConfigurationServiceRecorder {

    static final Logger log = Logger.getLogger(ConfigurationServiceRecorder.class.getName());

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Supplier<QuarkusConfigurationService> configurationServiceSupplier(Version version,
            Map<String, QuarkusControllerConfiguration> configurations,
            CRDGenerationInfo crdInfo, RunTimeOperatorConfiguration runTimeConfiguration,
            BuildTimeOperatorConfiguration buildTimeConfiguration, LaunchMode launchMode) {
        final var maxThreads = runTimeConfiguration.concurrentReconciliationThreads
                .orElse(ConfigurationService.DEFAULT_RECONCILIATION_THREADS_NUMBER);
        final var timeout = runTimeConfiguration.terminationTimeoutSeconds
                .orElse(ConfigurationService.DEFAULT_TERMINATION_TIMEOUT_SECONDS);
        final var workflowThreads = runTimeConfiguration.concurrentWorkflowThreads
                .orElse(ConfigurationService.DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER);
        final var cacheSyncTimeout = runTimeConfiguration.cacheSyncTimeout;

        configurations.forEach((name, c) -> {
            final var extConfig = runTimeConfiguration.controllers.get(name);

            // then override with controller-specific configuration if present
            if (extConfig != null) {
                extConfig.finalizer.ifPresent(c::setFinalizer);
                extConfig.selector.ifPresent(c::setLabelSelector);
                extConfig.namespaces.map(HashSet::new).ifPresent(c::setNamespaces);
                c.setRetryConfiguration(RetryConfigurationResolver.resolve(extConfig.retry));
            }

            // set retry to default if it hasn't been set already
            // note that this is a little hackish but we can't set the default version at built time
            // because it will get recorded in the byte code and this would make it harder to
            // override it using the old style (i.e. using setRetryConfiguration) at runtime
            final var retry = c.getRetry();
            if (retry == null) {
                c.setRetryConfiguration(null);
            }

            // if despite all of this, we still haven't set the namespaces, use the operator-level default if it exists
            if (DEFAULT_NAMESPACES_SET.equals(c.getNamespaces())) {
                runTimeConfiguration.namespaces.ifPresent(ns -> c.setNamespaces(new HashSet<>(ns)));
            }
        });

        return () -> {
            // customize fabric8 mapper
            final var mapper = Serialization.jsonMapper();
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            final var container = Arc.container();
            container.select(ObjectMapperCustomizer.class,
                    KubernetesClientSerializationCustomizer.Literal.INSTANCE)
                    .stream()
                    .sorted()
                    .forEach(c -> c.customize(mapper));

            // deactivate leader election in dev mode
            var leaderElectionConfiguration = container.instance(LeaderElectionConfiguration.class).get();
            if (LaunchMode.DEVELOPMENT == launchMode && leaderElectionConfiguration != null) {
                leaderElectionConfiguration = null;
                log.info("Leader election configuration ignored in Dev mode");
            }

            return new QuarkusConfigurationService(
                    version,
                    configurations.values(),
                    container.instance(KubernetesClient.class).get(),
                    crdInfo,
                    maxThreads,
                    workflowThreads,
                    timeout,
                    cacheSyncTimeout,
                    container.instance(Metrics.class).get(),
                    shouldStartOperator(buildTimeConfiguration.startOperator, launchMode),
                    mapper,
                    leaderElectionConfiguration,
                    container.instance(InformerStoppedHandler.class).orElse(null),
                    buildTimeConfiguration.closeClientOnStop,
                    buildTimeConfiguration.stopOnInformerErrorDuringStartup);
        };
    }

    static boolean shouldStartOperator(Optional<Boolean> fromConfiguration, LaunchMode launchMode) {
        if (fromConfiguration == null || fromConfiguration.isEmpty()) {
            return LaunchMode.TEST != launchMode;
        } else {
            return fromConfiguration.orElse(true);
        }
    }
}
