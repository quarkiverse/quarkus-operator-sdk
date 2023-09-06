package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.openshift.api.model.Role;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(name = "illegal")
public class DuplicatedBundleNameWithoutSharedCSVMetadata1 implements Reconciler<Role> {

    @Override
    public UpdateControl<Role> reconcile(Role role, Context<Role> context) throws Exception {
        return null;
    }
}
