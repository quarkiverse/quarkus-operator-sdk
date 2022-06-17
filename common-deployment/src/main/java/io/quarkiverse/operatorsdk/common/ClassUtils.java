package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.DEPENDENT_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.HAS_METADATA;
import static io.quarkiverse.operatorsdk.common.Constants.IGNORE_RECONCILER;
import static io.quarkiverse.operatorsdk.common.Constants.RECONCILER;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.builder.BuildException;
import io.quarkus.deployment.util.JandexUtil;

public class ClassUtils {

    private ClassUtils() {
    }

    public static boolean isStatusNotVoid(String statusClassName) {
        return !Void.class.getName().equals(statusClassName);
    }

    public static class DependentResourceInfo extends FilteredClassInfo {

        public DependentResourceInfo(ClassInfo classInfo) {
            super(classInfo, DEPENDENT_RESOURCE, 2);
        }

        @Override
        protected boolean doKeep(IndexView index, Logger log) {
            // only need to check the secondary resource type since the primary should have already been processed with the associated reconciler
            final var secondaryTypeDN = typeAt(0).name();
            registerForReflection(secondaryTypeDN.toString());

            // check if the secondary resource is a CR (rare but possible)
            handlePossibleCR(secondaryTypeDN, index, log);

            return true;
        }
    }

    /**
     * Metadata about a processable reconciler implementation.
     */
    public static class ReconcilerInfo extends FilteredClassInfo {
        private final String name;
        private boolean isCR;
        private boolean hasNonVoidStatus;

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

            // record target resource class for later forced registration for reflection
            registerForReflection(primaryTypeDN.toString());

            // check if the primary is also a CR, in which case we also need to register its
            // spec and status classes if we can determine them
            final var crStatus = handlePossibleCR(primaryTypeDN, index, log);
            isCR = crStatus.isCR;
            hasNonVoidStatus = crStatus.hasNonVoidStatus;

            return true;
        }

        public boolean isCRTargeting() {
            return isCR;
        }

        public boolean hasNonVoidStatus() {
            return hasNonVoidStatus;
        }
    }

    public abstract static class FilteredClassInfo {
        private final ClassInfo classInfo;
        private Type[] types;
        private final DotName extendedOrImplementedClass;
        private final int expectedParameterTypesCardinality;
        private final List<String> classNamesToRegisterForReflection = new ArrayList<>();

        protected FilteredClassInfo(ClassInfo classInfo, DotName extendedOrImplementedClass,
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

        public List<String> getClassNamesToRegisterForReflection() {
            return classNamesToRegisterForReflection;
        }

        protected void registerForReflection(String className) {
            if (className != null && !className.startsWith("java.")) {
                classNamesToRegisterForReflection.add(className);
            }
        }

        protected Type typeAt(int index) {
            return types[index];
        }

        protected String extendedOrImplementedClassName() {
            return extendedOrImplementedClass.local();
        }

        protected abstract boolean doKeep(IndexView index, Logger log);

        protected CRStatus handlePossibleCR(DotName primaryTypeDN, IndexView index, Logger log) {
            // register spec and status for reflection if we're targeting a CustomResource
            // note that this shouldn't be necessary anymore once https://github.com/quarkusio/quarkus/pull/26188
            // is merged and available as the kubernetes-client extension will properly take care of the
            // registration of the custom resource and associated status / spec classes for reflection
            final var primaryCI = index.getClassByName(primaryTypeDN);
            var isCR = false;
            if (primaryCI == null) {
                log.warnv(
                        "''{0}'' has not been found in the Jandex index so it cannot be introspected. Assumed not to be a CustomResource implementation. If you believe this is wrong, please index your classes with Jandex.",
                        primaryTypeDN);
            } else {
                try {
                    isCR = JandexUtil.isSubclassOf(index, primaryCI, CUSTOM_RESOURCE);
                } catch (BuildException e) {
                    log.errorv(
                            "Couldn't ascertain if ''{0}'' is a CustomResource subclass. Assumed not to be.",
                            e);
                }
            }

            boolean hasNonVoidStatus = false;
            if (isCR) {
                final var crParamTypes = JandexUtil.resolveTypeParameters(primaryTypeDN,
                        CUSTOM_RESOURCE, index);
                final var specClassName = crParamTypes.get(0).name().toString();
                final var statusClassName = crParamTypes.get(1).name().toString();
                hasNonVoidStatus = isStatusNotVoid(statusClassName);
                registerForReflection(specClassName);
                registerForReflection(statusClassName);
            }

            return new CRStatus(isCR, hasNonVoidStatus);
        }

        protected static class CRStatus {
            final boolean isCR;
            final boolean hasNonVoidStatus;

            protected CRStatus(boolean isCR, boolean hasNonVoidStatus) {
                this.isCR = isCR;
                this.hasNonVoidStatus = hasNonVoidStatus;
            }
        }
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
        return getProcessableExtensionsOf(RECONCILER, index, log)
                .map(ReconcilerInfo.class::cast);
    }

    public static Stream<? extends FilteredClassInfo> getProcessableExtensionsOf(DotName extendedOrImplementedClass,
            IndexView index, Logger log) {
        return index.getAllKnownImplementors(extendedOrImplementedClass).stream()
                .map(classInfo -> {
                    if (RECONCILER.equals(extendedOrImplementedClass)) {
                        return new ReconcilerInfo(classInfo);
                    } else if (DEPENDENT_RESOURCE.equals(extendedOrImplementedClass)) {
                        return new DependentResourceInfo(classInfo);
                    } else {
                        throw new IllegalArgumentException("Don't know how to process " + extendedOrImplementedClass);
                    }
                })
                .filter(fci -> fci.keep(index, log));
    }
}
