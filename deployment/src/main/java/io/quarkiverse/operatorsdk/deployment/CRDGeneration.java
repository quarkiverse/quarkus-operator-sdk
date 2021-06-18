package io.quarkiverse.operatorsdk.deployment;

import io.fabric8.crd.generator.CRDGenerator;
import io.fabric8.crd.generator.CustomResourceInfo;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class CRDGeneration {
    private final CRDGenerator generator = new CRDGenerator();
    private final CRDGenerationInfo.CRDGenerationInfoBuilder builder = CRDGenerationInfo.builder();
    private boolean generate;

    public CRDGeneration(boolean generate) {
        this.generate = generate;
    }

    CRDGenerationInfo generate(OutputTargetBuildItem outputTarget, CRDConfiguration crdConfig,
            boolean validateCustomResources) {
        if (generate) {
            final String outputDirName = crdConfig.outputDirectory;
            final var outputDir = outputTarget.getOutputDirectory().resolve(outputDirName).toFile();
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            generator.forCRDVersions(crdConfig.versions).inOutputDir(outputDir).generate();
            return builder
                    .forCRDVersions(crdConfig.versions)
                    .withTargetDir(outputDir)
                    .validatingCRDs(validateCustomResources)
                    .applyingCRDs(crdConfig.apply)
                    .withNamingStrategy(CRDGenerator::getOutputName)
                    .build();
        }
        return new CRDGenerationInfo();
    }

    public void withCustomResource(Class<CustomResource> crClass, String crdName) {
        if (generate) {
            generator.customResources(CustomResourceInfo.fromClass(crClass));
            builder.withCustomResource(crdName);
        }
    }
}
