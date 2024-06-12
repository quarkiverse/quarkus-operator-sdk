package io.quarkiverse.operatorsdk.runtime;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.quarkus.runtime.annotations.RecordableConstructor;

public class QuarkusKubernetesDependentResourceConfig<R extends HasMetadata> extends KubernetesDependentResourceConfig<R> {

    @RecordableConstructor
    public QuarkusKubernetesDependentResourceConfig(Boolean useSSA, boolean createResourceOnlyIfNotExistingWithSSA,
            InformerConfiguration.InformerConfigurationBuilder<R> informerConfiguration) {
        super(useSSA, createResourceOnlyIfNotExistingWithSSA, informerConfiguration);
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
    public InformerConfiguration.InformerConfigurationBuilder<R> getInformerConfiguration() {
        return informerConfigurationBuilder();
    }

    void setNamespaces(Set<String> namespaces) {
    }
}
