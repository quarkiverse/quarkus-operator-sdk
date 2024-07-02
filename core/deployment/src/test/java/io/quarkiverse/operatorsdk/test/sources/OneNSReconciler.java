package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfig;

@ControllerConfiguration(informerConfig = @InformerConfig(namespaces = OneNSReconciler.NS))
public class OneNSReconciler implements Reconciler<TestCR> {

    public static final String NS = "foo";

    @Override
    public UpdateControl<TestCR> reconcile(TestCR testCR, Context<TestCR> context) throws Exception {
        return null;
    }
}
