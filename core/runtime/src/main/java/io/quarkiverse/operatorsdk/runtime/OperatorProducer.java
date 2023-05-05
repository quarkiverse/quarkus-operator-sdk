package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.CRDUtils.applyCRD;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkus.arc.DefaultBean;

@Singleton
public class OperatorProducer {
    private static final Logger log = LoggerFactory.getLogger(OperatorProducer.class);

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
        if (configuration.getVersion() instanceof Version) {
            final var version = ((Version) configuration.getVersion());
            final var branch = !version.getExtensionBranch().equals(Version.UNKNOWN)
                    ? " on branch: " + version.getExtensionBranch()
                    : "";
            log.info("Quarkus Java Operator SDK extension {} (commit: {}{}) built on {}",
                    version.getExtensionVersion(),
                    version.getExtensionCommit(), branch, version.getExtensionBuildTime());
        }

        // if some CRDs just got generated and need to be applied, apply them
        final var crdInfo = configuration.getCRDGenerationInfo();
        if (crdInfo.isApplyCRDs()) {
            for (String generatedCrdName : crdInfo.getGenerated()) {
                applyCRD(configuration.getClient(), crdInfo, generatedCrdName);
            }
        }

        Operator operator = new Operator(configuration.getClient(), configuration);
        for (Reconciler<? extends HasMetadata> reconciler : reconcilers) {
            operator.register(reconciler);
        }

        return operator;
    }
}
