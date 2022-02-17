package io.quarkiverse.operatorsdk.csv.deployment.builders;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.ALL_VERBS;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionSpecFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.NamedInstallStrategyFluent;
import io.quarkiverse.operatorsdk.common.ResourceInfo;
import io.quarkiverse.operatorsdk.csv.deployment.AugmentedResourceInfo;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;

public class CsvManifestsBuilder extends ManifestsBuilder {

    private static final String DEFAULT_INSTALL_MODE = "AllNamespaces";
    private static final String DEPLOYMENT = "deployment";
    private static final String SERVICE_ACCOUNT_KIND = "ServiceAccount";
    private static final String CLUSTER_ROLE_KIND = "ClusterRole";
    private static final String ROLE_KIND = "Role";
    private static final Map<String, Set<ResourceInfo>> groupToCRInfo = new ConcurrentHashMap<>(7);

    private final String csvGroupName;
    private final ClusterServiceVersionBuilder csvBuilder;
    private final CSVMetadataHolder metadata;

    public CsvManifestsBuilder(AugmentedResourceInfo cri, Map<String, CSVMetadataHolder> csvMetadata) {
        super(cri);
        // record group to CRI mapping
        groupToCRInfo.computeIfAbsent(cri.getGroup(), s -> new HashSet<>()).add(cri);

        csvGroupName = cri.getCsvGroupName();
        metadata = csvMetadata.get(csvGroupName);
        csvBuilder = new ClusterServiceVersionBuilder()
                .withNewMetadata().withName(csvGroupName).endMetadata();
        final var csvSpecBuilder = csvBuilder
                .editOrNewSpec()
                .withDescription(metadata.description)
                .withDisplayName(StringUtils.defaultIfEmpty(metadata.displayName, getControllerName()))
                .withKeywords(metadata.keywords)
                .withReplaces(metadata.replaces)
                .withVersion(metadata.version)
                .withMaturity(metadata.maturity);

        if (metadata.providerName != null) {
            csvSpecBuilder.withNewProvider()
                    .withName(metadata.providerName)
                    .withUrl(metadata.providerURL)
                    .endProvider();
        }

        if (metadata.maintainers != null && metadata.maintainers.length > 0) {
            for (CSVMetadataHolder.Maintainer maintainer : metadata.maintainers) {
                csvSpecBuilder.addNewMaintainer(maintainer.email, maintainer.name);
            }
        }

        if (metadata.installModes == null || metadata.maintainers.length == 0) {
            csvSpecBuilder.addNewInstallMode(true, DEFAULT_INSTALL_MODE);
        } else {
            for (CSVMetadataHolder.InstallMode installMode : metadata.installModes) {
                csvSpecBuilder.addNewInstallMode(installMode.supported, installMode.type);
            }
        }

        csvSpecBuilder
                .editOrNewCustomresourcedefinitions()
                .addNewOwned()
                .withName(cri.getResourceFullName())
                .withVersion(cri.getVersion())
                .withKind(cri.getKind())
                .endOwned().endCustomresourcedefinitions()
                .endSpec();
    }

    public String getFileName() {
        return csvGroupName + ".csv.yml";
    }

    public byte[] getYAMLData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            List<Deployment> deployments) throws IOException {
        final var csvSpecBuilder = csvBuilder
                .editOrNewSpec();

        // deal with icon
        try (var iconAsStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(getIconName())) {
            if (iconAsStream != null) {
                final byte[] iconAsBase64 = Base64.getEncoder()
                        .encode(iconAsStream.readAllBytes());
                csvSpecBuilder.addNewIcon()
                        .withBase64data(new String(iconAsBase64))
                        .withMediatype("image/png")
                        .endIcon();
            }
        } catch (IOException e) {
            // ignore
        }

        String defaultServiceAccountName = serviceAccounts.get(0).getMetadata().getName();
        final var installSpec = csvSpecBuilder.editOrNewInstall()
                .withStrategy(DEPLOYMENT).editOrNewSpec();
        handleClusterPermissions(clusterRoleBindings, clusterRoles, roles, defaultServiceAccountName, installSpec);
        handlePermissions(clusterRoles, roleBindings, roles, defaultServiceAccountName, installSpec);
        handleDeployments(deployments, installSpec);

        // do not forget to end the elements!!
        installSpec.endSpec().endInstall();
        csvSpecBuilder.endSpec();

        final var csv = csvBuilder.build();
        return YAML_MAPPER.writeValueAsBytes(csv);
    }

    private String getIconName() {
        return csvGroupName + ".icon.png";
    }

    private void handleDeployments(List<Deployment> deployments,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        deployments.forEach(deployment -> handleDeployment(deployment, installSpec));
    }

    private void handlePermissions(List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            String defaultServiceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        Map<String, List<PolicyRule>> customPermissionRules = new HashMap<>();
        System.out.println("Custom permission rules: " + metadata.permissionRules);
        if (metadata.permissionRules != null) {
            for (CSVMetadataHolder.PermissionRule permissionRule : metadata.permissionRules) {
                String serviceAccountName = StringUtils.defaultIfEmpty(permissionRule.serviceAccountName,
                        defaultServiceAccountName);
                List<PolicyRule> customRulesByServiceAccount = customPermissionRules.get(serviceAccountName);
                if (customRulesByServiceAccount == null) {
                    customRulesByServiceAccount = new LinkedList<>();
                    customPermissionRules.put(serviceAccountName, customRulesByServiceAccount);
                }

                List<String> verbs;
                if (permissionRule.verbs != null && permissionRule.verbs.length > 0) {
                    verbs = Arrays.asList(permissionRule.verbs);
                } else {
                    verbs = ALL_VERBS;
                }

                customRulesByServiceAccount.add(new PolicyRuleBuilder()
                        .addAllToApiGroups(Arrays.asList(permissionRule.apiGroups))
                        .addAllToResources(Arrays.asList(permissionRule.resources))
                        .addAllToVerbs(verbs)
                        .build());
            }
        }

        System.out.println("Custom permission rules: " + customPermissionRules.size());

        for (RoleBinding binding : roleBindings) {
            String serviceAccountName = findServiceAccountFromSubjects(binding.getSubjects(), defaultServiceAccountName);
            List<PolicyRule> rules = new LinkedList<>();
            rules.addAll(findRules(binding.getRoleRef(), clusterRoles, roles));
            Optional.ofNullable(customPermissionRules.remove(serviceAccountName)).ifPresent(rules::addAll);

            handlerPermission(rules, serviceAccountName, installSpec);
        }
    }

    private void handleClusterPermissions(List<ClusterRoleBinding> clusterRoleBindings, List<ClusterRole> clusterRoles,
            List<Role> roles,
            String defaultServiceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        for (ClusterRoleBinding binding : clusterRoleBindings) {
            handleClusterPermission(findRules(binding.getRoleRef(), clusterRoles, roles),
                    findServiceAccountFromSubjects(binding.getSubjects(), defaultServiceAccountName),
                    installSpec);
        }
    }

    private void handleDeployment(Deployment deployment,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        if (deployment != null) {
            installSpec.addNewDeployment()
                    .withName(deployment.getMetadata().getName())
                    .withSpec(deployment.getSpec())
                    .endDeployment();
        }
    }

    private void handlerPermission(List<PolicyRule> rules,
            String serviceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        if (!rules.isEmpty()) {
            installSpec
                    .addNewPermission()
                    .withServiceAccountName(serviceAccountName)
                    .addAllToRules(rules)
                    .endPermission();
        }
    }

    private void handleClusterPermission(List<PolicyRule> rules,
            String serviceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        // check if we have our CR group in the cluster role fragment and remove the one we added
        // before since we presume that if the user defined a fragment for permissions associated with their
        // CR they want that fragment to take precedence over automatically generated code
        final var clusterPermissions = installSpec.buildClusterPermissions();
        groupToCRInfo.forEach((group, infos) -> {

            final Predicate<PolicyRule> hasGroup = pr -> pr.getApiGroups()
                    .contains(group);
            final var nowEmptyPermissions = new LinkedList<Integer>();
            final var permissionPosition = new Integer[] { 0 };
            if (rules.stream().anyMatch(hasGroup)) {
                clusterPermissions.forEach(p -> {
                    // record the position of all rules that match the group
                    Integer[] index = new Integer[] { 0 };
                    List<Integer> matchingRuleIndices = new LinkedList<>();
                    p.getRules().forEach(r -> {
                        if (hasGroup.test(r)) {
                            matchingRuleIndices.add(index[0]);
                        }
                        index[0]++;
                    });

                    // remove the group from all matching rules
                    matchingRuleIndices.forEach(i -> {
                        final var groups = p.getRules().get(i).getApiGroups();
                        groups.remove(group);
                        // if the rule doesn't have any groups anymore, remove it
                        if (groups.isEmpty()) {
                            p.getRules().remove(i.intValue());
                            // if the permission doesn't have any rules anymore, mark it for removal
                            // or the service account name is empty
                            if (p.getRules().isEmpty() || StringUtils.isEmpty(p.getServiceAccountName())) {
                                nowEmptyPermissions.add(permissionPosition[0]);
                            }
                        }
                    });

                    permissionPosition[0]++;
                });

                // remove now empty permissions
                nowEmptyPermissions.forEach(i -> clusterPermissions.remove(i.intValue()));
                installSpec.addAllToClusterPermissions(clusterPermissions);
            }
        });

        installSpec
                .addNewClusterPermission()
                .withServiceAccountName(serviceAccountName)
                .addAllToRules(rules)
                .endClusterPermission();
    }

    private String findServiceAccountFromSubjects(List<Subject> subjects, String defaultServiceAccountName) {
        return subjects.stream()
                .filter(o -> SERVICE_ACCOUNT_KIND.equalsIgnoreCase(o.getKind()))
                .map(o -> o.getName())
                .findFirst()
                .orElse(defaultServiceAccountName);
    }

    private List<PolicyRule> findRules(RoleRef roleRef, List<ClusterRole> clusterRoles,
            List<Role> roles) {
        if (roleRef == null) {
            return Collections.emptyList();
        }

        String roleRefKind = roleRef.getKind();
        String roleRefName = roleRef.getName();
        if (CLUSTER_ROLE_KIND.equals(roleRefKind)) {
            for (ClusterRole role : clusterRoles) {
                if (roleRefName.equals(role.getMetadata().getName())) {
                    return role.getRules();
                }
            }
        } else if (ROLE_KIND.equals(roleRefKind)) {
            for (Role role : roles) {
                if (roleRefName.equals(role.getMetadata().getName())) {
                    return role.getRules();
                }
            }
        }

        return Collections.emptyList();
    }
}
