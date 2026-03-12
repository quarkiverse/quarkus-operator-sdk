package io.quarkiverse.operatorsdk.common;

import java.util.Optional;
import java.util.function.Function;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;

import io.fabric8.kubernetes.client.CustomResource;

public class CustomResourceAugmentedClassInfo extends ReconciledResourceAugmentedClassInfo<CustomResource<?, ?>> {

    public static final String KEEP_CR_PREDICATE_KEY = "keep-cr-predicate";

    protected CustomResourceAugmentedClassInfo(ClassInfo classInfo, String associatedReconcilerName) {
        super(classInfo, Constants.CUSTOM_RESOURCE, 2, associatedReconcilerName);
    }

    @Override
    protected boolean doKeep(ClassUtils.IndexSearchContext context) {
        // only keep the information if the associated CRD hasn't already been generated
        return Optional.ofNullable(predicateFromContext(context))
                .map(predicate -> predicate.apply(this))
                .orElse(true);
    }

    @SuppressWarnings("unchecked")
    private Function<CustomResourceAugmentedClassInfo, Boolean> predicateFromContext(ClassUtils.IndexSearchContext context) {
        return (Function<CustomResourceAugmentedClassInfo, Boolean>) context.get(KEEP_CR_PREDICATE_KEY, Function.class);
    }

    @Override
    protected void doAugment(ClassUtils.IndexSearchContext context) {
        super.doAugment(context);

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
