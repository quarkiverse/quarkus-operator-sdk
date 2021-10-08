package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import io.fabric8.crd.generator.CRDGenerator;
import io.fabric8.crd.generator.CustomResourceInfo;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class CRDGeneration {
    private final CRDGenerator generator = new CRDGenerator();
    private final boolean generate;
    private boolean needGeneration;
    private final CustomResourceControllerMapping crMappings = new CustomResourceControllerMapping();

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

            crdDetailsPerNameAndVersion.forEach((crdName, initialVersionToCRDInfoMap) -> {
                OperatorSDKProcessor.log.infov("Generated {0} CRD:", crdName);
                generated.add(crdName);

                final var versions = crMappings.getCustomResourceInfos(crdName);
                final var versionToCRDInfo = converted.computeIfAbsent(crdName, s -> new HashMap<>());
                initialVersionToCRDInfoMap
                        .forEach((version, crdInfo) -> {
                            final var filePath = crdInfo.getFilePath();
                            OperatorSDKProcessor.log.infov("  - {0} -> {1}", version, filePath);
                            versionToCRDInfo.put(version, new CRDInfo(crdInfo.getCrdName(),
                                    version, filePath, crdInfo.getDependentClassNames(), versions));
                        });
            });
        }
        return new CRDGenerationInfo(crdConfig.apply, validateCustomResources, converted, generated);
    }

    public void withCustomResource(Class<CustomResource> crClass, String crdName) {
        final var info = CustomResourceInfo.fromClass(crClass);
        crMappings.add(info, crdName);
        generator.customResources(info);
        needGeneration = true;
    }
}
