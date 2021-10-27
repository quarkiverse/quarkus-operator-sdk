package io.quarkiverse.operatorsdk.deployment;

import java.util.Map;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.quarkiverse.operatorsdk.common.CustomResourceInfo;

public class AddClusterRoleDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {
    private final Map<String, CustomResourceInfo> controllerToCustomResourceMappings;
    private final boolean validateCRDs;

    public AddClusterRoleDecorator(Map<String, CustomResourceInfo> controllerToCustomResourceMappings, boolean validateCRDs) {
        this.controllerToCustomResourceMappings = controllerToCustomResourceMappings;
        this.validateCRDs = validateCRDs;
    }

    @Override
    public void visit(KubernetesListBuilder list) {
        controllerToCustomResourceMappings.forEach((controller, cri) -> {
            final var rule = new PolicyRuleBuilder();
            final var plural = cri.getPlural();
            rule.addNewResource(plural);

            // if the resource has a non-Void status, also add the status resource
            cri.getStatusClassName().ifPresent(statusClass -> {
                if (!"java.lang.Void".equals(statusClass)) {
                    rule.addNewResource(plural + "/status");
                }
            });

            rule.addNewApiGroup(cri.getGroup())
                    .addToVerbs("get", "list", "watch", "create", "delete", "patch", "update")
                    .build();

            final var clusterRoleBuilder = new ClusterRoleBuilder()
                    .withNewMetadata()
                    .withName(getClusterRoleName(controller))
                    .endMetadata()
                    .addToRules(rule.build());

            list.addToItems(clusterRoleBuilder.build());
        });

        // if we're asking to validate the CRDs, also add CRDs permissions, once
        if (validateCRDs) {
            final var crName = getMandatoryDeploymentMetadata(list).getName() + "-crd-validating-cluster-role";

            if (!contains(list, HasMetadata.getApiVersion(ClusterRole.class), HasMetadata.getKind(ClusterRole.class), crName)) {
                list.addToItems(new ClusterRoleBuilder().withNewMetadata().withName(crName).endMetadata()
                        .addToRules(new PolicyRuleBuilder()
                                .addNewApiGroup("apiextensions.k8s.io")
                                .addNewResource("customresourcedefinitions")
                                .addToVerbs("get", "list")
                                .build()));
            }
        }
    }

    static String getClusterRoleName(String controller) {
        return controller + "-cluster-role";
    }
}
