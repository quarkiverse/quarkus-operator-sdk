package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfig;

@ControllerConfiguration(name = CustomRateLimiterReconciler.NAME, rateLimiter = CustomRateLimiter.class, informerConfig = @InformerConfig(itemStore = NullItemStore.class))
@CustomRateConfiguration(42)
public class CustomRateLimiterReconciler implements Reconciler<ResourceQuota> {

    public static final String NAME = "CustomRateLimiter";

    @Override
    public UpdateControl<ResourceQuota> reconcile(ResourceQuota resourceQuota, Context<ResourceQuota> context)
            throws Exception {
        return null;
    }
}
