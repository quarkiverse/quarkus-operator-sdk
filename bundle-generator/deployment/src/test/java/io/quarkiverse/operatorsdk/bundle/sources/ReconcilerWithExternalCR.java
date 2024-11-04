package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;

@ControllerConfiguration(dependents = @Dependent(type = ExternalDependentResource.class))
@CSVMetadata(requiredCRDs = @CSVMetadata.RequiredCRD(kind = V1Beta1CRD.KIND, name = V1Beta1CRD.CR_NAME, version = V1Beta1CRD.VERSION))
public class ReconcilerWithExternalCR implements Reconciler<First> {
    @Override
    public UpdateControl<First> reconcile(First first, Context<First> context) throws Exception {
        return null;
    }
}
