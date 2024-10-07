package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class DependentResourceAugmentedClassInfo extends ResourceAssociatedAugmentedClassInfo {
    private final AnnotationInstance dependentAnnotationFromController;

    public DependentResourceAugmentedClassInfo(ClassInfo classInfo) {
        this(classInfo, null, null);
    }

    private DependentResourceAugmentedClassInfo(ClassInfo classInfo, AnnotationInstance dependentAnnotationFromController,
            String reconcilerName) {
        super(classInfo, DEPENDENT_RESOURCE, 2,
                Optional.ofNullable(dependentAnnotationFromController)
                        .map(a -> a.value("name"))
                        .map(AnnotationValue::asString)
                        .filter(Predicate.not(String::isBlank))
                        // note that this should match DependentResource.getDefaultNameFor implementation)
                        .orElse(classInfo.name().toString()),
                reconcilerName);
        this.dependentAnnotationFromController = dependentAnnotationFromController;
    }

    public static DependentResourceAugmentedClassInfo createFor(ClassInfo classInfo,
            AnnotationInstance dependentAnnotationFromController, IndexView index, Logger log,
            Map<String, Object> context, String reconcilerName) {
        final var info = new DependentResourceAugmentedClassInfo(classInfo, dependentAnnotationFromController, reconcilerName);
        info.augmentIfKept(index, log, context);
        return info;
    }

    public AnnotationInstance getDependentAnnotationFromController() {
        if (dependentAnnotationFromController == null) {
            throw new IllegalStateException("Should only be called if this instance was manually created");
        }

        return dependentAnnotationFromController;
    }
}
