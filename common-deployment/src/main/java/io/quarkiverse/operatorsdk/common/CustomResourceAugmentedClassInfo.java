package io.quarkiverse.operatorsdk.common;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.fabric8.kubernetes.client.CustomResource;

public class CustomResourceAugmentedClassInfo extends ReconciledResourceAugmentedClassInfo<CustomResource<?, ?>> {

    public static final String EXISTING_CRDS_KEY = "existing-crds-key";

    protected CustomResourceAugmentedClassInfo(ClassInfo classInfo, String associatedReconcilerName) {
        super(classInfo, Constants.CUSTOM_RESOURCE, 2, associatedReconcilerName);
    }

    @Override
    protected boolean doKeep(IndexView index, Logger log, Map<String, Object> context) {
        // only keep the information if the associated CRD hasn't already been generated
        return Optional.ofNullable(context.get(EXISTING_CRDS_KEY))
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Set<String> generated = (Set<String>) value;
                    return !generated.contains(id());
                })
                .orElse(true);
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        super.doAugment(index, log, context);

        // registering these classes is not necessary anymore since the kubernetes client extension takes care of it
        // however, we keep doing it here so that the name of these classes appear in the logs as has been the case since the first version of this extension
        final var specClassName = typeAt(0).name().toString();
        final var statusClassName = typeAt(1).name().toString();
        registerForReflection(specClassName);
        registerForReflection(statusClassName);
    }

    @Override
    protected boolean hasStatus(IndexView index) {
        final var statusClassName = typeAt(1).name().toString();
        return ClassUtils.isStatusNotVoid(statusClassName);
    }

    @Override
    public boolean isCR() {
        return true;
    }
}
