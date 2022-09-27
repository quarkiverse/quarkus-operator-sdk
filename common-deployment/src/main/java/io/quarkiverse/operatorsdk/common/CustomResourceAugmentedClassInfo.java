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

    private boolean hasNonVoidStatus;

    protected CustomResourceAugmentedClassInfo(ClassInfo classInfo, String associatedReconcilerName) {
        super(classInfo, Constants.CUSTOM_RESOURCE, 2, associatedReconcilerName);
    }

    @Override
    protected boolean doKeep(IndexView index, Logger log, Map<String, Object> context) {

        // only keep the information if the associated CRD hasn't already been generated
        final var fullName = fullResourceName();
        return Optional.ofNullable(context.get(EXISTING_CRDS_KEY))
                .map(value -> {
                    @SuppressWarnings("unchecked")
                    Set<String> generated = (Set<String>) value;
                    return !generated.contains(fullName);
                })
                .orElse(true);
    }

    @Override
    protected void doAugment(IndexView index, Logger log, Map<String, Object> context) {
        super.doAugment(index, log, context);

        // check if the primary is also a CR, in which case we also need to register its
        // spec and status classes if we can determine them
        // register spec and status for reflection if we're targeting a CustomResource
        // note that this shouldn't be necessary anymore once https://github.com/quarkusio/quarkus/pull/26188
        // is merged and available as the kubernetes-client extension will properly take care of the
        // registration of the custom resource and associated status / spec classes for reflection
        final var specClassName = typeAt(0).name().toString();
        final var statusClassName = typeAt(1).name().toString();
        hasNonVoidStatus = ClassUtils.isStatusNotVoid(statusClassName);
        registerForReflection(specClassName);
        registerForReflection(statusClassName);
    }

    @Override
    public boolean isCR() {
        return true;
    }

    @Override
    public boolean hasNonVoidStatus() {
        return hasNonVoidStatus;
    }
}
