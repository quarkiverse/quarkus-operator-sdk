package io.quarkiverse.operatorsdk.common;

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

import io.quarkus.deployment.util.JandexUtil;

public abstract class SelectiveAugmentedClassInfo {

    private final ClassInfo classInfo;
    private Type[] types;
    protected DotName extendedOrImplementedClass;
    private int expectedParameterTypesCardinality;

    private final List<String> classNamesToRegisterForReflection = new ArrayList<>();

    protected SelectiveAugmentedClassInfo(ClassInfo classInfo, DotName extendedOrImplementedClass,
            int expectedParameterTypesCardinality) {
        this(classInfo);
        init(extendedOrImplementedClass, expectedParameterTypesCardinality);
    }

    protected SelectiveAugmentedClassInfo(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }

    protected void init(DotName extendedOrImplementedClass,
            int expectedParameterTypesCardinality) {
        this.extendedOrImplementedClass = extendedOrImplementedClass;
        this.expectedParameterTypesCardinality = expectedParameterTypesCardinality;
    }

    public ClassInfo classInfo() {
        return classInfo;
    }

    protected boolean keep(IndexView index, Logger log, Map<String, Object> context) {
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

        return true;
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

    protected void augmentIfKept(IndexView index, Logger log, Map<String, Object> context) {
    }
}
