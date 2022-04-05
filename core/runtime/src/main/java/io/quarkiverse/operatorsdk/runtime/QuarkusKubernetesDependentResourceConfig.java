package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfig extends KubernetesDependentResourceConfig {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfig(Set<String> namespaces, String labelSelector) {
        super(namespaces, labelSelector);
    }

    // Needed for the recordable constructor
    public Set<String> getNamespaces() {
        return namespaces();
    }

    // Needed for the recordable constructor
    public String getLabelSelector() {
        return labelSelector();
    }
}
