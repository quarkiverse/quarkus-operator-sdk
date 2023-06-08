package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata.Annotations;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata.Annotations.Annotation;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata.RequiredCRD;

@CSVMetadata(name = "third-operator", requiredCRDs = @RequiredCRD(kind = SecondExternal.KIND, name = "externalagains."
        + SecondExternal.GROUP, version = SecondExternal.VERSION), replaces = "1.0.0", annotations = @Annotations(skipRange = ">=1.0.0 <1.0.3", capabilities = "Test", others = @Annotation(name = "foo", value = "bar")))
@ControllerConfiguration(dependents = {
        @Dependent(type = ExternalDependentResource.class),
        @Dependent(type = PodDependentResource.class)
})
public class ThirdReconciler implements Reconciler<Third> {

    @Override
    public UpdateControl<Third> reconcile(Third third, Context<Third> context) {
        return UpdateControl.noUpdate();
    }
}
