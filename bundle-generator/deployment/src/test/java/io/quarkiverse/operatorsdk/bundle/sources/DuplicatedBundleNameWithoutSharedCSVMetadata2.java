package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.openshift.api.model.RoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(bundleName = "illegal")
public class DuplicatedBundleNameWithoutSharedCSVMetadata2 implements Reconciler<RoleBinding> {

    @Override
    public UpdateControl<RoleBinding> reconcile(RoleBinding roleBinding, Context<RoleBinding> context) {
        return null;
    }
}
