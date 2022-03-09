package io.quarkiverse.operatorsdk.csv.runtime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

public interface ShareableCSVMetadata<R extends HasMetadata> extends Reconciler<R> {
}
