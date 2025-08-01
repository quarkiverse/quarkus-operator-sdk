package io.quarkiverse.operatorsdk.deployment;

import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.crdv2.generator.CRDPostProcessor;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.common.ClassLoadingUtils;
import io.quarkiverse.operatorsdk.common.CustomResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfos;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.runtime.LaunchMode;

class CRDGeneration {
    private static final Logger log = Logger.getLogger(CRDGeneration.class.getName());
    private final LaunchMode mode;
    private final CRDConfiguration crdConfiguration;
    private final CRDGenerator generator;
    private boolean needGeneration;

    CRDGeneration(CRDConfiguration crdConfig, LaunchMode mode) {
        this.crdConfiguration = crdConfig;
        this.mode = mode;
        // generator MUST be initialized before we start processing classes as initializing it
        // will reset the types information held by the generator
        final var useV1 = crdConfiguration.useV1CRDGenerator();
        if (useV1) {
            if (crdConfiguration.crdPostProcessorClass().isPresent()) {
                log.warnf(
                        "CRD post processing is only available when using the v2 version of the CRD generation API. Specified processor will be ignored: %s",
                        crdConfiguration.crdPostProcessorClass());
            }
            generator = new CRDGeneratorV1(crdConfiguration.generateInParallel());
        } else {
            final var postProcessor = crdConfiguration.crdPostProcessorClass()
                    .map(processorClassName -> ClassLoadingUtils.loadClass(processorClassName, CRDPostProcessor.class))
                    .map(ClassLoadingUtils::instantiate)
                    .orElse(CRDPostProcessor.nullProcessor);
            generator = new CRDGeneratorV2(crdConfiguration.generateInParallel(), postProcessor);
        }
    }

    static boolean shouldGenerate(Optional<Boolean> configuredGenerate, Optional<Boolean> configuredApply,
            LaunchMode launchMode) {
        return shouldApply(configuredApply, launchMode) || configuredGenerate.orElse(true);
    }

    static boolean shouldApply(Optional<Boolean> configuredApply, LaunchMode launchMode) {
        if (launchMode == null || !launchMode.isDevOrTest()) {
            return false;
        }
        return configuredApply.orElse(true);
    }

    boolean shouldApply() {
        return shouldApply(crdConfiguration.apply(), mode);
    }

    /**
     * Generates the CRD in the location specified by the output target, using the specified CRD
     * generation configuration only if generation has been requested by call
     * {@link #scheduleForGenerationIfNeeded(CustomResourceAugmentedClassInfo, Map, Set)} or
     * {@link #withCustomResource(Class, String)}
     *
     * @param outputTarget the {@link OutputTargetBuildItem} specifying where the CRDs
     *        should be generated
     * @param validateCustomResources whether the SDK should check if the CRDs are properly deployed
     *        on the server
     * @param existing the already known CRDInfos
     * @param externalCRDs the specified external CRDs that might need to be applied
     * @return a {@link CRDGenerationInfo} detailing information about the CRD generation
     */
    CRDGenerationInfo generate(OutputTargetBuildItem outputTarget,
            boolean validateCustomResources, CRDInfos existing, CRDInfos externalCRDs) {
        // initialize CRDInfo with existing data to always have a full view even if we don't generate anything
        final var converted = new CRDInfos(existing);
        // record which CRDs got generated so that we only apply the changed ones
        final var generated = new HashSet<String>();

        // we also want to apply the external CRDs if they haven't been applied already, so cheat by putting their names in generated if they're not already part of the existing CRD infos
        externalCRDs.getCRDNameToInfoMappings()
                .forEach((k, v) -> {
                    final var alreadyAdded = converted.addCRDInfo(v);
                    if (alreadyAdded == null) {
                        // we have not seen that external CRD before so add it to the list of CRDs that got "generated"
                        generated.add(k);
                    }
                });

        if (needGeneration) {
            Path targetDirectory = crdConfiguration.outputDirectory()
                    .map(d -> Paths.get("").toAbsolutePath().resolve(d))
                    .orElse(outputTarget.getOutputDirectory().resolve(KUBERNETES));
            final var outputDir = targetDirectory.toFile();
            FileUtils.ensureDirectoryExists(outputDir);

            // generate CRDs with detailed information
            generator.generate(crdConfiguration.versions(), outputDir, generated, converted);
        }
        return new CRDGenerationInfo(shouldApply(), validateCustomResources, converted, generated);
    }

    private boolean needsGeneration(Map<String, CRDInfo> existingCRDInfos, Set<String> changedClassNames) {
        final boolean[] generateCurrent = { true }; // request CRD generation by default
        crdConfiguration.versions().forEach(v -> {
            final var crd = existingCRDInfos.get(v);
            // if we don't have any information about this CRD version, we need to generate the CRD
            if (crd == null) {
                return;
            }

            // if dependent classes have been changed
            if (changedClassNames != null && !changedClassNames.isEmpty()) {
                for (String changedClass : changedClassNames) {
                    if (crd.getDependentClassNames().contains(changedClass)) {
                        return; // a dependent class has been changed, so we'll need to generate the CRD
                    }
                }
            }

            // we've looked at all the changed classes and none have been changed for this CR/version: do not regenerate CRD
            log.infof(
                    "'%s' CRD generation was skipped for '%s' because no changes impacting the CRD were detected",
                    v, crd.getCrdName());
            generateCurrent[0] = false;
        });
        return generateCurrent[0];
    }

    boolean scheduleForGenerationIfNeeded(CustomResourceAugmentedClassInfo crInfo,
            Map<String, CRDInfo> existingCRDInfos, Set<String> changedClasses) {
        var scheduleCurrent = true;

        if (existingCRDInfos != null && !existingCRDInfos.isEmpty()) {
            scheduleCurrent = needsGeneration(existingCRDInfos, changedClasses);
        }

        if (scheduleCurrent) {
            withCustomResource(crInfo.loadAssociatedClass(), crInfo.getAssociatedReconcilerName().orElse(null));
        }

        return scheduleCurrent;
    }

    void withCustomResource(Class<? extends CustomResource<?, ?>> crClass, String associatedControllerName) {
        try {
            generator.scheduleForGeneration(crClass);
            needGeneration = true;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot process " + crClass.getName() + " custom resource"
                    + (associatedControllerName != null ? " for controller '" + associatedControllerName + "'" : ""),
                    e);
        }
    }
}
