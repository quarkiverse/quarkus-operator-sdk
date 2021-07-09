package io.quarkiverse.operatorsdk.deployment;

import java.util.HashMap;
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
     * @return a {@link CRDGenerationInfo} detailing information about the CRD generation
     */
    CRDGenerationInfo generate(OutputTargetBuildItem outputTarget, CRDConfiguration crdConfig,
            boolean validateCustomResources) {
        if (generate) {
            final String outputDirName = crdConfig.outputDirectory;
            final var outputDir = outputTarget.getOutputDirectory().resolve(outputDirName).toFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            final var info = generator.forCRDVersions(crdConfig.versions).inOutputDir(outputDir).detailedGenerate();
            final var crdDetailsPerNameAndVersion = info.getCRDDetailsPerNameAndVersion();
            final var converted = new HashMap<String, Map<String, CRDInfo>>();
            crdDetailsPerNameAndVersion.forEach((crdName, initialVersionToCRDInfoMap) -> {
                final var versionToCRDInfo = converted.computeIfAbsent(crdName, s -> new HashMap<>());
                initialVersionToCRDInfoMap
                        .forEach((version, crdInfo) -> versionToCRDInfo.put(version, new CRDInfo(crdInfo.getCrdName(),
                                crdInfo.getVersion(), crdInfo.getFilePath(), crdInfo.getDependentClassNames())));
            });
            return new CRDGenerationInfo(validateCustomResources, crdConfig.apply, converted);
        }
        return new CRDGenerationInfo();
    }

    public void withCustomResource(Class<CustomResource> crClass, String crdName) {
        if (generate) {
            generator.customResources(CustomResourceInfo.fromClass(crClass));
        }
    }
}
