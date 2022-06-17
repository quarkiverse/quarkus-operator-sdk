package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.HAS_METADATA;
import static io.quarkiverse.operatorsdk.common.Constants.IGNORE_RECONCILER;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.deployment.util.JandexUtil;

public class ClassUtils {

    private ClassUtils() {
    }

    /**
     * Metadata about a processable reconciler implementation.
     */
    public static class ReconcilerInfo extends FilteredClassInfo {
        private final String name;

        public ReconcilerInfo(ClassInfo classInfo) {
            super(classInfo, RECONCILER, 1);
            this.name = ConfigurationUtils.getReconcilerName(classInfo);
        }

        public DotName primaryTypeName() {
            return typeAt(0).name();
        }

        public String name() {
            return name;
        }

        @Override
        protected boolean doKeep(IndexView index, Logger log) {
            final var primaryTypeDN = typeAt(0).name();
            // if we get CustomResource instead of a subclass, ignore the controller since we cannot do anything with it
            if (primaryTypeDN.toString() == null || CUSTOM_RESOURCE.equals(primaryTypeDN)
                    || HAS_METADATA.equals(primaryTypeDN)) {
                log.warnv(
                        "Skipped processing of ''{0}'' {1} as it''s not parameterized with a CustomResource or HasMetadata sub-class",
                        name(), extendedOrImplementedClassName());
                return false;
            }

            return true;
        }
    }

    public abstract static class FilteredClassInfo {

        private final ClassInfo classInfo;
        private Type[] types;
        private final DotName extendedOrImplementedClass;
        private final int expectedParameterTypesCardinality;

        public FilteredClassInfo(ClassInfo classInfo, DotName extendedOrImplementedClass,
                int expectedParameterTypesCardinality) {
            this.extendedOrImplementedClass = extendedOrImplementedClass;
            this.classInfo = classInfo;
            this.expectedParameterTypesCardinality = expectedParameterTypesCardinality;
        }

        public ClassInfo classInfo() {
            return classInfo;
        }

        boolean keep(IndexView index, Logger log) {
            final var targetClassName = extendedOrImplementedClassName();
            final var consideredClassName = classInfo.name();
            if (Modifier.isAbstract(classInfo.flags())) {
                log.warnv("Skipping ''{0}'' {1} because it''s abstract",
                        consideredClassName, targetClassName);
                return false;
            }

            // Ignore Reconciler implementations annotated with @Ignore
            if (classInfo.annotations().containsKey(IGNORE_RECONCILER)) {
                log.debugv("Skipping ''{0}'' {1} because it''s annotated with @Ignore",
                        consideredClassName, targetClassName);
                return false;
            }

            final var typeParameters = JandexUtil.resolveTypeParameters(classInfo.name(),
                    extendedOrImplementedClass, index);
            if (expectedParameterTypesCardinality != typeParameters.size()) {
                throw new IllegalArgumentException("Cannot process " + classInfo.simpleName()
                        + " as an implementation/extension of " + targetClassName
                        + " because it doesn't match the expected cardinality ("
                        + expectedParameterTypesCardinality + ") of type parameters");
            }
            this.types = typeParameters.toArray(Type[]::new);

            return doKeep(index, log);
        }

        protected Type typeAt(int index) {
            return types[index];
        }

        protected String extendedOrImplementedClassName() {
            return extendedOrImplementedClass.local();
        }

        protected abstract boolean doKeep(IndexView index, Logger log);
    }

    /**
     * Only retrieve {@link io.javaoperatorsdk.operator.api.reconciler.Reconciler} implementations that should be considered by
     * the extension, excluding the SDK's own implementations and non-processable (i.e. reconcilers that are not correctly
     * parameterized) ones.
     *
     * @param index the {@link IndexView} used to retrieve class informations
     * @param log a {@link Logger} used to output skipped reconcilers information
     * @return a stream of {@link ReconcilerInfo} providing information about processable reconcilers
     */
    public static Stream<ReconcilerInfo> getKnownReconcilers(IndexView index, Logger log) {
        return index.getAllKnownImplementors(RECONCILER).stream()
                .map(ReconcilerInfo::new)
                .filter(ri -> ri.keep(index, log));
    }
}
