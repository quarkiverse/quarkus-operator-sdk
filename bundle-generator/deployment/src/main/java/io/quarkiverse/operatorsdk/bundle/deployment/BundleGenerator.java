package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.quarkiverse.operatorsdk.bundle.deployment.builders.AnnotationsManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.BundleDockerfileManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.CsvManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.ManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.runtime.BundleGenerationConfiguration;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;

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

    public static Set<ManifestsBuilder> prepareGeneration(ApplicationInfoBuildItem configuration,
            BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration,
            Map<CSVMetadataHolder, List<AugmentedResourceInfo>> csvGroups) {
        final var labels = generateBundleLabels(configuration, bundleConfiguration, operatorConfiguration);
        return csvGroups.entrySet().stream()
                .flatMap(entry -> Set.of(new CsvManifestsBuilder(entry.getKey(), entry.getValue()),
                        new AnnotationsManifestsBuilder(entry.getKey(), labels),
                        new BundleDockerfileManifestsBuilder(entry.getKey(), labels)).stream())
                .collect(Collectors.toSet());
    }

    private static Map<String, String> generateBundleLabels(
            ApplicationInfoBuildItem applicationConfiguration,
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
            values.put(join(PREFIX_ANNOTATION, PACKAGE, version), bundleConfiguration.packageName
                    .orElse(applicationConfiguration.getName()));
        }

        return values;
    }

    private static String join(String... elements) {
        return String.join(DOT, elements);
    }
}
