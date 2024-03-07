package io.quarkiverse.operatorsdk.bundle.sources;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@CSVMetadata(name = AReconciler.SHARED, version = "0.0.2")
public class BReconciler implements Reconciler<Service> {

    @Override
    public UpdateControl<Service> reconcile(Service service, Context<Service> context) {
        return null;
    }
}
