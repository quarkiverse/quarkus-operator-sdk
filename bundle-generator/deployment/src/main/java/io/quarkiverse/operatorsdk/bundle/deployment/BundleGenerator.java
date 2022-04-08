package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.quarkiverse.operatorsdk.bundle.deployment.builders.AnnotationsManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.BundleDockerfileManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.CsvManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.CustomResourceManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.ManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.runtime.BundleGenerationConfiguration;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;

public class BundleGenerator {

    public static final String MANIFESTS = "manifests";

    private static final String DOT = ".";
    private static final String COMMA = ",";
    private static final String DEFAULT = "default";
    private static final String METADATA = "metadata";
    private static final String SLASH = "/";
    private static final String REGISTRY_PLUS = "registry+";
    private static final String PACKAGE = "package";
    private static final String MEDIA_TYPE = "mediatype";
    private static final String CHANNEL = "channel";
    private static final String CHANNELS = "channels";
    private static final String PREFIX_ANNOTATION = "operators.operatorframework.io.bundle";

    private BundleGenerator() {
    }

    public static List<ManifestsBuilder> prepareGeneration(BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration,
            Map<CSVMetadataHolder, List<AugmentedResourceInfo>> csvGroups, List<CRDInfo> crds) {
        List<ManifestsBuilder> builders = new ArrayList<>();
        for (Map.Entry<CSVMetadataHolder, List<AugmentedResourceInfo>> entry : csvGroups.entrySet()) {
            final var labels = generateBundleLabels(entry.getKey(), bundleConfiguration, operatorConfiguration);

            builders.add(new CsvManifestsBuilder(entry.getKey(), entry.getValue()));
            builders.add(new AnnotationsManifestsBuilder(entry.getKey(), labels));
            builders.add(new BundleDockerfileManifestsBuilder(entry.getKey(), labels));
            entry.getValue().stream()
                    .map(controller -> findOwnedCustomResource(controller, crds))
                    .filter(Objects::nonNull)
                    .map(crd -> new CustomResourceManifestsBuilder(entry.getKey(), crd))
                    .forEach(builders::add);
        }

        return builders;
    }

    private static CRDInfo findOwnedCustomResource(AugmentedResourceInfo controller, List<CRDInfo> crds) {
        for (CRDInfo crd : crds) {
            if (crd.getCrdName().startsWith(controller.getResourceFullName())) {
                return crd;
            }
        }

        return null;
    }

    private static Map<String, String> generateBundleLabels(CSVMetadataHolder csvMetadata,
            BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration) {
        Map<String, String> values = new HashMap<>();
        for (String version : operatorConfiguration.crd.versions) {
            values.put(join(PREFIX_ANNOTATION, CHANNEL, DEFAULT, version),
                    bundleConfiguration.defaultChannel.orElse(bundleConfiguration.channels.get(0)));
            values.put(join(PREFIX_ANNOTATION, CHANNELS, version), String.join(COMMA, bundleConfiguration.channels));
            values.put(join(PREFIX_ANNOTATION, MANIFESTS, version), MANIFESTS + SLASH);
            values.put(join(PREFIX_ANNOTATION, MEDIA_TYPE, version), REGISTRY_PLUS + version);
            values.put(join(PREFIX_ANNOTATION, METADATA, version), METADATA + SLASH);
            values.put(join(PREFIX_ANNOTATION, PACKAGE, version), csvMetadata.name);
        }

        return values;
    }

    private static String join(String... elements) {
        return String.join(DOT, elements);
    }
}
