package io.quarkiverse.operatorsdk.common;

import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.CustomResource;

public class ResourceTargetingAugmentedClassInfo extends SelectiveAugmentedClassInfo
        implements LoadableResourceHolder<CustomResource<?, ?>> {
    private final LoadableResourceHolder<CustomResource<?, ?>> holder;
    private final String reconcilerName;

    protected ResourceTargetingAugmentedClassInfo(ClassInfo classInfo, String associatedReconcilerName) {
        super(classInfo, Constants.CUSTOM_RESOURCE, 2);
        this.reconcilerName = associatedReconcilerName;
        this.holder = new SimpleLoadableResourceHolder<>(classInfo);
    }

    @Override
    protected boolean augmentIfKept(IndexView index, Logger log) {
        return true;
    }

    public String getAssociatedResourceTypeName() {
        return holder.getAssociatedResourceTypeName();
    }

    public Optional<String> getAssociatedReconcilerName() {
        return Optional.ofNullable(reconcilerName);
    }

    public Class<CustomResource<?, ?>> loadAssociatedClass() {
        return holder.loadAssociatedClass();
    }
}
