package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;

@Workflow(dependents = {
        @Dependent(type = CRUDConfigMap.class),
        @Dependent(type = ReadOnlySecret.class),
        @Dependent(type = CreateOnlyService.class),
        @Dependent(type = NonKubeResource.class),
        @Dependent(type = TypelessKubeResource.class),
        @Dependent(type = TypelessAnotherKubeResource.class),
        @Dependent(type = NoDefaultArgConstructorDependent.class)
})
@ControllerConfiguration(name = TestReconciler.NAME)
@RBACRule(verbs = RBACVerbs.UPDATE, apiGroups = RBACRule.ALL, resources = RBACRule.ALL)
public class TestReconciler implements Reconciler<TestCR> {

    public static final String NAME = "test";

    @Override
    public UpdateControl<TestCR> reconcile(TestCR testCR, Context<TestCR> context) {
        return null;
    }
}
