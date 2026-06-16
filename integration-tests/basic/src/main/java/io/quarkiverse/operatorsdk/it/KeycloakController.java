package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE), maxReconciliationInterval = @MaxReconciliationInterval(interval = Constants.NO_MAX_RECONCILIATION_INTERVAL))
public class KeycloakController implements Reconciler<Keycloak> {

    public static final String FROM_ENV = Constants.WATCH_ALL_NAMESPACES;

    @Override
    public UpdateControl<Keycloak> reconcile(Keycloak keycloak, Context<Keycloak> context)
            throws Exception {
        return null;
    }
}
