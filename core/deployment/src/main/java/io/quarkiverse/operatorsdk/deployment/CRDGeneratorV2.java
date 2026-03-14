package io.quarkiverse.operatorsdk.deployment;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.crdv2.generator.CRDPostProcessor;
import io.fabric8.crdv2.generator.CustomResourceInfo;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfos;
import io.quarkiverse.operatorsdk.runtime.CRDUtils;

class CRDGeneratorV2 implements CRDGenerator {
    private static final Logger log = Logger.getLogger(CRDGeneratorV2.class);
    private final io.fabric8.crdv2.generator.CRDGenerator generator;
    private final CRDInfoAugmentingCRDPostProcessor processor;

    /**
     * Records additional information in emitted CRDInfo, which would otherwise require to parse the CRD after generation.
     */
    static class CRDInfoAugmentingCRDPostProcessor implements CRDPostProcessor {
        private final CRDPostProcessor userDefined;
        private final Map<String, Set<String>> crdKeyToSelectableFields;

        CRDInfoAugmentingCRDPostProcessor(CRDPostProcessor userDefined) {
            this.userDefined = userDefined;
            this.crdKeyToSelectableFields = new HashMap<>();
        }

        @Override
        public HasMetadata process(HasMetadata crd, String crdSpecVersion) {
            // record selectable fields for that CRD
            final var selectableFields = CRDUtils.getSelectableFields((CustomResourceDefinition) crd);
            if (!selectableFields.isEmpty()) {
                final var key = keyFor(crdSpecVersion, crd);
                crdKeyToSelectableFields.put(key, selectableFields);
            }

            // call user-defined processor if it exists
            if (userDefined != null) {
                return userDefined.process(crd, crdSpecVersion);
            }
            return crd;
        }

        Set<String> selectableFields(String crdSpecVersion, String crdFullName) {
            return crdKeyToSelectableFields.get(keyFor(crdSpecVersion, crdFullName));
        }

        static String keyFor(String crdSpecVersion, HasMetadata crd) {
            return keyFor(crdSpecVersion, crd.getFullResourceName());
        }

        private static String keyFor(String crdSpecVersion, String fullCRDName) {
            return fullCRDName + crdSpecVersion;
        }
    }

    public CRDGeneratorV2(boolean parallelGeneration, CRDPostProcessor postProcessor) {
        this.processor = new CRDInfoAugmentingCRDPostProcessor(postProcessor);
        this.generator = new io.fabric8.crdv2.generator.CRDGenerator()
                .withParallelGenerationEnabled(parallelGeneration)
                .withPostProcessor(processor);
    }

    @Override
    public void generate(List<String> crdSpecVersions, File outputDir, Set<String> generated, CRDInfos converted) {
        final var info = generator.forCRDVersions(crdSpecVersions).inOutputDir(outputDir).detailedGenerate();
        final var crdDetailsPerNameAndVersion = info.getCRDDetailsPerNameAndVersion();

        crdDetailsPerNameAndVersion.forEach((crdName, initialVersionToCRDInfoMap) -> {
            log.infof("Generated %s CRD:", crdName);
            generated.add(crdName);

            initialVersionToCRDInfoMap
                    .forEach((crdSpecVersion, crdInfo) -> {
                        final var filePath = crdInfo.getFilePath();
                        final var selectableFields = processor.selectableFields(crdSpecVersion, crdName);
                        log.infof("  - '%s' CRD spec -> %s", crdSpecVersion, filePath);
                        converted.addCRDInfo(new CRDInfo(crdName,
                                crdSpecVersion, filePath, crdInfo.getDependentClassNames(), selectableFields));
                    });
        });
    }

    @Override
    public void scheduleForGeneration(Class<? extends CustomResource<?, ?>> crClass) {
        final var info = CustomResourceInfo.fromClass(crClass);
        generator.customResources(info);
    }
}
