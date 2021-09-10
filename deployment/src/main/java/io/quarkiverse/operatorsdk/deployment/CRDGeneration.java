package io.quarkiverse.operatorsdk.deployment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.fabric8.crd.generator.CRDGenerator;
import io.fabric8.crd.generator.CustomResourceInfo;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionBuilder;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class CRDGeneration {
    private final CRDGenerator generator = new CRDGenerator();
    private final boolean generate;
    private boolean needGeneration;
    private final CustomResourceControllerMapping crMappings = new CustomResourceControllerMapping();

    private static final ObjectMapper YAML_MAPPER;
    static {
        YAML_MAPPER = new ObjectMapper((new YAMLFactory()).enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        YAML_MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        YAML_MAPPER.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        YAML_MAPPER.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
    }

    public CRDGeneration(boolean generate) {
        this.generate = generate;
    }

    public boolean wantCRDGenerated() {
        return generate;
    }

    /**
     * Generates the CRD in the location specified by the output target, using the specified CRD generation configuration
     * 
     * @param outputTarget the {@link OutputTargetBuildItem} specifying where the CRDs should be generated
     * @param crdConfig the {@link CRDConfiguration} specifying how the CRDs should be generated
     * @param validateCustomResources whether the SDK should check if the CRDs are properly deployed on the server
     * @param existing the already known CRDInfos
     * @return a {@link CRDGenerationInfo} detailing information about the CRD generation
     */
    CRDGenerationInfo generate(OutputTargetBuildItem outputTarget, CRDConfiguration crdConfig,
            boolean validateCustomResources, Map<String, Map<String, CRDInfo>> existing) {
        // initialize CRDInfo with existing data to always have a full view even if we don't generate anything
        final var converted = new HashMap<>(existing);
        // record which CRDs got generated so that we only apply the changed ones
        final var generated = new HashSet<String>();

        if (needGeneration) {
            final String outputDirName = crdConfig.outputDirectory;
            final var outputDir = outputTarget.getOutputDirectory().resolve(outputDirName).toFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            final var info = generator.forCRDVersions(crdConfig.versions).inOutputDir(outputDir).detailedGenerate();
            final var crdDetailsPerNameAndVersion = info.getCRDDetailsPerNameAndVersion();

            final var controllerToCSVBuilders = new HashMap<String, ClusterServiceVersionBuilder>(7);

            crdDetailsPerNameAndVersion.forEach((crdName, initialVersionToCRDInfoMap) -> {
                OperatorSDKProcessor.log.infov("Generated {0} CRD:", crdName);
                generated.add(crdName);

                final var versions = crMappings.getCustomResourceInfos(crdName);
                versions.forEach((version, cri) -> controllerToCSVBuilders
                        .computeIfAbsent(cri.getControllerName(), s -> new ClusterServiceVersionBuilder()
                                .withNewMetadata().withName(s).endMetadata())
                        .editOrNewSpec()
                        .editOrNewCustomresourcedefinitions()
                        .addNewOwned()
                        .withName(crdName)
                        .withVersion(version)
                        .withKind(cri.getKind())
                        .endOwned().endCustomresourcedefinitions().endSpec());

                final var versionToCRDInfo = converted.computeIfAbsent(crdName, s -> new HashMap<>());
                initialVersionToCRDInfoMap
                        .forEach((version, crdInfo) -> {
                            final var filePath = crdInfo.getFilePath();
                            OperatorSDKProcessor.log.infov("  - {0} -> {1}", version, filePath);
                            versionToCRDInfo.put(version, new CRDInfo(crdInfo.getCrdName(),
                                    version, filePath, crdInfo.getDependentClassNames(), versions));
                        });
            });

            controllerToCSVBuilders.forEach((controllerName, csvBuilder) -> {
                final var csv = csvBuilder.build();
                try {
                    final var outputStream = new FileOutputStream(new File(outputDir, controllerName + ".csv.yml"));
                    YAML_MAPPER.writeValue(outputStream, csv);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return new CRDGenerationInfo(crdConfig.apply, validateCustomResources, converted, generated);
    }

    public void withCustomResource(Class<CustomResource> crClass, String crdName, String controllerName) {
        final var info = CustomResourceInfo.fromClass(crClass);
        crMappings.add(info, crdName, controllerName);
        generator.customResources(info);
        needGeneration = true;
    }
}
