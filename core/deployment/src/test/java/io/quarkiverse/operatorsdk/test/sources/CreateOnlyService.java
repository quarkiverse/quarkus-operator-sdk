package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.Creator;

public class CreateOnlyService implements DependentResource<Service, TestCR>, Creator<Service, TestCR> {

    @Override
    public ReconcileResult<Service> reconcile(TestCR testCR, Context<TestCR> context) {
        return null;
    }

    @Override
    public Class<Service> resourceType() {
        return Service.class;
    }

    @Override
    public Service create(Service service, TestCR testCR, Context<TestCR> context) {
        return null;
    }
}
