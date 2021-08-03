package io.quarkiverse.operatorsdk.runtime;

import java.io.File;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.quarkus.arc.DefaultBean;

@Singleton
public class OperatorProducer {
    private static final YAMLMapper mapper = new YAMLMapper();
    private static final Logger log = Logger.getLogger(OperatorProducer.class.getName());

    @Produces
    @DefaultBean
    @Singleton
    Operator operator(KubernetesClient client, QuarkusConfigurationService configuration,
            Instance<ResourceController<? extends CustomResource>> controllers) {
        final var operator = new Operator(client, configuration);
        for (ResourceController<? extends CustomResource> controller : controllers) {
            QuarkusControllerConfiguration<? extends CustomResource> config = configuration
                    .getConfigurationFor(controller);
            if (!config.isRegistrationDelayed()) {
                applyCRDIfNeededAndRegister(operator, controller, configuration);
            }
        }
        return operator;
    }

    public static void applyCRDIfNeededAndRegister(Operator operator, ResourceController<? extends CustomResource> controller,
            QuarkusConfigurationService configuration) {
        QuarkusControllerConfiguration<? extends CustomResource> config = configuration.getConfigurationFor(controller);
        final var crdInfo = configuration.getCRDGenerationInfo();
        if (crdInfo.isApplyCRDs()) {
            final var crdName = config.getCRDName();
            // if the CRD just got generated, apply it
            if (crdInfo.shouldApplyCRD(crdName)) {
                applyCRD(operator, crdInfo, crdName);
            }

            // try to register the controller, if we get a MissingCRDException, apply the CRD and re-attempt registration
            try {
                operator.register(controller);
            } catch (MissingCRDException e) {
                applyCRD(operator, crdInfo, crdName);
                operator.register(controller);
            }

        } else {
            operator.register(controller);
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
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
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
