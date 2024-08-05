package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfigHolder;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfig<R extends HasMetadata> extends KubernetesDependentResourceConfig<R> {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfig(Boolean useSSA, boolean createResourceOnlyIfNotExistingWithSSA,
            QuarkusInformerConfigHolder<R> informerConfig) {
        super(useSSA, createResourceOnlyIfNotExistingWithSSA, informerConfig);
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public boolean isCreateResourceOnlyIfNotExistingWithSSA() {
        return createResourceOnlyIfNotExistingWithSSA();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public Boolean isUseSSA() {
        return useSSA();
    }

    // Getter required for Quarkus' RecordableConstructor, must match the associated constructor parameter name
    @SuppressWarnings("unused")
    public InformerConfigHolder<R> getInformerConfig() {
        return informerConfig();
    }

    void setNamespaces(Set<String> namespaces) {
        // todo: remove?
    }
}
