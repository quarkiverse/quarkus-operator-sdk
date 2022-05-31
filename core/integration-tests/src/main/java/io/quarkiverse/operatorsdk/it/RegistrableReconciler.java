package io.quarkiverse.operatorsdk.it;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public interface RegistrableReconciler<R extends HasMetadata> extends Reconciler<R> {

    boolean isInitialized();
}
