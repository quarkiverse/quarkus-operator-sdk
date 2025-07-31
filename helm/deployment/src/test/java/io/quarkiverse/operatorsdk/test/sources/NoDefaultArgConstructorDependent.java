package io.quarkiverse.operatorsdk.test.sources;

import jakarta.inject.Singleton;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

@KubernetesDependent
@Singleton
public class NoDefaultArgConstructorDependent extends KubernetesDependentResource<Secret, TestCR> {

    public NoDefaultArgConstructorDependent(InjectedDependency injectedDependency) {
        super(Secret.class);
    }
}
