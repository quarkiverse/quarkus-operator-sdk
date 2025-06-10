package io.quarkiverse.operatorsdk.test.sources;

import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.quarkiverse.operatorsdk.runtime.api.ConfigurableReconciler;

public class ConfiguredReconciler implements ConfigurableReconciler<TestCR> {

    public static final String LABEL_SELECTOR = "foo=bar";

    @Override
    public UpdateControl<TestCR> reconcile(TestCR testCR, Context<TestCR> context) throws Exception {
        return null;
    }

    @Override
    public void updateConfigurationFrom(ControllerConfigurationOverrider<TestCR> configOverrider) {
        configOverrider.withLabelSelector(LABEL_SELECTOR);
    }
}
