package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.Controller;

@Controller(name = ConfiguredController.NAME, namespaces = "foo")
public class ConfiguredController extends AbstractController<ChildTestResource2> {

    public static final String NAME = "annotation";
}
