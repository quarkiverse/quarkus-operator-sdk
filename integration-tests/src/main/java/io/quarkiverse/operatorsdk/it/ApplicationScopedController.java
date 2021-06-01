package io.quarkiverse.operatorsdk.it;

import javax.enterprise.context.ApplicationScoped;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

@ApplicationScoped
@Controller(name = ApplicationScopedController.NAME)
public class ApplicationScopedController implements ResourceController<ChildTestResource> {

    public static final String NAME = "ApplicationScoped";

    @Override
    public UpdateControl<ChildTestResource> createOrUpdateResource(ChildTestResource childTestResource,
            Context<ChildTestResource> context) {
        return UpdateControl.noUpdate();
    }
}
