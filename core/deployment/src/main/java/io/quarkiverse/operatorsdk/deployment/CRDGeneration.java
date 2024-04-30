package io.quarkiverse.operatorsdk.deployment;

import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.logging.Logger;

import io.fabric8.crdv2.generator.CRDGenerator;
import io.fabric8.crdv2.generator.CustomResourceInfo;
import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.common.CustomResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.FileUtils;
import io.quarkiverse.operatorsdk.runtime.CRDConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.runtime.LaunchMode;

class CRDGeneration {
    private static final Logger log = Logger.getLogger(CRDGeneration.class.getName());
    private CRDGenerator generator;
    private final LaunchMode mode;
    private final CRDConfiguration crdConfiguration;
    private boolean needGeneration;
    private final ResourceControllerMapping crMappings = new ResourceControllerMapping();

    public CRDGeneration(CRDConfiguration crdConfig, LaunchMode mode) {
        this.crdConfiguration = crdConfig;
        this.mode = mode;
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
     * {@link #withCustomResource(Class, String, String)}
     *
     * @param outputTarget the {@link OutputTargetBuildItem} specifying where the CRDs
     *        should be generated
     * @param validateCustomResources whether the SDK should check if the CRDs are properly deployed
     *        on the server
     * @param existing the already known CRDInfos
     * @return a {@link CRDGenerationInfo} detailing information about the CRD generation
     */
    CRDGenerationInfo generate(OutputTargetBuildItem outputTarget,
            boolean validateCustomResources, Map<String, Map<String, CRDInfo>> existing) {
        // initialize CRDInfo with existing data to always have a full view even if we don't generate anything
        final var converted = new HashMap<>(existing);
        // record which CRDs got generated so that we only apply the changed ones
        final var generated = new HashSet<String>();

        if (needGeneration) {
            Path targetDirectory = crdConfiguration.outputDirectory()
                    .map(d -> Paths.get("").toAbsolutePath().resolve(d))
                    .orElse(outputTarget.getOutputDirectory().resolve(KUBERNETES));
            final var outputDir = targetDirectory.toFile();
            FileUtils.ensureDirectoryExists(outputDir);

            // generate CRDs with detailed information
            final var info = generator.forCRDVersions(crdConfiguration.versions()).inOutputDir(outputDir).detailedGenerate();
            final var crdDetailsPerNameAndVersion = info.getCRDDetailsPerNameAndVersion();

            crdDetailsPerNameAndVersion.forEach((crdName, initialVersionToCRDInfoMap) -> {
                log.infov("Generated {0} CRD:", crdName);
                generated.add(crdName);

                final var versions = crMappings.getResourceInfos(crdName);
                final var versionToCRDInfo = converted.computeIfAbsent(crdName, s -> new HashMap<>());
                initialVersionToCRDInfoMap
                        .forEach((version, crdInfo) -> {
                            final var filePath = crdInfo.getFilePath();
                            log.infov("  - {0} -> {1}", version, filePath);
                            versionToCRDInfo.put(version, new CRDInfo(crdInfo.getCrdName(),
                                    version, filePath, crdInfo.getDependentClassNames(), versions));
                        });
            });
        }
        return new CRDGenerationInfo(shouldApply(), validateCustomResources, converted, generated);
    }

    private boolean needsGeneration(Map<String, CRDInfo> existingCRDInfos, Set<String> changedClassNames, String targetCRName) {
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
            log.infov(
                    "''{0}'' CRD generation was skipped for ''{1}'' because no changes impacting the CRD were detected",
                    v, targetCRName);
            generateCurrent[0] = false;
        });
        return generateCurrent[0];
    }

    boolean scheduleForGenerationIfNeeded(CustomResourceAugmentedClassInfo crInfo,
            Map<String, CRDInfo> existingCRDInfos, Set<String> changedClasses) {
        var scheduleCurrent = true;
        final String targetCRName = crInfo.asResourceTargeting().fullResourceName();

        if (existingCRDInfos != null && !existingCRDInfos.isEmpty()) {
            scheduleCurrent = needsGeneration(existingCRDInfos, changedClasses, targetCRName);
        }

        if (scheduleCurrent) {
            withCustomResource(crInfo.loadAssociatedClass(), targetCRName, crInfo.getAssociatedReconcilerName().orElse(null));
        }

        return scheduleCurrent;
    }

    public void withCustomResource(Class<? extends CustomResource<?, ?>> crClass, String crdName,
            String associatedControllerName) {
        // first check if the CR is not filtered out
        if (crdConfiguration.excludeResources().map(excluded -> excluded.contains(crClass.getName())).orElse(false)) {
            log.infov("CRD generation was skipped for ''{0}'' because it was excluded from generation", crClass.getName());
            return;
        }

        try {
            // generator MUST be initialized before we start processing classes as initializing it
            // will reset the types information held by the generator
            if (generator == null) {
                generator = new CRDGenerator().withParallelGenerationEnabled(crdConfiguration.generateInParallel());
            }
            final var info = CustomResourceInfo.fromClass(crClass);
            crMappings.add(info, crdName, associatedControllerName);
            generator.customResources(info);
            needGeneration = true;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot process " + crClass.getName() + " custom resource"
                    + (associatedControllerName != null ? " for controller '" + associatedControllerName + "'" : ""),
                    e);
        }
    }
}
