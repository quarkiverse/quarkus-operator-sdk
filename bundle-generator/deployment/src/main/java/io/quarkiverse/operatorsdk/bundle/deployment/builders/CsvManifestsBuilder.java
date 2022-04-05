package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import static io.quarkiverse.operatorsdk.bundle.deployment.BundleGenerator.MANIFESTS;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

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
import io.quarkiverse.operatorsdk.bundle.deployment.AugmentedResourceInfo;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.ResourceInfo;

public class CsvManifestsBuilder extends ManifestsBuilder {

    private static final String DEFAULT_INSTALL_MODE = "AllNamespaces";
    private static final String DEPLOYMENT = "deployment";
    private static final String SERVICE_ACCOUNT_KIND = "ServiceAccount";
    private static final String CLUSTER_ROLE_KIND = "ClusterRole";
    private static final String ROLE_KIND = "Role";
    private static final String NO_SERVICE_ACCOUNT = "";
    private static final Logger LOGGER = Logger.getLogger(CsvManifestsBuilder.class.getName());

    private static final Map<String, Set<ResourceInfo>> groupToCRInfo = new ConcurrentHashMap<>(7);

    private final ClusterServiceVersionBuilder csvBuilder;

    public CsvManifestsBuilder(CSVMetadataHolder metadata, List<AugmentedResourceInfo> controllers) {
        super(metadata);
        // record group to CRI mapping
        // groupToCRInfo.computeIfAbsent(metadata.getGroup(), s -> new HashSet<>()).add(cri);
        csvBuilder = new ClusterServiceVersionBuilder()
                .withNewMetadata().withName(getName()).endMetadata();
        final var csvSpecBuilder = csvBuilder
                .editOrNewSpec()
                .withDescription(metadata.description)
                .withDisplayName(defaultIfEmpty(metadata.displayName, getName()))
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

        if (metadata.installModes == null || metadata.installModes.length == 0) {
            csvSpecBuilder.addNewInstallMode(true, DEFAULT_INSTALL_MODE);
        } else {
            for (CSVMetadataHolder.InstallMode installMode : metadata.installModes) {
                csvSpecBuilder.addNewInstallMode(installMode.supported, installMode.type);
            }
        }

        for (AugmentedResourceInfo controller : controllers) {
            csvSpecBuilder
                    .editOrNewCustomresourcedefinitions()
                    .addNewOwned()
                    .withName(controller.getResourceFullName())
                    .withVersion(controller.getVersion())
                    .withKind(controller.getKind())
                    .endOwned().endCustomresourcedefinitions()
                    .endSpec();
        }
    }

    @Override
    public String getManifestType() {
        return "CSV";
    }

    public Path getFileName() {
        return Path.of(MANIFESTS, getName() + ".csv.yml");
    }

    public byte[] getManifestData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
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

        String defaultServiceAccountName = NO_SERVICE_ACCOUNT;
        if (!serviceAccounts.isEmpty()) {
            defaultServiceAccountName = serviceAccounts.get(0).getMetadata().getName();
        }

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
        return getName() + ".icon.png";
    }

    private void handleDeployments(List<Deployment> deployments,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        deployments.forEach(deployment -> handleDeployment(deployment, installSpec));
    }

    private void handlePermissions(List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            String defaultServiceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        Map<String, List<PolicyRule>> customPermissionRules = new HashMap<>();
        if (metadata.permissionRules != null) {
            for (CSVMetadataHolder.PermissionRule permissionRule : metadata.permissionRules) {
                String serviceAccountName = defaultIfEmpty(permissionRule.serviceAccountName, defaultServiceAccountName);
                List<PolicyRule> customRulesByServiceAccount = customPermissionRules.computeIfAbsent(serviceAccountName,
                        k -> new LinkedList<>());

                customRulesByServiceAccount.add(new PolicyRuleBuilder()
                        .addAllToApiGroups(Arrays.asList(permissionRule.apiGroups))
                        .addAllToResources(Arrays.asList(permissionRule.resources))
                        .addAllToVerbs(Arrays.asList(permissionRule.verbs))
                        .build());
            }
        }

        for (RoleBinding binding : roleBindings) {
            String serviceAccountName = findServiceAccountFromSubjects(binding.getSubjects(), defaultServiceAccountName);
            if (NO_SERVICE_ACCOUNT.equals(serviceAccountName)) {
                LOGGER.warnf("Role '%s' was not added because the service account is missing", binding.getRoleRef().getName());
                continue;
            }

            List<PolicyRule> rules = new LinkedList<>(findRules(binding.getRoleRef(), clusterRoles, roles));
            Optional.ofNullable(customPermissionRules.remove(serviceAccountName)).ifPresent(rules::addAll);

            handlerPermission(rules, serviceAccountName, installSpec);
        }
    }

    private void handleClusterPermissions(List<ClusterRoleBinding> clusterRoleBindings, List<ClusterRole> clusterRoles,
            List<Role> roles,
            String defaultServiceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
        for (ClusterRoleBinding binding : clusterRoleBindings) {
            String serviceAccountName = findServiceAccountFromSubjects(binding.getSubjects(), defaultServiceAccountName);
            if (NO_SERVICE_ACCOUNT.equals(serviceAccountName)) {
                LOGGER.warnf("Cluster Role '%s' was not added because the service account is missing",
                        binding.getRoleRef().getName());
                continue;
            }

            handleClusterPermission(findRules(binding.getRoleRef(), clusterRoles, roles), serviceAccountName, installSpec);
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
                            final var san = p.getServiceAccountName();
                            if (p.getRules().isEmpty() || (san == null || san.isBlank())) {
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
                .map(Subject::getName)
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

    private static String defaultIfEmpty(String possiblyNullOrEmpty, String defaultValue) {
        return Optional.ofNullable(possiblyNullOrEmpty).filter(String::isBlank).orElse(defaultValue);
    }
}
