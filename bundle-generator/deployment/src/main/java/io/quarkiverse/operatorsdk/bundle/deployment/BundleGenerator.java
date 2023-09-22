package io.quarkiverse.operatorsdk.bundle.deployment;

import java.nio.file.Path;
import java.util.*;

import org.jboss.logging.Logger;

import io.quarkiverse.operatorsdk.bundle.deployment.builders.AnnotationsManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.BundleDockerfileManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.CsvManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.CustomResourceManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.deployment.builders.ManifestsBuilder;
import io.quarkiverse.operatorsdk.bundle.runtime.BundleGenerationConfiguration;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkiverse.operatorsdk.runtime.Version;
import io.quarkus.container.util.PathsUtil;

public class BundleGenerator {

    private static final Logger log = Logger.getLogger(BundleGenerator.class);

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
    private static final String BUNDLE_PREFIX = "operators.operatorframework.io.bundle";
    private static final String METRICS_PREFIX = "operators.operatorframework.io.metrics";
    private static final String ANNOTATIONS_VERSION = "v1";
    private static final String BUILDER = "builder";
    private static final String METRICS_V1 = "metrics+v1";
    private static final String PROJECT_LAYOUT = "project_layout";
    private static final String LAYOUT_V1_ALPHA = "quarkus.javaoperatorsdk.io/v1-alpha";
    private static final String QOSDK = "qosdk-bundle-generator/";

    private BundleGenerator() {
    }

    public static List<ManifestsBuilder> prepareGeneration(BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration, Version version,
            Map<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> csvGroups, Map<String, CRDInfo> crds,
            Path outputDirectory) {
        List<ManifestsBuilder> builders = new ArrayList<>();
        for (Map.Entry<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> entry : csvGroups.entrySet()) {
            final var csvMetadata = entry.getKey();
            final var labels = generateBundleLabels(csvMetadata, bundleConfiguration, version);

            final var mainSourcesRoot = PathsUtil.findMainSourcesRoot(outputDirectory);
            final var csvBuilder = new CsvManifestsBuilder(csvMetadata, operatorConfiguration, entry.getValue(),
                    mainSourcesRoot != null ? mainSourcesRoot.getKey() : null);
            builders.add(csvBuilder);
            builders.add(new AnnotationsManifestsBuilder(csvMetadata, labels));
            builders.add(new BundleDockerfileManifestsBuilder(csvMetadata, labels));

            // output owned CRDs in the manifest, fail if we're missing some
            var missing = addCRDManifestBuilder(crds, builders, csvMetadata, csvBuilder.getOwnedCRs());
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "Missing owned CRD data for resources: " + missing + " for bundle: " + csvMetadata.name);
            }
            // output required CRDs in the manifest, output a warning in case we're missing some
            missing = addCRDManifestBuilder(crds, builders, csvMetadata, csvBuilder.getRequiredCRs());
            if (!missing.isEmpty()) {
                log.warnv("Missing required CRD data for resources: {0} for bundle: {1}", missing, csvMetadata.name);
            }
        }

        return builders;
    }

    private static HashSet<String> addCRDManifestBuilder(Map<String, CRDInfo> crds, List<ManifestsBuilder> builders,
            CSVMetadataHolder csvMetadata, Set<String> controllerDefinedCRs) {
        final var missing = new HashSet<>(controllerDefinedCRs);
        controllerDefinedCRs.forEach(crdName -> {
            final var info = crds.get(crdName);
            if (info != null) {
                builders.add(new CustomResourceManifestsBuilder(csvMetadata, info));
                missing.remove(crdName);
            }
        });
        return missing;
    }

    private static SortedMap<String, String> generateBundleLabels(CSVMetadataHolder csvMetadata,
            BundleGenerationConfiguration bundleConfiguration, Version version) {
        var packageName = bundleConfiguration.packageName.orElse(csvMetadata.name);

        SortedMap<String, String> values = new TreeMap<>();
        values.put(join(BUNDLE_PREFIX, CHANNEL, DEFAULT, ANNOTATIONS_VERSION),
                bundleConfiguration.defaultChannel.orElse(bundleConfiguration.channels.get(0)));
        values.put(join(BUNDLE_PREFIX, CHANNELS, ANNOTATIONS_VERSION), String.join(COMMA, bundleConfiguration.channels));
        values.put(join(BUNDLE_PREFIX, MANIFESTS, ANNOTATIONS_VERSION), MANIFESTS + SLASH);
        values.put(join(BUNDLE_PREFIX, MEDIA_TYPE, ANNOTATIONS_VERSION), REGISTRY_PLUS + ANNOTATIONS_VERSION);
        values.put(join(BUNDLE_PREFIX, METADATA, ANNOTATIONS_VERSION), METADATA + SLASH);
        values.put(join(BUNDLE_PREFIX, PACKAGE, ANNOTATIONS_VERSION), packageName);
        values.put(join(METRICS_PREFIX, BUILDER), QOSDK + version.getExtensionVersion() + "+" + version.getExtensionCommit());
        values.put(join(METRICS_PREFIX, MEDIA_TYPE, ANNOTATIONS_VERSION), METRICS_V1);
        values.put(join(METRICS_PREFIX, PROJECT_LAYOUT), LAYOUT_V1_ALPHA);

        return values;
    }

    private static String join(String... elements) {
        return String.join(DOT, elements);
    }
}
