package io.quarkiverse.operatorsdk.bundle.deployment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkiverse.operatorsdk.bundle.deployment.builders.AnnotationsManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.BundleDockerfileManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.CsvManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.ManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.runtime.BundleGenerationConfiguration;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkus.arc.impl.Sets;

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

    public static Set<ManifestsBuilder> prepareGeneration(
            BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration,
            Map<String, AugmentedResourceInfo> info,
            Map<String, CSVMetadataHolder> csvMetadata) {
        final var labels = generateBundleLabels(bundleConfiguration, operatorConfiguration);
        final var builders = new ConcurrentHashMap<String, Set<ManifestsBuilder>>(7);
        return info.values().parallelStream()
                .flatMap(cri -> builders.computeIfAbsent(cri.getCsvGroupName(),
                        s -> Sets.of(new CsvManifestsBuilder(cri, csvMetadata),
                                new AnnotationsManifestsBuilder(cri, labels),
                                new BundleDockerfileManifestsBuilder(cri, labels)))
                        .stream())
                .collect(Collectors.toSet());
    }

    private static final Map<String, String> generateBundleLabels(BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration) {
        Map<String, String> values = new HashMap<>();
        for (String version : operatorConfiguration.crd.versions) {
            values.put(join(PREFIX_ANNOTATION, CHANNEL, DEFAULT, version),
                    bundleConfiguration.defaultChannel.orElse(bundleConfiguration.channels.get(0)));
            values.put(join(PREFIX_ANNOTATION, CHANNELS, version),
                    bundleConfiguration.channels.stream().collect(Collectors.joining(COMMA)));
            values.put(join(PREFIX_ANNOTATION, MANIFESTS, version), MANIFESTS + SLASH);
            values.put(join(PREFIX_ANNOTATION, MEDIA_TYPE, version), REGISTRY_PLUS + version);
            values.put(join(PREFIX_ANNOTATION, METADATA, version), METADATA + SLASH);
            values.put(join(PREFIX_ANNOTATION, PACKAGE, version), bundleConfiguration.packageName);
        }

        return values;
    }

    private static final String join(String... elements) {
        return Stream.of(elements).collect(Collectors.joining(DOT));
    }
}
