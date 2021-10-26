package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.Controller;
import io.quarkiverse.operatorsdk.runtime.DelayRegistrationUntil;

@Controller(name = DelayedController.NAME)
@DelayRegistrationUntil(event = DelayedController.RegisterEvent.class)
public class DelayedController extends AbstractController<Delayed> {
    // CDI Event to trigger registration
    public static class RegisterEvent {

    }

    public static final String NAME = "delayed";
}
