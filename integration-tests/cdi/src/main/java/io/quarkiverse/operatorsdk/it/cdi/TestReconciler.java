package io.quarkiverse.operatorsdk.it.cdi;

import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {
        @Dependent(name = TestReconciler.DEPLOYMENT, type = DeploymentDependent.class, activationCondition = CustomActionvationCondition.class, readyPostcondition = ReadyPostCondition.class, reconcilePrecondition = ReconcilePrecondition.class),
        @Dependent(type = ConfigMapDependent.class, deletePostcondition = DeletePostCondition.class, dependsOn = TestReconciler.DEPLOYMENT)
})
@ControllerConfiguration
public class TestReconciler implements Reconciler<TestResource>, Cleaner<TestResource> {

    public static final String DEPLOYMENT = "deployment";

    @Override
    public UpdateControl<TestResource> reconcile(TestResource resource, Context<TestResource> context) throws Exception {
        return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl cleanup(TestResource resource, Context<TestResource> context) throws Exception {
        return DeleteControl.defaultDelete();
    }
}
