package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;

public class NonKubeResource implements DependentResource<Foo, TestCR> {

    @Override
    public ReconcileResult<Foo> reconcile(TestCR testCR, Context<TestCR> context) {
        return null;
    }

    @Override
    public Class<Foo> resourceType() {
        return Foo.class;
    }
}
