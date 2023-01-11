package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.CRDUtils.applyCRD;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class OperatorProducer {
    private static final Logger log = LoggerFactory.getLogger(OperatorProducer.class);

    /*
     * Note that using ApplicationScoped instead of Singleton will create a proxy, which instantiates the Operator using the
     * default constructor resulting in the configuration service provider being initialized, thus leading to failure when it is
     * set again.
     */
    @Produces
    @DefaultBean
    @Singleton
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

        // make sure we reset the ConfigurationService in case we restarted in dev mode
        ConfigurationServiceProvider.reset();

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
