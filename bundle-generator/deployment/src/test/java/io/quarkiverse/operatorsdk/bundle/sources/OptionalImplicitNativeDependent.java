package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition;

public class OptionalImplicitNativeDependent extends KubernetesDependentResource<Secret, Third> {
    public static class ActivationCondition extends CRDPresentActivationCondition<Secret, Third> {
        @Override
        public boolean isMet(DependentResource<Secret, Third> dependentResource, Third primary, Context<Third> context) {
            return false;
        }
    }
}
