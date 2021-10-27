package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRoleDecorator.getClusterRoleName;

import java.util.Set;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;

public class AddClusterRoleBindingDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {
    private final Set<String> controllerNames;

    public AddClusterRoleBindingDecorator(Set<String> controllerNames) {
        this.controllerNames = controllerNames;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        final var serviceAccountName = getMandatoryDeploymentMetadata(list).getName();

        controllerNames.forEach(controllerName -> {
            list.addToItems(new ClusterRoleBindingBuilder()
                    .withNewMetadata().withName(controllerName + "-cluster-role-binding").endMetadata()
                    .withNewRoleRef("rbac.authorization.k8s.io", "ClusterRole", getClusterRoleName(controllerName))
                    .addNewSubject(null, "ServiceAccount", serviceAccountName, null)
                    .build());
        });
    }
}
