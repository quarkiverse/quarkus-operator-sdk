package io.quarkiverse.operatorsdk.common;

import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ReconciledResourceAugmentedClassInfo<T extends HasMetadata> extends ReconciledAugmentedClassInfo<T> {

    public static final String STATUS = "status";
    protected boolean hasStatus;

    protected ReconciledResourceAugmentedClassInfo(ClassInfo classInfo,
            DotName extendedOrImplementedClass, int expectedParameterTypesCardinality,
            String associatedReconcilerName) {
        super(classInfo, extendedOrImplementedClass, expectedParameterTypesCardinality,
                associatedReconcilerName);
    }

    public String fullResourceName() {
        return HasMetadataUtils.getFullResourceName(classInfo());
    }

    public String kind() {
        return HasMetadataUtils.getKind(classInfo());
    }

    public String version() {
        return HasMetadataUtils.getVersion(classInfo());
    }

    public String group() {
        return HasMetadataUtils.getGroup(classInfo());
    }

    @Override
    public boolean isResource() {
        return true;
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        super.doAugment(index, log, context);
        hasStatus = hasStatus(index);
    }

    protected boolean hasStatus(IndexView index) {
        return ClassUtils.hasField(index, classInfo(), STATUS);
    }

    public boolean hasNonVoidStatus() {
        return hasStatus;
    }
}
