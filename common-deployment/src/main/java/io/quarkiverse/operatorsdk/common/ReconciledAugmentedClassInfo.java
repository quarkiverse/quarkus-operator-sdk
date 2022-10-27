package io.quarkiverse.operatorsdk.common;

import static io.quarkiverse.operatorsdk.common.ClassLoadingUtils.loadClass;
import static io.quarkiverse.operatorsdk.common.Constants.CUSTOM_RESOURCE;
import static io.quarkiverse.operatorsdk.common.Constants.HAS_METADATA;
import static io.quarkiverse.operatorsdk.common.Constants.OBJECT;

import java.util.Map;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.util.JandexUtil;

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

    @SuppressWarnings("rawtypes")
    public static ReconciledAugmentedClassInfo createFor(ClassInfo resourceCI, String name,
            IndexView index, Logger log, Map<String, Object> context) {
        var isResource = false;
        var isCR = false;
        try {
            isResource = ClassUtils.isImplementationOf(index, resourceCI, HAS_METADATA);
            if (isResource) {
                isCR = JandexUtil.isSubclassOf(index, resourceCI, CUSTOM_RESOURCE);
            }
        } catch (BuildException e) {
            log.errorv(
                    "Couldn't ascertain if ''{0}'' is a CustomResource or HasMetadata subclass. Assumed not to be.",
                    e);
        }

        ReconciledAugmentedClassInfo reconciledInfo;
        if (isCR) {
            reconciledInfo = new CustomResourceAugmentedClassInfo(resourceCI, name);
        } else if (isResource) {
            reconciledInfo = new ReconciledResourceAugmentedClassInfo<>(resourceCI, HAS_METADATA, 0,
                    name);
        } else {
            reconciledInfo = new ReconciledAugmentedClassInfo<>(resourceCI, OBJECT, 0, name);
        }
        // make sure the associated resource is properly initialized
        reconciledInfo.augmentIfKept(index, log, context);
        return reconciledInfo;
    }
}
