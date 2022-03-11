package io.quarkiverse.operatorsdk.runtime;

import static io.quarkiverse.operatorsdk.runtime.CRDUtils.applyCRD;

import javax.annotation.PreDestroy;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkus.arc.DefaultBean;

@Singleton
public class OperatorProducer {

    private Operator operator;

    @Produces
    @DefaultBean
    @Singleton
    Operator operator(QuarkusConfigurationService configuration, Instance<Reconciler<? extends HasMetadata>> reconcilers) {
        operator = new Operator(configuration.getClient(), configuration);
        for (Reconciler<? extends HasMetadata> reconciler : reconcilers) {
            final var config = configuration.getConfigurationFor(reconciler);
            if (!config.isRegistrationDelayed()) {
                applyCRDAndRegister(operator, reconciler, configuration);
            }
        }

        return operator;
    }

    @PreDestroy
    public void destroy() {
        if (operator != null) {
            operator.stop();
        }
    }

    public static void applyCRDAndRegister(Operator operator, Reconciler<? extends HasMetadata> reconciler,
            QuarkusConfigurationService configuration) {
        final var config = configuration.getConfigurationFor(reconciler);
        final var crdInfo = configuration.getCRDGenerationInfo();
        final var crdName = config.getResourceTypeName();
        // if the CRD just got generated, apply it
        if (crdInfo.shouldApplyCRD(crdName)) {
            applyCRD(operator.getKubernetesClient(), crdInfo, crdName);
        }

        // try to register the reconciler, if we get a MissingCRDException, apply the CRD and re-attempt registration
        try {
            operator.register(reconciler);
        } catch (MissingCRDException e) {
            applyCRD(operator.getKubernetesClient(), crdInfo, crdName);
            operator.register(reconciler);
        }
    }
}
