package io.quarkiverse.operatorsdk.deployment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.jboss.logging.Logger;

import io.quarkiverse.operatorsdk.common.ClassUtils;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.Constants;
import io.quarkiverse.operatorsdk.common.CustomResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.runtime.LaunchMode;

public class CRDGenerationBuildStep {
    static final Logger log = Logger.getLogger(CRDGenerationBuildStep.class.getName());

    private static class WantCRDGenerated implements BooleanSupplier {

        private final boolean generate;

        private WantCRDGenerated(BuildTimeOperatorConfiguration config, LaunchMode launchMode) {
            generate = CRDGeneration.shouldGenerate(config.crd.generate, config.crd.apply, launchMode);
        }

        @Override
        public boolean getAsBoolean() {
            return generate;
        }
    }

    @BuildStep(onlyIf = WantCRDGenerated.class)
    GeneratedCRDInfoBuildItem generateCRDs(
            ReconcilerInfosBuildItem reconcilers,
            BuildTimeOperatorConfiguration buildTimeConfiguration,
            LaunchModeBuildItem launchMode,
            LiveReloadBuildItem liveReload,
            OutputTargetBuildItem outputTarget,
            CombinedIndexBuildItem combinedIndexBuildItem) {
        final var crdConfig = buildTimeConfiguration.crd;
        final boolean validateCustomResources = ConfigurationUtils.shouldValidateCustomResources(crdConfig.validate);

        //apply should imply generate:we cannot apply if we 're not generating!
        final var crdGeneration = new CRDGeneration(crdConfig, launchMode.getLaunchMode());

        // retrieve the known CRD information to make sure we always have a full view
        var stored = liveReload.getContextObject(ContextStoredCRDInfos.class);
        if (stored == null) {
            stored = new ContextStoredCRDInfos();
        }
        final var storedCRDInfos = stored;
        final var changedClasses = ConfigurationUtils.getChangedClasses(liveReload);
        final var scheduledForGeneration = new HashSet<String>(7);

        reconcilers.getReconcilers().values().stream().forEach(raci -> {
            // add associated primary resource for CRD generation if needed
            if (raci.associatedResourceInfo().isCR()) {
                final var crInfo = raci.associatedResourceInfo().asResourceTargeting();
                // When we have a live reload, check if we need to regenerate the associated CRD
                Map<String, CRDInfo> crdInfos = Collections.emptyMap();

                final String targetCRName = crInfo.fullResourceName();
                if (liveReload.isLiveReload()) {
                    crdInfos = storedCRDInfos.getCRDInfosFor(targetCRName);
                }

                if (crdGeneration.scheduleForGenerationIfNeeded((CustomResourceAugmentedClassInfo) crInfo, crdInfos,
                        changedClasses)) {
                    scheduledForGeneration.add(targetCRName);
                }
            }
        });

        // generate non-reconciler associated CRDs if requested
        if (crdConfig.generateAll) {
            ClassUtils.getProcessableSubClassesOf(Constants.CUSTOM_RESOURCE, combinedIndexBuildItem.getIndex(), log,
                    // pass already generated CRD names so that we can only keep the unhandled ones
                    Map.of(CustomResourceAugmentedClassInfo.EXISTING_CRDS_KEY, scheduledForGeneration))
                    .map(CustomResourceAugmentedClassInfo.class::cast)
                    .forEach(cr -> {
                        final var targetCRName = cr.fullResourceName();
                        crdGeneration.withCustomResource(cr.loadAssociatedClass(), targetCRName, null);
                        log.infov("Will generate CRD for non-reconciler bound resource: {0}", targetCRName);
                    });
        }

        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, validateCustomResources, storedCRDInfos.getExisting());

        // record CRD generation info in context for future use
        Map<String, Map<String, CRDInfo>> generatedCRDs = crdInfo.getCrds();
        storedCRDInfos.putAll(generatedCRDs);
        liveReload.setContextObject(ContextStoredCRDInfos.class, storedCRDInfos);

        return new GeneratedCRDInfoBuildItem(crdInfo);
    }
}
