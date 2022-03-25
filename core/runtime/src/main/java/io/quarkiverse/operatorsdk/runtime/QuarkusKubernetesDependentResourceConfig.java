package io.quarkiverse.operatorsdk.runtime;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfig extends KubernetesDependentResourceConfig {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfig(String[] namespaces, String labelSelector) {
        super(namespaces, labelSelector);
    }

    public String[] getNamespaces() {
        return namespaces();
    }

    public String getLabelSelector() {
        return labelSelector();
    }
}
