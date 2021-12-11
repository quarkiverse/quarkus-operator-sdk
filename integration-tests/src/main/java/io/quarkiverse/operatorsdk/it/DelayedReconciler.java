package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.quarkiverse.operatorsdk.runtime.DelayRegistrationUntil;

@ControllerConfiguration(name = DelayedReconciler.NAME)
@DelayRegistrationUntil(event = DelayedReconciler.RegisterEvent.class)
public class DelayedReconciler extends AbstractReconciler<Delayed> {
    // CDI Event to trigger registration
    public static class RegisterEvent {

    }

    public static final String NAME = "delayed";
}
