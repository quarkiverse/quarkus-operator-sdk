package io.quarkiverse.operatorsdk.deployment;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import io.quarkiverse.operatorsdk.common.*;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfo;
import io.quarkiverse.operatorsdk.runtime.CRDInfos;
import io.quarkiverse.operatorsdk.runtime.CRDUtils;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

class CRDGenerationBuildStep {
    static final Logger log = Logger.getLogger(CRDGenerationBuildStep.class.getName());
    private static final String excludedCause = "it was explicitly excluded from generation";
    private static final String externalCause = "it is associated with an externally provided CRD";

    @BuildStep
    GeneratedCRDInfoBuildItem generateCRDs(
            BuildTimeOperatorConfiguration operatorConfiguration,
            ReconcilerInfosBuildItem reconcilers,
            LaunchModeBuildItem launchModeBuildItem,
            LiveReloadBuildItem liveReload,
            OutputTargetBuildItem outputTarget,
            CombinedIndexBuildItem combinedIndexBuildItem,
            UnownedCRDInfoBuildItem unownedCRDInfo) {
        final var crdConfig = operatorConfiguration.crd();
        final boolean validateCustomResources = ConfigurationUtils.shouldValidateCustomResources(crdConfig.validate());

        //apply should imply generate: we cannot apply if we're not generating!
        final var launchMode = launchModeBuildItem.getLaunchMode();
        final var crdGeneration = new CRDGeneration(crdConfig, launchMode);

        // retrieve the known CRD information to make sure we always have a full view
        var stored = liveReload.getContextObject(CRDInfos.class);
        if (stored == null) {
            stored = new CRDInfos();
        }

        final var generate = CRDGeneration.shouldGenerate(crdConfig.generate(), crdConfig.apply(), launchMode);
        final var storedCRDInfos = stored;
        final var changedClasses = ConfigurationUtils.getChangedClasses(liveReload);
        final var scheduledForGeneration = new HashSet<String>(7);

        final var excludedResourceClasses = crdConfig.excludeResources().map(Set::copyOf).orElseGet(Collections::emptySet);
        final var externalCRDs = unownedCRDInfo.getCRDs();
        // predicate to decide whether or not to consider a given resource for generation
        Function<CustomResourceAugmentedClassInfo, Boolean> keepResourcePredicate = (
                CustomResourceAugmentedClassInfo crInfo) -> !isExcluded(crInfo, externalCRDs, excludedResourceClasses);

        if (generate) {
            reconcilers.getReconcilers().values().stream()
                    .map(ResourceAssociatedAugmentedClassInfo::associatedResourceInfo)
                    .filter(ReconciledAugmentedClassInfo::isCR) // only keep CRs
                    .map(CustomResourceAugmentedClassInfo.class::cast)
                    .filter(keepResourcePredicate::apply)
                    .forEach(associatedResource -> {
                        final var crInfo = associatedResource.asResourceTargeting();
                        final String crId = crInfo.id();

                        // if the primary resource is unowned, mark it as "scheduled" (i.e. already handled) so that it doesn't get considered if all CRDs generation is requested
                        if (!operatorConfiguration
                                .isControllerOwningPrimary(associatedResource.getAssociatedReconcilerName().orElseThrow())) {
                            scheduledForGeneration.add(crId);
                        } else {
                            // When we have a live reload, check if we need to regenerate the associated CRD
                            Map<String, CRDInfo> crdInfos = Collections.emptyMap();
                            if (liveReload.isLiveReload()) {
                                crdInfos = storedCRDInfos.getOrCreateCRDSpecVersionToInfoMapping(crId);
                            }

                            // schedule the generation of associated primary resource CRD if required
                            if (crdGeneration.scheduleForGenerationIfNeeded((CustomResourceAugmentedClassInfo) crInfo, crdInfos,
                                    changedClasses)) {
                                scheduledForGeneration.add(crId);
                            }
                        }
                    });

            // generate non-reconciler associated CRDs if requested
            if (crdConfig.generateAll()) {
                // only process CRs that haven't been already considered and are not excluded
                keepResourcePredicate = (
                        CustomResourceAugmentedClassInfo crInfo) -> !scheduledForGeneration.contains(crInfo.id())
                                && !isExcluded(crInfo, externalCRDs, excludedResourceClasses);
                final Map<String, Object> context = Map.of(CustomResourceAugmentedClassInfo.KEEP_CR_PREDICATE_KEY,
                        keepResourcePredicate);
                ClassUtils.getProcessableSubClassesOf(Constants.CUSTOM_RESOURCE, combinedIndexBuildItem.getIndex(), log,
                        context)
                        .map(CustomResourceAugmentedClassInfo.class::cast)
                        .forEach(cr -> {
                            crdGeneration.withCustomResource(cr.loadAssociatedClass(), null);
                            log.infov("Will generate CRD for non-reconciler bound resource: {0}", cr.fullResourceName());
                        });
            }
        }

        // perform "generation" even if not requested to ensure we always produce the needed build item for other steps
        CRDGenerationInfo crdInfo = crdGeneration.generate(outputTarget, validateCustomResources, storedCRDInfos);

        // record CRD generation info in context for future use
        liveReload.setContextObject(CRDInfos.class, storedCRDInfos);

        return new GeneratedCRDInfoBuildItem(crdInfo);
    }

    @BuildStep
    UnownedCRDInfoBuildItem unownedCRDInfo(BuildTimeOperatorConfiguration operatorConfiguration,
            CurateOutcomeBuildItem appInfoBuildItem) {
        final Optional<List<String>> maybeExternalCRDs = operatorConfiguration.crd().externalCRDLocations();
        final var crds = new CRDInfos();
        if (maybeExternalCRDs.isPresent()) {
            final var moduleRoot = appInfoBuildItem.getApplicationModel().getApplicationModule().getModuleDir().toPath();
            maybeExternalCRDs.get().parallelStream()
                    .filter(Predicate.not(String::isBlank))
                    .map(String::trim)
                    .forEach(crdLocation -> {
                        final var crdPath = moduleRoot.resolve(crdLocation);
                        final var crd = CRDUtils.loadFromAsCRDInfo(crdPath);
                        crds.addCRDInfo(crd);
                    });
        }
        return new UnownedCRDInfoBuildItem(crds);
    }

    /**
     * Exclude all resources that shouldn't be generated because either they've been explicitly excluded or because they're
     * supposed to be loaded directly from a specified CRD file
     */
    private boolean isExcluded(CustomResourceAugmentedClassInfo crInfo, CRDInfos externalCRDs,
            Set<String> excludedResourceClassNames) {
        final var crClassName = crInfo.classInfo().name().toString();
        final var excluded = excludedResourceClassNames.contains(crClassName);
        final var external = externalCRDs.contains(crInfo.id());
        if (excluded || external) {
            log.infov("CRD generation was skipped for ''{0}'' because {1}", crClassName,
                    external ? externalCause : excludedCause);
            return true;
        } else {
            return false;
        }
    }
}
