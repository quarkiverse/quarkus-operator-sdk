package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(name = CustomRateLimiterReconciler.NAME, rateLimiter = CustomRateLimiter.class)
@CustomRateConfiguration(42)
public class CustomRateLimiterReconciler implements Reconciler<ResourceQuota> {

    public static final String NAME = "CustomRateLimiter";

    @Override
    public UpdateControl<ResourceQuota> reconcile(ResourceQuota resourceQuota, Context<ResourceQuota> context)
            throws Exception {
        return null;
    }
}
