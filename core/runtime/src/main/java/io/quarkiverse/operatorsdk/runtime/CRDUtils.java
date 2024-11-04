package io.quarkiverse.operatorsdk.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;

public final class CRDUtils {

    private static final Logger LOGGER = Logger.getLogger(CRDUtils.class.getName());
    private static final KubernetesSerialization SERIALIZATION = new KubernetesSerialization(new ObjectMapper(), false);
    public final static String DEFAULT_CRD_SPEC_VERSION = "v1";
    public static final String V1BETA1_CRD_SPEC_VERSION = "v1beta1";

    private CRDUtils() {
    }

    public static void applyCRD(KubernetesClient client, CRDGenerationInfo crdInfo, String crdName) {
        try {
            crdInfo.getCRDInfosFor(crdName).forEach((crdVersion, info) -> {
                final var filePath = Path.of(info.getFilePath());
                try {
                    final var crd = loadFrom(filePath, client.getKubernetesSerialization(), getCRDClassFor(crdVersion));
                    apply(client, crdVersion, crd);
                    LOGGER.infov("Applied {0} CRD named ''{1}'' from {2}", crdVersion, crdName, filePath);
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Couldn't read CRD file at " + filePath
                            + " as a " + crdVersion + " CRD for " + crdName, ex);
                }
            });
        } catch (Exception exception) {
            LOGGER.warnv(exception, "Couldn't apply ''{0}'' CRD", crdName);
        }
    }

    private static <T> T loadFrom(Path crdPath, KubernetesSerialization serialization, Class<T> crdClass) throws IOException {
        serialization = serialization == null ? SERIALIZATION : serialization;
        return serialization.unmarshal(Files.newInputStream(crdPath), crdClass);
    }

    public static CustomResourceDefinition loadFrom(Path crdPath) throws IOException {
        final var crd = loadFrom(crdPath, null, CustomResourceDefinition.class);
        final var crdVersion = crd.getApiVersion().split("/")[1];
        if (!DEFAULT_CRD_SPEC_VERSION.equals(crdVersion)) {
            LOGGER.warnv(
                    "CRD at {0} was loaded as a {1} CRD but is defined as using {2} CRD spec version. While things might still work as expected, we recommend that you only use CRDs using the {1} CRD spec version.",
                    crdPath, DEFAULT_CRD_SPEC_VERSION, crdVersion);
        }
        return crd;
    }

    private static void apply(KubernetesClient client, String v, Object crd) {
        switch (v) {
            case DEFAULT_CRD_SPEC_VERSION:
                final var resource = client.apiextensions().v1().customResourceDefinitions()
                        .resource((CustomResourceDefinition) crd);
                if (resource.get() != null) {
                    resource.update();
                } else {
                    resource.create();
                }
                break;
            case V1BETA1_CRD_SPEC_VERSION:
                final var legacyResource = client.apiextensions().v1beta1().customResourceDefinitions()
                        .resource((io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition) crd);
                if (legacyResource.get() != null) {
                    legacyResource.update();
                } else {
                    legacyResource.create();
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown CRD version: " + v);
        }
    }

    private static Class<?> getCRDClassFor(String v) {
        return switch (v) {
            case DEFAULT_CRD_SPEC_VERSION -> CustomResourceDefinition.class;
            case V1BETA1_CRD_SPEC_VERSION ->
                io.fabric8.kubernetes.api.model.apiextensions.v1beta1.CustomResourceDefinition.class;
            default -> throw new IllegalArgumentException("Unknown CRD version: " + v);
        };
    }
}
