package io.quarkiverse.operatorsdk.it;

import javax.enterprise.context.ApplicationScoped;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ApplicationScoped
@ControllerConfiguration(name = ApplicationScopedController.NAME)
public class ApplicationScopedController implements Reconciler<ChildTestResource> {

    public static final String NAME = "ApplicationScoped";

    @Override
    public UpdateControl<ChildTestResource> reconcile(ChildTestResource childTestResource,
            Context context) {
        return UpdateControl.noUpdate();
    }
}
