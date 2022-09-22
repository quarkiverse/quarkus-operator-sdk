package io.quarkiverse.operatorsdk.common;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ReconciledResourceAugmentedClassInfo<T extends HasMetadata> extends ReconciledAugmentedClassInfo<T> {

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

    @Override
    public boolean isResource() {
        return true;
    }

    public boolean hasNonVoidStatus() {
        return false;
    }
}
