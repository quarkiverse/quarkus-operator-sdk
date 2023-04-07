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
    private static final String PREFIX_ANNOTATION = "operators.operatorframework.io.bundle";

    private BundleGenerator() {
    }

    public static List<ManifestsBuilder> prepareGeneration(BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration,
            Map<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> csvGroups, Map<String, CRDInfo> crds,
            Path outputDirectory) {
        List<ManifestsBuilder> builders = new ArrayList<>();
        for (Map.Entry<CSVMetadataHolder, List<ReconcilerAugmentedClassInfo>> entry : csvGroups.entrySet()) {
            final var csvMetadata = entry.getKey();
            final var labels = generateBundleLabels(csvMetadata, bundleConfiguration, operatorConfiguration);

            final var mainSourcesRoot = PathsUtil.findMainSourcesRoot(outputDirectory);
            final var csvBuilder = new CsvManifestsBuilder(csvMetadata, entry.getValue(),
                    mainSourcesRoot != null ? mainSourcesRoot.getKey() : null);
            builders.add(csvBuilder);
            builders.add(new AnnotationsManifestsBuilder(csvMetadata, labels));
            builders.add(new BundleDockerfileManifestsBuilder(csvMetadata, labels));

            // output owned CRDs in the manifest, fail if we're missing some
            var missing = addCRDManifestBuilder(crds, builders, csvMetadata, csvBuilder.getOwnedCRs());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Missing owned CRD data for resources: " + missing);
            }
            // output required CRDs in the manifest, output a warning in case we're missing some
            missing = addCRDManifestBuilder(crds, builders, csvMetadata, csvBuilder.getRequiredCRs());
            if (!missing.isEmpty()) {
                log.warnv("Missing required CRD data for resources: {0}", missing);
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
            BundleGenerationConfiguration bundleConfiguration,
            BuildTimeOperatorConfiguration operatorConfiguration) {
        SortedMap<String, String> values = new TreeMap<>();
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
