package io.quarkiverse.operatorsdk.deployment;

import java.util.Map;

import io.dekorate.kubernetes.decorator.ResourceProvidingDecorator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesListBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.quarkiverse.operatorsdk.common.ResourceInfo;

public class AddClusterRolesDecorator extends ResourceProvidingDecorator<KubernetesListBuilder> {

    static final String JOSDK_CRD_VALIDATING_CLUSTER_ROLE = "josdk-crd-validating-cluster-role";
    private final Map<String, ResourceInfo> controllerToCustomResourceMappings;
    private final boolean validateCRDs;

    public AddClusterRolesDecorator(Map<String, ResourceInfo> controllerToCustomResourceMappings, boolean validateCRDs) {
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
            final var crName = JOSDK_CRD_VALIDATING_CLUSTER_ROLE;

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
