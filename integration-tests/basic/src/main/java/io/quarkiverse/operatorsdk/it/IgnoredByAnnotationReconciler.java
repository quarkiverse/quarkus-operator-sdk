package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Ignore
public class IgnoredByAnnotationReconciler implements Reconciler<TestResource> {

    @Override
    public UpdateControl<TestResource> reconcile(TestResource testResource, Context context) {
        return UpdateControl.noUpdate();
    }
}
