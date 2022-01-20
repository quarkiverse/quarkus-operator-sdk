package io.quarkiverse.operatorsdk.runtime;

import java.io.File;
import java.io.IOException;

import javax.annotation.PreDestroy;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkus.arc.DefaultBean;

@Singleton
public class OperatorProducer {
    private static final YAMLMapper mapper = new YAMLMapper();
    private static final Logger log = Logger.getLogger(OperatorProducer.class.getName());
    private Operator operator;

    @Produces
    @DefaultBean
    @Singleton
    Operator operator(QuarkusConfigurationService configuration, Instance<Reconciler<? extends HasMetadata>> reconcilers) {
        operator = new Operator(configuration.getClient(), configuration);
        for (Reconciler<? extends HasMetadata> reconciler : reconcilers) {
            final var config = configuration.getConfigurationFor(reconciler);
            if (!config.isRegistrationDelayed()) {
                applyCRDIfNeededAndRegister(operator, reconciler, configuration);
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

    public static void applyCRDIfNeededAndRegister(Operator operator, Reconciler<? extends HasMetadata> reconciler,
            QuarkusConfigurationService configuration) {
        final var config = configuration.getConfigurationFor(reconciler);
        final var crdInfo = configuration.getCRDGenerationInfo();
        if (crdInfo.isApplyCRDs()) {
            final var crdName = config.getResourceTypeName();
            // if the CRD just got generated, apply it
            if (crdInfo.shouldApplyCRD(crdName)) {
                applyCRD(operator, crdInfo, crdName);
            }

            // try to register the reconciler, if we get a MissingCRDException, apply the CRD and re-attempt registration
            try {
                operator.register(reconciler);
            } catch (MissingCRDException e) {
                applyCRD(operator, crdInfo, crdName);
                operator.register(reconciler);
            }

        } else {
            operator.register(reconciler);
        }
    }

    private static void applyCRD(Operator operator, CRDGenerationInfo crdInfo, String crdName) {
        try {
            crdInfo.getCRDInfosFor(crdName).forEach((crdVersion, info) -> {
                final var filePath = info.getFilePath();
                final var crdFile = new File(filePath);
                try {
                    final var crd = mapper.readValue(crdFile, getCRDClassFor(crdVersion));
                    apply(operator.getKubernetesClient(), crdVersion, crd);
                    log.infov("Applied {0} CRD named ''{1}'' from {2}", crdVersion, crdName, filePath);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Couldn't read CRD file at " + filePath
                            + " as a " + crdVersion + " CRD for " + crdName, ex);
                }
            });
        } catch (Exception exception) {
            log.debugv(exception, "Couldn't apply ''{0}'' CRD", crdName);
        }
    }

    private static void apply(KubernetesClient client, String v, Object crd) {
        switch (v) {
            case "v1":
                client.apiextensions().v1().customResourceDefinitions().createOrReplace((CustomResourceDefinition) crd);
                break;
            case "v1beta1":
                client.apiextensions().v1beta1().customResourceDefinitions()
                        .createOrReplace((io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition) crd);
                break;
            default:
                throw new IllegalArgumentException("Unknown CRD version: " + v);
        }
    }

    private static Class<?> getCRDClassFor(String v) {
        switch (v) {
            case "v1":
                return CustomResourceDefinition.class;
            case "v1beta1":
                return io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition.class;
            default:
                throw new IllegalArgumentException("Unknown CRD version: " + v);
        }
    }
}
