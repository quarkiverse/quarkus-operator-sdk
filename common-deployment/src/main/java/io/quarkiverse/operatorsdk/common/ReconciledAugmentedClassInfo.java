package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;

import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ReconciledAugmentedClassInfo<T> extends SelectiveAugmentedClassInfo {
    private Class<T> clazz;
    private final String resourceClassName;
    private final String reconcilerName;

    protected ReconciledAugmentedClassInfo(ClassInfo classInfo, DotName extendedOrImplementedClass,
            int expectedParameterTypesCardinality,
            String associatedReconcilerName) {
        super(classInfo, extendedOrImplementedClass, expectedParameterTypesCardinality);
        this.resourceClassName = classInfo.name().toString();
        this.reconcilerName = associatedReconcilerName;
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        // record target resource class for later forced registration for reflection
        registerForReflection(classInfo().name().toString());
    }

    @SuppressWarnings("unchecked")
    public Class<T> loadAssociatedClass() {
        if (clazz == null) {
            clazz = (Class<T>) loadClass(resourceClassName, HasMetadata.class);
        }
        return clazz;
    }

    public boolean isCR() {
        return false;
    }

    public boolean isResource() {
        return false;
    }

    public Optional<String> getAssociatedReconcilerName() {
        return Optional.ofNullable(reconcilerName);
    }

    @SuppressWarnings("rawtypes")
    public ReconciledResourceAugmentedClassInfo asResourceTargeting() {
        return (ReconciledResourceAugmentedClassInfo) this;
    }
}
