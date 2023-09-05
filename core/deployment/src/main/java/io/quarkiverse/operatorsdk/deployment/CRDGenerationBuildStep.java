package io.quarkiverse.operatorsdk.deployment;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;

import org.jboss.logging.Logger;

import io.quarkiverse.operatorsdk.common.*;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class CRDGenerationBuildStep {
    static final Logger log = Logger.getLogger(CRDGenerationBuildStep.class.getName());

    private BuildTimeOperatorConfiguration operatorConfiguration;

    @BuildStep
    GeneratedCRDInfoBuildItem generateCRDs(
            ReconcilerInfosBuildItem reconcilers,
            LaunchModeBuildItem launchModeBuildItem,
            LiveReloadBuildItem liveReload,
            OutputTargetBuildItem outputTarget,
            CombinedIndexBuildItem combinedIndexBuildItem) {
        final var crdConfig = operatorConfiguration.crd;
        final boolean validateCustomResources = ConfigurationUtils.shouldValidateCustomResources(crdConfig.validate);

        //apply should imply generate: we cannot apply if we're not generating!
        final var launchMode = launchModeBuildItem.getLaunchMode();
        final var crdGeneration = new CRDGeneration(crdConfig, launchMode);

        // retrieve the known CRD information to make sure we always have a full view
        var stored = liveReload.getContextObject(ContextStoredCRDInfos.class);
        if (stored == null) {
            stored = new ContextStoredCRDInfos();
        }

        final var generate = CRDGeneration.shouldGenerate(crdConfig.generate, crdConfig.apply, launchMode);
        final var storedCRDInfos = stored;
        final var changedClasses = ConfigurationUtils.getChangedClasses(liveReload);
        final var scheduledForGeneration = new HashSet<String>(7);

        if (generate) {
            reconcilers.getReconcilers().values().forEach(raci -> {
                // add associated primary resource for CRD generation if it's a CR and it's owned by the reconciler
                final ReconciledAugmentedClassInfo<?> associatedResource = raci.associatedResourceInfo();
                if (associatedResource.isCR()) {
                    final var crInfo = associatedResource.asResourceTargeting();
                    final String targetCRName = crInfo.fullResourceName();

                    // if the primary resource is unowned, mark it as "scheduled" (i.e. already handled) so that it doesn't get considered if all CRDs generation is requested
                    if (!operatorConfiguration.isControllerOwningPrimary(raci.nameOrFailIfUnset())) {
                        scheduledForGeneration.add(targetCRName);
                    } else {
                        // When we have a live reload, check if we need to regenerate the associated CRD
                        Map<String, CRDInfo> crdInfos = Collections.emptyMap();
                        if (liveReload.isLiveReload()) {
                            crdInfos = storedCRDInfos.getCRDInfosFor(targetCRName);
                        }

                        // schedule the generation of associated primary resource CRD if required
                        if (crdGeneration.scheduleForGenerationIfNeeded((CustomResourceAugmentedClassInfo) crInfo, crdInfos,
                                changedClasses)) {
                            scheduledForGeneration.add(targetCRName);
                        }
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
        }

        // perform "generation" even if not requested to ensure we always produce the needed build item for other steps
        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, validateCustomResources, storedCRDInfos.getExisting());

        // record CRD generation info in context for future use
        Map<String, Map<String, CRDInfo>> generatedCRDs = crdInfo.getCrds();
        storedCRDInfos.putAll(generatedCRDs);
        liveReload.setContextObject(ContextStoredCRDInfos.class, storedCRDInfos);

        return new GeneratedCRDInfoBuildItem(crdInfo);
    }
}
