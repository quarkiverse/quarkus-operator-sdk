package io.quarkiverse.operatorsdk.runtime;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkiverse.operatorsdk.runtime.api.ConfigurableReconciler;
import io.quarkus.arc.DefaultBean;

@Singleton
public class OperatorProducer {
    private static final Logger log = Logger.getLogger(OperatorProducer.class);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <P extends HasMetadata> void register(QuarkusConfigurationService configurationService,
            Reconciler<P> reconciler, Operator operator) {
        if (reconciler instanceof ConfigurableReconciler configurable) {
            final var conf = configurationService.getConfigurationFor(reconciler);
            if (conf != null) {
                final var override = ControllerConfigurationOverrider.override(conf);
                configurable.updateConfigurationFrom(override);
                final var updated = override.build();
                final Duration maxReconciliationInterval = updated.maxReconciliationInterval()
                        .orElse(Duration.ofHours(MaxReconciliationInterval.DEFAULT_INTERVAL));
                final var qConf = new QuarkusControllerConfiguration(updated.getInformerConfig(), updated.getName(),
                        updated.isGenerationAware(), conf.getAssociatedReconcilerClassName(), updated.getRetry(),
                        updated.getRateLimiter(), maxReconciliationInterval, updated.getFinalizerName(), updated.fieldManager(),
                        conf.getWorkflow(), conf.getResourceTypeName(), conf.getResourceClass());
                qConf.setParent(configurationService);
                operator.register(reconciler, qConf);
                return;
            }
        }
        operator.register(reconciler);
    }

    /**
     * Produces an application-scoped Operator, given the provided configuration and detected reconcilers. We previously
     * produced the operator instance as singleton-scoped but this prevents being able to inject the operator instance in
     * reconcilers (which we don't necessarily recommend but might be needed for corner cases) due to an infinite loop.
     * ApplicationScoped being proxy-based allows for breaking the cycle, thus allowing the operator-reconciler parent-child
     * relation to be handled by CDI.
     *
     * @param configuration the {@link QuarkusConfigurationService} providing the configuration for the operator and controllers
     * @param reconcilers the detected {@link Reconciler} implementations
     * @return a properly configured {@link Operator} instance
     */
    @Produces
    @DefaultBean
    @ApplicationScoped
    Operator operator(QuarkusConfigurationService configuration, Instance<Reconciler<? extends HasMetadata>> reconcilers) {
        if (configuration.getVersion() instanceof Version version) {
            log.infof("Quarkus Java Operator SDK extension %s", version.getExtensionCompleteVersion());
        }

        // if some CRDs just got generated and need to be applied, apply them
        final var crdInfo = configuration.getCRDGenerationInfo();
        if (crdInfo.isApplyCRDs()) {
            for (String generatedCrdName : crdInfo.getGenerated()) {
                CRDUtils.applyCRD(configuration.getKubernetesClient(), crdInfo, generatedCrdName);
            }
        }

        Operator operator = new Operator(configuration);
        for (Reconciler<? extends HasMetadata> reconciler : reconcilers) {
            register(configuration, reconciler, operator);
        }

        // if we set a termination timeout, install a shutdown hook
        final var terminationTimeoutSeconds = configuration.getTerminationTimeoutSeconds();
        if (QuarkusConfigurationService.UNSET_TERMINATION_TIMEOUT_SECONDS != terminationTimeoutSeconds) {
            operator.installShutdownHook(Duration.ofSeconds(terminationTimeoutSeconds));
        }

        return operator;
    }
}
