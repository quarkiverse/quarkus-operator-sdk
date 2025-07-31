package io.quarkiverse.operatorsdk.test.sources;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

public class ReadOnlySecret implements DependentResource<Secret, TestCR> {

    @Override
    public ReconcileResult<Secret> reconcile(TestCR testCR, Context<TestCR> context) {
        return null;
    }

    @Override
    public Class<Secret> resourceType() {
        return Secret.class;
    }
}
