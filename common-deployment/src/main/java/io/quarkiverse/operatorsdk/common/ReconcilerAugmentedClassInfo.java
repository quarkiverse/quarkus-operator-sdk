package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

/**
 * Metadata about a processable reconciler implementation.
 */
public class ReconcilerAugmentedClassInfo extends ResourceAssociatedAugmentedClassInfo {

    private Collection<DependentResourceAugmentedClassInfo> dependentResourceInfos;

    public ReconcilerAugmentedClassInfo(ClassInfo classInfo) {
        super(classInfo, RECONCILER, 1, ConfigurationUtils.getReconcilerName(classInfo));
    }

    @Override
    protected boolean doKeep(IndexView index, Logger log, Map<String, Object> context) {
        // if we get CustomResource instead of a subclass, ignore the controller since we cannot do anything with it
        final var primaryTypeDN = resourceTypeName();
        if (primaryTypeDN.toString() == null || CUSTOM_RESOURCE.equals(primaryTypeDN)
                || HAS_METADATA.equals(primaryTypeDN)) {
            log.warnv(
                    "Skipped processing of ''{0}'' {1} as it''s not parameterized with a CustomResource or HasMetadata sub-class",
                    name(), extendedOrImplementedClassName());
            return false;
        }
        return true;
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        super.doAugment(index, log, context);

        // extract dependent information
        final var reconciler = classInfo();
        final var workflow = reconciler.declaredAnnotation(WORKFLOW);
        dependentResourceInfos = Collections.emptyList();
        if (workflow != null) {
            final var dependents = workflow.value("dependents");
            if (dependents != null) {
                final var dependentAnnotations = dependents.asNestedArray();
                var dependentResources = Collections.<String, DependentResourceAugmentedClassInfo> emptyMap();
                dependentResources = new LinkedHashMap<>(dependentAnnotations.length);
                for (AnnotationInstance dependentConfig : dependentAnnotations) {
                    final var dependentType = ConfigurationUtils.getClassInfoForInstantiation(
                            dependentConfig.value("type"),
                            DependentResource.class, index);
                    final var dependent = DependentResourceAugmentedClassInfo.createFor(dependentType, dependentConfig, index,
                            log, context, nameOrFailIfUnset());
                    final var dependentName = dependent.nameOrFailIfUnset();
                    final var dependentTypeName = dependentType.name().toString();
                    if (dependentResources.containsKey(dependentName)) {
                        throw new IllegalArgumentException(
                                "A DependentResource named: " + dependentName + " already exists: "
                                        + dependentTypeName);
                    }
                    dependentResources.put(dependentName, dependent);
                    // mark dependent class as needing reflection registration
                    registerForReflection(dependentTypeName);
                }
                dependentResourceInfos = dependentResources.values();
            }
        }
    }

    public Collection<DependentResourceAugmentedClassInfo> getDependentResourceInfos() {
        return dependentResourceInfos;
    }
}
