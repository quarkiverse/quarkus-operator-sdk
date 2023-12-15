package io.quarkiverse.operatorsdk.runtime;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static io.quarkiverse.operatorsdk.runtime.Constants.QOSDK_USE_BUILDTIME_NAMESPACES_SET;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigUtils;

@Recorder
public class ConfigurationServiceRecorder {

    static final Logger log = Logger.getLogger(ConfigurationServiceRecorder.class.getName());

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Supplier<QuarkusConfigurationService> configurationServiceSupplier(Version version,
            Map<String, QuarkusControllerConfiguration> configurations,
            CRDGenerationInfo crdInfo, RunTimeOperatorConfiguration runTimeConfiguration,
            BuildTimeOperatorConfiguration buildTimeConfiguration) {
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
                c.setRetryConfiguration(RetryConfigurationResolver.resolve(extConfig.retry));
                setNamespacesFromRuntime(c, extConfig.namespaces);
            }

            // set retry to default if it hasn't been set already
            // note that this is a little hackish but we can't set the default version at built time
            // because it will get recorded in the byte code and this would make it harder to
            // override it using the old style (i.e. using setRetryConfiguration) at runtime
            final var retry = c.getRetry();
            if (retry == null) {
                c.setRetryConfiguration(null);
            }

            // if the namespaces weren't set on controller level or as an annotation, use the operator-level configuration if it exists
            if (!c.isWereNamespacesSet()) {
                setNamespacesFromRuntime(c, runTimeConfiguration.namespaces);
            }
        });

        return () -> {
            final var container = Arc.container();

            // deactivate leader election in dev mode
            LeaderElectionConfiguration leaderElectionConfiguration = null;
            final var profiles = ConfigUtils.getProfiles();
            if (profiles.stream().anyMatch(buildTimeConfiguration.activateLeaderElectionForProfiles::contains)) {
                leaderElectionConfiguration = container.instance(LeaderElectionConfiguration.class).get();
            } else {
                log.info("Leader election deactivated because it is only activated for "
                        + buildTimeConfiguration.activateLeaderElectionForProfiles
                        + " profiles. Currently active profiles: " + profiles);
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
                    buildTimeConfiguration.startOperator,
                    leaderElectionConfiguration,
                    container.instance(InformerStoppedHandler.class).orElse(null),
                    buildTimeConfiguration.closeClientOnStop,
                    buildTimeConfiguration.stopOnInformerErrorDuringStartup,
                    buildTimeConfiguration.enableSSA);
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "OptionalUsedAsFieldOrParameterType" })
    private static void setNamespacesFromRuntime(QuarkusControllerConfiguration controllerConfig,
            Optional<List<String>> runtimeNamespaces) {
        // The namespaces field has a default value so that we are able to detect if the configuration value is set to "".
        // Setting the value to "" will reset the configuration and result in an empty Optional.
        // Not setting the value at all will result in the default being applied, which we can test for.
        if (runtimeNamespaces.isPresent()) {
            final var namespaces = new HashSet<>(runtimeNamespaces.get());
            // If it's not the default value, use it because it was set.
            // If it is the default value, ignore it and let any build time config be used.
            if (!QOSDK_USE_BUILDTIME_NAMESPACES_SET.equals(namespaces)) {
                controllerConfig.setNamespaces(namespaces);
            }
        } else {
            // Value has been explicitly reset (value was empty string), use all namespaces mode
            controllerConfig.setNamespaces(DEFAULT_NAMESPACES_SET);
        }
    }
}
