package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.Pod;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkus.arc.properties.IfBuildProperty;

@IfBuildProperty(name = "foo", stringValue = "some property value that is not set")
public class BuildTimeExcludedReconciler implements Reconciler<Pod> {
    @Override
    public UpdateControl<Pod> reconcile(Pod pod, Context<Pod> context) throws Exception {
        return null;
    }
}
