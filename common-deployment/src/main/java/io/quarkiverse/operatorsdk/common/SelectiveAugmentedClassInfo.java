package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.Constants.IGNORE_ANNOTATION;
import static io.quarkiverse.operatorsdk.common.Constants.OBJECT;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Type;

import io.quarkus.deployment.util.JandexUtil;

public abstract class SelectiveAugmentedClassInfo {

    private final ClassInfo classInfo;
    private Type[] types;
    private final DotName extendedOrImplementedClass;
    private final int expectedParameterTypesCardinality;
    private final Map<Object, Object> extendedInfos = new ConcurrentHashMap<>();

    private final List<String> classNamesToRegisterForReflection = new ArrayList<>();

    protected SelectiveAugmentedClassInfo(ClassInfo classInfo, DotName extendedOrImplementedClass,
            int expectedParameterTypesCardinality) {
        this.classInfo = classInfo;
        this.extendedOrImplementedClass = extendedOrImplementedClass;
        this.expectedParameterTypesCardinality = expectedParameterTypesCardinality;
    }

    public ClassInfo classInfo() {
        return classInfo;
    }

    protected void logSkipping(ClassUtils.IndexSearchContext context, String reason) {
        final var targetClassName = extendedOrImplementedClassName();
        final var consideredClassName = classInfo.name();
        context.log().debugf("Skipping '%s' %s. Reason: %s", consideredClassName, targetClassName, reason);
    }

    protected boolean keep(ClassUtils.IndexSearchContext context) {
        if (Modifier.isAbstract(classInfo.flags())) {
            logSkipping(context, "abstract class");
            return false;
        }

        // Ignore implementations annotated with @Ignore
        if (classInfo.annotationsMap().containsKey(IGNORE_ANNOTATION)) {
            logSkipping(context, "annotated with @Ignore");
            return false;
        }

        // Ignore implementations that are excluded at build time
        if (context.isExcluded(classInfo.name().toString())) {
            logSkipping(context, "excluded at build time via @IfBuildProperty or similar annotation");
            return false;
        }

        initTypesIfNeeded(context.indexView());

        return doKeep(context);
    }

    protected boolean doKeep(ClassUtils.IndexSearchContext context) {
        return true;
    }

    private void initTypesIfNeeded(IndexView index) {
        if (expectedParameterTypesCardinality > 0 && types == null && !OBJECT.equals(extendedOrImplementedClass)) {
            final List<Type> typeParameters;
            try {
                typeParameters = JandexUtil.resolveTypeParameters(classInfo.name(),
                        extendedOrImplementedClass, index);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot process " + classInfo.simpleName()
                        + " as an implementation/extension of " + extendedOrImplementedClassName(), e);
            }
            if (expectedParameterTypesCardinality != typeParameters.size()) {
                throw new IllegalArgumentException("Cannot process " + classInfo.simpleName()
                        + " as an implementation/extension of " + extendedOrImplementedClassName()
                        + " because it doesn't match the expected cardinality ("
                        + expectedParameterTypesCardinality + ") of type parameters");
            }
            this.types = typeParameters.toArray(Type[]::new);
        }
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
        return extendedOrImplementedClass.withoutPackagePrefix();
    }

    protected void augmentIfKept(ClassUtils.IndexSearchContext context) {
        initTypesIfNeeded(context.indexView());
        doAugment(context);
    }

    protected abstract void doAugment(ClassUtils.IndexSearchContext context);

    @SuppressWarnings({ "unchecked", "unused" })
    public <T> T getExtendedInfo(Object key, Class<T> expectedValueType) {
        return (T) extendedInfos.get(key);
    }

    public void setExtendedInfo(Object key, Object value) {
        extendedInfos.put(key, value);
    }
}
