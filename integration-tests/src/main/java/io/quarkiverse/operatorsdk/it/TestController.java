package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.Controller;

@Controller(name = TestController.NAME)
public class TestController extends AbstractController<Test> {
    public static final String NAME = "test";
}
