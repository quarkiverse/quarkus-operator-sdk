package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.Controller;
import io.quarkiverse.operatorsdk.runtime.DelayRegistrationUntil;

@Controller(name = TestController.NAME)
@DelayRegistrationUntil(event = TestController.RegisterEvent.class)
public class TestController extends AbstractController<ChildTestResource> {

    // CDI Event to trigger registration
    public static class RegisterEvent {

    }

    public static final String NAME = "test";
}
