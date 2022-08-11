package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.ANNOTATION_CONFIGURABLE;
import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

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
        return getProcessableExtensionsOf(RECONCILER, index, log)
                .map(ReconcilerAugmentedClassInfo.class::cast);
    }

    public static Stream<? extends SelectiveAugmentedClassInfo> getProcessableExtensionsOf(DotName extendedOrImplementedClass,
            IndexView index, Logger log) {
        return index.getAllKnownImplementors(extendedOrImplementedClass).stream()
                .map(classInfo -> {
                    if (RECONCILER.equals(extendedOrImplementedClass)) {
                        return new ReconcilerAugmentedClassInfo(classInfo);
                    } else if (DEPENDENT_RESOURCE.equals(extendedOrImplementedClass)) {
                        return new DependentResourceAugmentedClassInfo(classInfo);
                    } else if (ANNOTATION_CONFIGURABLE.equals(extendedOrImplementedClass)) {
                        return new AnnotationConfigurableAugmentedClassInfo(classInfo);
                    } else {
                        throw new IllegalArgumentException("Don't know how to process " + extendedOrImplementedClass);
                    }
                })
                .filter(fci -> fci.keepAugmented(index, log));
    }
}
