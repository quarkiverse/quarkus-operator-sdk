package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.IGNORE_ANNOTATION;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkus.builder.BuildException;
import io.quarkus.deployment.util.JandexUtil;

public abstract class SelectiveAugmentedClassInfo {

    private final ClassInfo classInfo;
    private Type[] types;
    private final DotName extendedOrImplementedClass;
    private final int expectedParameterTypesCardinality;
    private final List<String> classNamesToRegisterForReflection = new ArrayList<>();

    protected SelectiveAugmentedClassInfo(ClassInfo classInfo, DotName extendedOrImplementedClass,
            int expectedParameterTypesCardinality) {
        this.extendedOrImplementedClass = extendedOrImplementedClass;
        this.classInfo = classInfo;
        this.expectedParameterTypesCardinality = expectedParameterTypesCardinality;
    }

    public ClassInfo classInfo() {
        return classInfo;
    }

    boolean keepAugmented(IndexView index, Logger log, Map<String, Object> context) {
        final var targetClassName = extendedOrImplementedClassName();
        final var consideredClassName = classInfo.name();
        if (Modifier.isAbstract(classInfo.flags())) {
            log.warnv("Skipping ''{0}'' {1} because it''s abstract",
                    consideredClassName, targetClassName);
            return false;
        }

        // Ignore implementations annotated with @Ignore
        if (classInfo.annotations().containsKey(IGNORE_ANNOTATION)) {
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

        return augmentIfKept(index, log, context);
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

    protected abstract boolean augmentIfKept(IndexView index, Logger log,
            Map<String, Object> context);

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
            hasNonVoidStatus = ClassUtils.isStatusNotVoid(statusClassName);
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
