package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE)
public class KeycloakController implements Reconciler<Keycloak> {

    public static final String FROM_ENV = "keycloak-ns";

    @Override
    public UpdateControl<Keycloak> reconcile(Keycloak keycloak, Context<Keycloak> context)
            throws Exception {
        return null;
    }
}
