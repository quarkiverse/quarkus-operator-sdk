package io.quarkiverse.operatorsdk.common;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.CustomResource;

public class ResourceTargetingAugmentedClassInfo extends SelectiveAugmentedClassInfo
        implements LoadableResourceHolder<CustomResource<?, ?>> {
    private final LoadableResourceHolder<CustomResource<?, ?>> holder;
    private final String reconcilerName;
    public static final String EXISTING_CRDS_KEY = "existing-crds-key";

    protected ResourceTargetingAugmentedClassInfo(ClassInfo classInfo, String associatedReconcilerName) {
        super(classInfo, Constants.CUSTOM_RESOURCE, 2);
        this.reconcilerName = associatedReconcilerName;
        this.holder = new SimpleLoadableResourceHolder<>(classInfo);
    }

    @Override
    protected boolean augmentIfKept(IndexView index, Logger log, Map<String, Object> context) {
        // only keep the information if the associated CRD hasn't already been generated
        return Optional.ofNullable(context.get(EXISTING_CRDS_KEY))
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Set<String> generated = (Set<String>) value;
                    return !generated.contains(getAssociatedResourceTypeName());
                })
                .orElse(true);
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
