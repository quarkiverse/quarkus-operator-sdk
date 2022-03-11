package io.quarkiverse.operatorsdk.runtime;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfig extends KubernetesDependentResourceConfig {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfig(boolean addOwnerReference,
            String[] namespaces, String labelSelector) {
        super(addOwnerReference, namespaces, labelSelector);
    }
}
