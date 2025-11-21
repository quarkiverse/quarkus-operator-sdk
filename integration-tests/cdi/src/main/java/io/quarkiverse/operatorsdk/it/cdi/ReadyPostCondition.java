package io.quarkiverse.operatorsdk.it.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class ReadyPostCondition implements Condition<Deployment, TestResource> {

    @Inject
    TestUUIDBean testUUIDBean;

    private String uuid;

    @Override
    public boolean isMet(DependentResource<Deployment, TestResource> dependentResource, TestResource primary,
            Context<TestResource> context) {
        uuid = testUUIDBean.uuid();
        return dependentResource
                .getSecondaryResource(primary, context)
                .map(deployment -> deployment.getSpec().getReplicas().equals(1))
                .orElse(false);
    }

    public String getUuid() {
        return uuid;
    }
}
