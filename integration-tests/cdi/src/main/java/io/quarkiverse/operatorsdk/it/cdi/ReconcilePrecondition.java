package io.quarkiverse.operatorsdk.it.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class ReconcilePrecondition implements Condition {

    @Inject
    TestUUIDBean testUUIDBean;

    private String uuid;

    @Override

    public boolean isMet(DependentResource dependentResource, HasMetadata primary, Context context) {
        uuid = testUUIDBean.uuid();
        return true;
    }

    public String getUuid() {
        return uuid;
    }
}
