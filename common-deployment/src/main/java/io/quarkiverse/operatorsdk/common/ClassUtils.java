package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.ANNOTATION_CONFIGURABLE;
import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

public class ClassUtils {

    private ClassUtils() {
    }

    public static boolean isStatusNotVoid(String statusClassName) {
        return !Void.class.getName().equals(statusClassName);
    }

    /**
     * Only retrieve {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler} implementations that should be considered by
     * the extension, excluding the SDK's own implementations and non-processable (i.e. reconcilers that are not correctly
     * parameterized) ones.
     *
     * @param index the {@link IndexView} used to retrieve class informations
     * @param log a {@link Logger} used to output skipped reconcilers information
     * @return a stream of {@link ReconcilerAugmentedClassInfo} providing information about processable reconcilers
     */
    public static Stream<ReconcilerAugmentedClassInfo> getKnownReconcilers(IndexView index, Logger log) {
        return getProcessableImplementationsOf(RECONCILER, index, log, Collections.emptyMap())
                .map(ReconcilerAugmentedClassInfo.class::cast);
    }

    public static Stream<? extends SelectiveAugmentedClassInfo> getProcessableImplementationsOf(DotName interfaceType,
            IndexView index, Logger log, Map<String, Object> context) {
        return getProcessableImplementationsOrExtensionsOf(interfaceType, index, log, context, true);
    }

    public static Stream<? extends SelectiveAugmentedClassInfo> getProcessableSubClassesOf(DotName classType,
            IndexView index, Logger log, Map<String, Object> context) {
        return getProcessableImplementationsOrExtensionsOf(classType, index, log, context, false);
    }

    private static Stream<? extends SelectiveAugmentedClassInfo> getProcessableImplementationsOrExtensionsOf(
            DotName implementedOrExtendedClass,
            IndexView index, Logger log, Map<String, Object> context, boolean isInterface) {
        final var extensions = isInterface ? index.getAllKnownImplementors(implementedOrExtendedClass)
                : index.getAllKnownSubclasses(implementedOrExtendedClass);
        return extensions.stream()
                .map(classInfo -> {
                    if (RECONCILER.equals(implementedOrExtendedClass)) {
                        return new ReconcilerAugmentedClassInfo(classInfo);
                    } else if (DEPENDENT_RESOURCE.equals(implementedOrExtendedClass)) {
                        return new DependentResourceAugmentedClassInfo(classInfo);
                    } else if (ANNOTATION_CONFIGURABLE.equals(implementedOrExtendedClass)) {
                        return new AnnotationConfigurableAugmentedClassInfo(classInfo);
                    } else if (CUSTOM_RESOURCE.equals(implementedOrExtendedClass)) {
                        return new ResourceTargetingAugmentedClassInfo(classInfo, null);
                    } else {
                        throw new IllegalArgumentException("Don't know how to process " + implementedOrExtendedClass);
                    }
                })
                .filter(fci -> fci.keepAugmented(index, log, context));
    }
}
