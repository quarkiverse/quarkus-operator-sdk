package io.quarkiverse.operatorsdk.csv.deployment;

import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;

import java.util.HashMap;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.CustomResourceInfo;
import io.quarkiverse.operatorsdk.csv.runtime.CSVGenerationConfiguration;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.csv.runtime.SharedCSVMetadata;
import io.quarkiverse.operatorsdk.deployment.ConfigurationServiceBuildItem;
import io.quarkiverse.operatorsdk.deployment.GeneratedCRDInfoBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

public class CSVProcessor {
    private static final Logger log = Logger.getLogger(CSVProcessor.class);
    private static final DotName SHARED_CSV_METADATA = DotName.createSimple(SharedCSVMetadata.class.getName());
    private static final DotName CSV_METADATA = DotName.createSimple(CSVMetadata.class.getName());

    private CSVGenerationConfiguration configuration;

    @BuildStep
    CSVMetadataBuildItem gatherCSVMetadata(CombinedIndexBuildItem combinedIndexBuildItem,
            GeneratedCRDInfoBuildItem generatedCRDs,
            ConfigurationServiceBuildItem configurationServiceBI) {
        final var csvGroupMetadata = new HashMap<String, CSVMetadataHolder>();

        // map controller name to associated CustomResourceInfo
        final var controllerConfigs = configurationServiceBI.getControllerConfigs();
        final var controllerToCRInfo = new HashMap<String, CustomResourceInfo>(controllerConfigs.size());
        controllerConfigs.forEach(c -> {
            final var crdVersionToInfos = generatedCRDs.getCRDGenerationInfo().getCRDInfosFor(c.getCRDName());
            // find the first CustomResourceInfo that matches our defined CR version as all matching versions should be equal anyway
            final var cri = crdVersionToInfos.values().stream()
                    .filter(info -> info.getVersions().containsKey(c.getCrVersion()))
                    .findFirst()
                    .map(info -> info.getVersions().get(c.getCrVersion()))
                    .orElseThrow();
            controllerToCRInfo.put(c.getName(), cri);
        });

        final var crdGenerationInfo = generatedCRDs.getCRDGenerationInfo();
        final var augmentedCRInfos = new HashMap<String, AugmentedCustomResourceInfo>();
        final var index = combinedIndexBuildItem.getIndex();
        ClassUtils.getKnownResourceControllers(index, log)
                .forEach(info -> {
                    // figure out which group should be used to generate CSV
                    final var name = ConfigurationUtils.getControllerName(info);
                    final var csvMetadata = getCSVMetadata(info, name, index);
                    csvGroupMetadata.put(csvMetadata.name, csvMetadata);
                    final var cri = controllerToCRInfo.get(name);
                    if (cri == null) {
                        throw new IllegalStateException(
                                "There should be a CustomResourceInfo associated to controller: " + name);
                    }
                    augmentedCRInfos.put(cri.getCrdName(), new AugmentedCustomResourceInfo(cri, csvMetadata.name));
                });

        return new CSVMetadataBuildItem(csvGroupMetadata, augmentedCRInfos);
    }

    @BuildStep
    FeatureBuildItem generateCSV(OutputTargetBuildItem outputTarget,
            CSVMetadataBuildItem csvMetadata,
            BuildProducer<GeneratedCSVBuildItem> ignored/*
                                                         * ,
                                                         * List<GeneratedKubernetesResourceBuildItem>
                                                         * generatedKubernetesManifests
                                                         */) {
        if (configuration.generateCSV.orElse(false)) {
            try {
                final var outputDir = outputTarget.getOutputDirectory().resolve(KUBERNETES);
                final var serviceAccountName = new String[1];
                final var clusterRole = new ClusterRole[1];
                final var role = new Role[1];
                final var deployment = new Deployment[1];

                /*
                 * generatedKubernetesManifests.stream()
                 * .filter(bi -> bi.getName().equals("kubernetes.yml"))
                 * .findAny()
                 * .ifPresent(
                 * bi -> {
                 * final var resources = Serialization
                 * .unmarshalAsList(new ByteArrayInputStream(bi.getContent()));
                 * resources.getItems().forEach(r -> {
                 * if (r instanceof ServiceAccount) {
                 * serviceAccountName[0] = r.getMetadata().getName();
                 * return;
                 * }
                 *
                 * if (r instanceof ClusterRole) {
                 * clusterRole[0] = (ClusterRole) r;
                 * return;
                 * }
                 *
                 * if (r instanceof Role) {
                 * role[0] = (Role) r;
                 * return;
                 * }
                 *
                 * if (r instanceof Deployment) {
                 * deployment[0] = (Deployment) r;
                 * return;
                 * }
                 * });
                 * });
                 */
                CSVGenerator.generate(outputDir, csvMetadata.getAugmentedCustomResourceInfos(), csvMetadata.getCSVMetadata(),
                        serviceAccountName[0], clusterRole[0], role[0], deployment[0]);
                ignored.produce(new GeneratedCSVBuildItem());
            } catch (Exception e) {
                log.infov(e, "Couldn't generate CSV:");
            }
        }

        // generating a feature is a sure way to make sure this step will be executed by Quarkus
        return new FeatureBuildItem("CSVGeneration");
    }

    private CSVMetadataHolder getCSVMetadata(ClassInfo info, String controllerName, IndexView index) {
        return info.interfaceTypes().stream()
                .filter(t -> t.name().equals(SHARED_CSV_METADATA))
                .findFirst()
                .map(t -> {
                    final var metadataHolderType = t.asParameterizedType().arguments().get(0);
                    // need to get the associated ClassInfo to properly resolve the annotations
                    final var metadataHolder = index.getClassByName(metadataHolderType.name());
                    final var csvMetadata = metadataHolder.classAnnotation(CSV_METADATA);
                    return createMetadataHolder(csvMetadata, new CSVMetadataHolder(controllerName));

                })
                .map(mh -> createMetadataHolder(info.classAnnotation(CSV_METADATA), new CSVMetadataHolder(mh)))
                .orElse(new CSVMetadataHolder(controllerName));
    }

    private CSVMetadataHolder createMetadataHolder(AnnotationInstance csvMetadata, CSVMetadataHolder mh) {
        final var providerField = csvMetadata.value("provider");
        String providerName = null;
        String providerURL = null;
        if (providerField != null) {
            final var provider = providerField.asNested();
            providerName = ConfigurationUtils.annotationValueOrDefault(provider, "name",
                    AnnotationValue::asString, () -> mh.providerName);
            providerURL = ConfigurationUtils.annotationValueOrDefault(provider, "url",
                    AnnotationValue::asString, () -> mh.providerURL);
        }

        final var maintainersField = csvMetadata.value("maintainers");
        CSVMetadataHolder.Maintainer[] maintainers = null;
        if (maintainersField != null) {
            final var maintainersAnn = maintainersField.asNestedArray();
            maintainers = new CSVMetadataHolder.Maintainer[maintainersAnn.length];
            for (int i = 0; i < maintainersAnn.length; i++) {
                maintainers[i] = new CSVMetadataHolder.Maintainer(
                        ConfigurationUtils.annotationValueOrDefault(maintainersAnn[i], "name",
                                AnnotationValue::asString, () -> null),
                        ConfigurationUtils.annotationValueOrDefault(maintainersAnn[i], "email",
                                AnnotationValue::asString, () -> null));
            }
        } else {
            maintainers = mh.maintainers;
        }

        return new CSVMetadataHolder(
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "name",
                        AnnotationValue::asString, () -> mh.name),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "description",
                        AnnotationValue::asString, () -> mh.description),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "displayName",
                        AnnotationValue::asString, () -> mh.displayName),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "keywords",
                        AnnotationValue::asStringArray, () -> mh.keywords),
                providerName, providerURL,
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "replaces",
                        AnnotationValue::asString, () -> mh.replaces),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "version",
                        AnnotationValue::asString, () -> mh.version),
                ConfigurationUtils.annotationValueOrDefault(csvMetadata, "maturity",
                        AnnotationValue::asString, () -> mh.maturity),
                maintainers);
    }
}
