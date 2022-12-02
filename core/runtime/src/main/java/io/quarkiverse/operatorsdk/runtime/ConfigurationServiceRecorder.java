package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.SerializationFeature;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.quarkiverse.operatorsdk.common.RuntimeConfigurationUtils;
import io.quarkus.arc.Arc;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.common.utils.StringUtil;

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
        final var workflowThreads = runTimeConfiguration.concurrentWorkflowThreads
                .orElse(ConfigurationService.DEFAULT_WORKFLOW_EXECUTOR_THREAD_NUMBER);
        final var cacheSyncTimeout = runTimeConfiguration.cacheSyncTimeout;

        configurations.forEach((name, c) -> {
            final var extConfig = runTimeConfiguration.controllers.get(name);

            // then override with controller-specific configuration if present
            if (extConfig != null) {
                extConfig.finalizer.ifPresent(c::setFinalizer);
                extConfig.namespaces.ifPresent(c::setNamespaces);
                extConfig.selector.ifPresent(c::setLabelSelector);
                c.setRetryConfiguration(RetryConfigurationResolver.resolve(extConfig.retry));
            }

            // check if we need to expand variable names from namespaces
            if (c.isNamespaceExpansionRequired()) {
                final var expandedNS = ((QuarkusControllerConfiguration<?>) c).getNamespaces().stream()
                        .map(RuntimeConfigurationUtils::expandedValueFrom).collect(Collectors.toSet());
                c.setNamespaces(expandedNS);
            }

            // replace already set namespaces if there is an env variable overriding the value
            final var propName = "quarkus.operator-sdk.controllers." + name + ".namespaces";
            final var envVarName = StringUtil.replaceNonAlphanumericByUnderscores(
                    propName.toUpperCase());
            final var nsFromEnv = System.getProperty(envVarName);
            if (nsFromEnv != null) {
                final var namespaces = RuntimeConfigurationUtils.stringPropValueAsSet(nsFromEnv);
                c.setNamespaces(namespaces);
            }

            // if despite all of this, we still haven't set the namespaces, use the operator-level default if it exists
            if (Constants.DEFAULT_NAMESPACES_SET.equals(c.getNamespaces())) {
                runTimeConfiguration.namespaces.ifPresent(c::setNamespaces);
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
                    container.instance(LeaderElectionConfiguration.class).orElse(null),
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
