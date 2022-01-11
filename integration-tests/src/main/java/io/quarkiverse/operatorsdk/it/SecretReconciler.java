package io.quarkiverse.operatorsdk.it;

import java.util.Base64;
import java.util.HashMap;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(finalizerName = Constants.NO_FINALIZER)
public class SecretReconciler implements Reconciler<Secret> {

    @Override
    public UpdateControl<Secret> reconcile(Secret secret, Context context) {
        if (secret.getType().equals("Opaque")) {
            final var labels = secret.getMetadata().getLabels();
            if (labels != null && "true".equals(labels.get("quarkus-operator-sdk.secret-reconciler-marker"))) {
                System.out.println("Reconciling secret " + secret.getMetadata().getName());

                var data = secret.getData();
                if (data == null) {
                    data = new HashMap<>();
                    secret.setStringData(data);
                }
                final String foo = data.putIfAbsent("quarkus-operator-sdk.added-value",
                        Base64.getEncoder().encodeToString("quarkus-operator-sdk rocks!".getBytes()));
                ;
                if (foo == null) {
                    return UpdateControl.updateResource(secret);
                }
            }
        }
        return UpdateControl.noUpdate();
    }
}
