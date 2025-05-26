package io.quarkiverse.operatorsdk.deployment;

import java.io.File;
import java.util.List;
import java.util.Set;

import io.fabric8.crdv2.generator.CRDPostProcessor;
import io.fabric8.crdv2.generator.CustomResourceInfo;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfos;
import io.quarkus.logging.Log;

class CRDGeneratorV2 implements CRDGenerator {
    private final io.fabric8.crdv2.generator.CRDGenerator generator;

    public CRDGeneratorV2(boolean parallelGeneration, CRDPostProcessor postProcessor) {
        this.generator = new io.fabric8.crdv2.generator.CRDGenerator()
                .withParallelGenerationEnabled(parallelGeneration);
        if (postProcessor != null) {
            generator.withPostProcessor(postProcessor);
        }
    }

    @Override
    public void generate(List<String> crdSpecVersions, File outputDir, Set<String> generated, CRDInfos converted) {
        final var info = generator.forCRDVersions(crdSpecVersions).inOutputDir(outputDir).detailedGenerate();
        final var crdDetailsPerNameAndVersion = info.getCRDDetailsPerNameAndVersion();

        crdDetailsPerNameAndVersion.forEach((crdName, initialVersionToCRDInfoMap) -> {
            Log.infov("Generated {0} CRD:", crdName);
            generated.add(crdName);

            initialVersionToCRDInfoMap
                    .forEach((crdSpecVersion, crdInfo) -> {
                        final var filePath = crdInfo.getFilePath();
                        Log.infov("  - ''{0}'' CRD spec -> {1}", crdSpecVersion, filePath);
                        converted.addCRDInfo(new CRDInfo(crdName,
                                crdSpecVersion, filePath, crdInfo.getDependentClassNames()));
                    });
        });
    }

    @Override
    public void scheduleForGeneration(Class<? extends CustomResource<?, ?>> crClass) {
        final var info = CustomResourceInfo.fromClass(crClass);
        generator.customResources(info);
    }
}
