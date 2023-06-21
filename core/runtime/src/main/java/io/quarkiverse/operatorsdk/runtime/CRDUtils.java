package io.quarkiverse.operatorsdk.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;

public final class CRDUtils {

    private static final Logger LOGGER = Logger.getLogger(CRDUtils.class.getName());

    private CRDUtils() {
    }

    public static void applyCRD(KubernetesClient client, CRDGenerationInfo crdInfo, String crdName) {
        try {
            crdInfo.getCRDInfosFor(crdName).forEach((crdVersion, info) -> {
                final var filePath = Path.of(info.getFilePath());
                try {
                    final var crd = client.getKubernetesSerialization()
                            .unmarshal(Files.newInputStream(filePath), getCRDClassFor(crdVersion));
                    apply(client, crdVersion, crd);
                    LOGGER.infov("Applied {0} CRD named ''{1}'' from {2}", crdVersion, crdName, filePath);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Couldn't read CRD file at " + filePath
                            + " as a " + crdVersion + " CRD for " + crdName, ex);
                }
            });
        } catch (Exception exception) {
            LOGGER.debugv(exception, "Couldn't apply ''{0}'' CRD", crdName);
        }
    }

    private static void apply(KubernetesClient client, String v, Object crd) {
        switch (v) {
            case "v1":
                client.apiextensions().v1().customResourceDefinitions()
                        .resource((CustomResourceDefinition) crd)
                        .serverSideApply();
                break;
            case "v1beta1":
                client.apiextensions().v1beta1().customResourceDefinitions()
                        .resource((io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition) crd)
                        .serverSideApply();
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
