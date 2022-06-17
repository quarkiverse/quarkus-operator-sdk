package io.quarkiverse.operatorsdk.common;

import org.jboss.jandex.DotName;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class Constants {
    private Constants() {
    }

    public static final DotName RECONCILER = DotName.createSimple(Reconciler.class.getName());
    public static final DotName IGNORE_RECONCILER = DotName.createSimple(Ignore.class.getName());
    public static final DotName CUSTOM_RESOURCE = DotName.createSimple(CustomResource.class.getName());
    public static final DotName HAS_METADATA = DotName.createSimple(HasMetadata.class.getName());
    public static final DotName CONTROLLER_CONFIGURATION = DotName.createSimple(ControllerConfiguration.class.getName());
    public static final DotName KUBERNETES_DEPENDENT_RESOURCE = DotName
            .createSimple(KubernetesDependentResource.class.getName());
    public static final DotName KUBERNETES_DEPENDENT = DotName.createSimple(KubernetesDependent.class.getName());

    public static final DotName DEPENDENT_RESOURCE = DotName.createSimple(DependentResource.class.getName());
}
