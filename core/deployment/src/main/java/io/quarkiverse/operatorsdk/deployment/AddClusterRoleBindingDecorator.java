package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRoleDecorator.getClusterRoleName;

import java.util.Set;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;

public class AddClusterRoleBindingDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {
    private final Set<String> controllerNames;
    private final String serviceAccountName;

    public AddClusterRoleBindingDecorator(Set<String> controllerNames, String serviceAccountName) {
        this.controllerNames = controllerNames;
        this.serviceAccountName = serviceAccountName;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        controllerNames.forEach(controllerName -> list.addToItems(new ClusterRoleBindingBuilder()
                .withNewMetadata().withName(controllerName + "-cluster-role-binding").endMetadata()
                .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole", getClusterRoleName(controllerName))
                .addNewSubject(null, "ServiceAccount", serviceAccountName, null)
                .build()));
    }
}
