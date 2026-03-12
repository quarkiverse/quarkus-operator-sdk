package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.ANNOTATION_CONFIGURABLE;
import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.OBJECT;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

public class ClassUtils {
    private ClassUtils() {
    }

    public static boolean isStatusNotVoid(String statusClassName) {
        return !Void.class.getName().equals(statusClassName);
    }

    public record IndexSearchContext(IndexView indexView, Logger log, Map<String, Object> context) {
        private static final String EXCLUDED_CLASSES_KEY = "excluded-classes";

        public IndexSearchContext(IndexView indexView, Logger log, Set<String> excludedClasses) {
            this(indexView, log, new HashMap<>(5));
            context.put(EXCLUDED_CLASSES_KEY, excludedClasses);
        }

        public <T> T get(String key, Class<T> expectedType) {
            return expectedType.cast(this.context.get(key));
        }

        public void put(String key, Object value) {
            this.context.put(key, value);
        }

        public boolean isExcluded(String className) {
            return get(EXCLUDED_CLASSES_KEY, Set.class).contains(className);
        }
    }

    public static IndexSearchContext context(IndexView indexView, Logger log, Set<String> excludedClasses) {
        return new IndexSearchContext(indexView, log, excludedClasses);
    }

    /**
     * Only retrieve {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler} implementations that should be considered by
     * the extension, excluding the SDK's own implementations and non-processable (i.e. reconcilers that are not correctly
     * parameterized) ones.
     *
     * @param context the {@link IndexSearchContext} with which to retrieve reconcilers
     * @return a stream of {@link ReconcilerAugmentedClassInfo} providing information about processable reconcilers
     */
    public static Stream<ReconcilerAugmentedClassInfo> getKnownReconcilers(IndexSearchContext context) {
        return getProcessableImplementationsOf(RECONCILER, context).map(ReconcilerAugmentedClassInfo.class::cast);
    }

    public static Stream<? extends SelectiveAugmentedClassInfo> getProcessableImplementationsOf(DotName interfaceType,
            IndexSearchContext context) {
        return getProcessableImplementationsOrExtensionsOf(interfaceType, context, true);
    }

    public static Stream<? extends SelectiveAugmentedClassInfo> getProcessableSubClassesOf(DotName classType,
            IndexSearchContext context) {
        return getProcessableImplementationsOrExtensionsOf(classType, context, false);
    }

    private static Stream<? extends SelectiveAugmentedClassInfo> getProcessableImplementationsOrExtensionsOf(
            DotName implementedOrExtendedClass,
            IndexSearchContext context, boolean isInterface) {
        final var index = context.indexView;
        final var extensions = isInterface ? index.getAllKnownImplementations(implementedOrExtendedClass)
                : index.getAllKnownSubclasses(implementedOrExtendedClass);
        return extensions.stream()
                .map(classInfo -> createAugmentedClassInfoFor(implementedOrExtendedClass, classInfo))
                .filter(fci -> fci.keep(context))
                .peek(fci -> fci.augmentIfKept(context));
    }

    static SelectiveAugmentedClassInfo createAugmentedClassInfoFor(DotName implementedOrExtendedClass, ClassInfo classInfo) {
        if (RECONCILER.equals(implementedOrExtendedClass)) {
            return new ReconcilerAugmentedClassInfo(classInfo);
        } else if (DEPENDENT_RESOURCE.equals(implementedOrExtendedClass)) {
            return new DependentResourceAugmentedClassInfo(classInfo);
        } else if (ANNOTATION_CONFIGURABLE.equals(implementedOrExtendedClass)) {
            return new AnnotationConfigurableAugmentedClassInfo(classInfo);
        } else if (CUSTOM_RESOURCE.equals(implementedOrExtendedClass)) {
            return new CustomResourceAugmentedClassInfo(classInfo, null);
        } else {
            throw new IllegalArgumentException("Don't know how to process " + implementedOrExtendedClass);
        }
    }

    /**
     * Determines whether the class represented by the specified {@link ClassInfo} implements the specified target interface.
     * Note that this only checks whether the target interface is directly implemented by any of the classes reachable from the
     * specified class. This won't examine whether the target interface is indirectly reachable (via e.g. interface
     * hierarchies).
     *
     * @param index the {@link IndexView} used to retrieve class information
     * @param info the {@link ClassInfo} associated with the class we want to examine
     * @param targetInterface the target interface name
     * @return {@code true} if the specified {@link ClassInfo} or one of its super-types directly implements the target
     *         interface, {@code false} otherwise
     */
    public static boolean isImplementationOf(IndexView index, ClassInfo info, DotName targetInterface) {
        if (info.interfaceNames().contains(targetInterface)) {
            return true;
        } else {

            // move up the type hierarchy
            Type superType = info.superClassType();

            // if we've reached Object, our class doesn't implement the target interface
            final var superTypeName = superType.name();
            if (OBJECT.equals(superTypeName)) {
                return false;
            }
            ClassInfo superClass = index.getClassByName(superTypeName);
            if (superClass == null) {
                throw new IllegalStateException("The class " + superTypeName + " is not inside the Jandex index");
            } else {
                return isImplementationOf(index, superClass, targetInterface);
            }
        }
    }

    public static boolean hasField(IndexView index, ClassInfo info, String fieldName) {
        ClassInfo tmpClassInfo = info;
        while (tmpClassInfo != null) {
            final var status = tmpClassInfo.field(fieldName);
            if (status != null) {
                return true;
            }
            // look up the hierarchy
            if (tmpClassInfo.superName() != null) {
                tmpClassInfo = index.getClassByName(tmpClassInfo.superName());
            } else {
                tmpClassInfo = null;
            }
        }
        return false;
    }
}
