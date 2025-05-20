package io.quarkiverse.operatorsdk.bundle.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata.Annotations;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata.Annotations.Annotation;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata.Label;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata.RequiredCRD;

@Workflow(dependents = {
        @Dependent(type = ExternalDependentResource.class),
        @Dependent(name = "pod1", type = PodDependentResource.class),
        @Dependent(name = "pod2", type = PodDependentResource.class)
})
@CSVMetadata(bundleName = ThirdReconciler.BUNDLE_NAME, requiredCRDs = @RequiredCRD(kind = SecondExternal.KIND, name = "externalagains."
        + SecondExternal.GROUP, version = SecondExternal.VERSION), replaces = "1.0.0", annotations = @Annotations(skipRange = ">=1.0.0 <1.0.3", capabilities = "Test", repository = "should be overridden by property", others = @Annotation(name = "foo", value = "bar")), labels = {
                @Label(name = "test-label", value = "test-value"),
                @Label(name = "test-label2", value = "test-value2") })
@ControllerConfiguration(name = ThirdReconciler.NAME)
public class ThirdReconciler implements Reconciler<Third> {

    public static final String NAME = "third";
    public static final String BUNDLE_NAME = "third-operator";

    @Override
    public UpdateControl<Third> reconcile(Third third, Context<Third> context) {
        return UpdateControl.noUpdate();
    }
}
