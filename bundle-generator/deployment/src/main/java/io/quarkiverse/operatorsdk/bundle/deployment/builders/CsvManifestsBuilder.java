package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import static io.quarkiverse.operatorsdk.bundle.deployment.BundleGenerator.MANIFESTS;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.*;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder.RequiredCRD;
import io.quarkiverse.operatorsdk.common.ReconciledAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ResourceAssociatedAugmentedClassInfo;

public class CsvManifestsBuilder extends ManifestsBuilder {

    private static final Logger log = Logger.getLogger(CsvManifestsBuilder.class);

    private static final String DEFAULT_INSTALL_MODE = "AllNamespaces";
    private static final String DEPLOYMENT = "deployment";
    private static final String SERVICE_ACCOUNT_KIND = "ServiceAccount";
    private static final String CLUSTER_ROLE_KIND = "ClusterRole";
    private static final String ROLE_KIND = "Role";
    private static final String NO_SERVICE_ACCOUNT = "";
    private static final Logger LOGGER = Logger.getLogger(CsvManifestsBuilder.class.getName());

    private static final String IMAGE_PNG = "image/png";

    private ClusterServiceVersionBuilder csvBuilder;
    private final SortedSet<String> ownedCRs = new TreeSet<>();
    private final SortedSet<String> requiredCRs = new TreeSet<>();
    private final Path kubernetesResources;

    public CsvManifestsBuilder(CSVMetadataHolder metadata, List<ReconcilerAugmentedClassInfo> controllers,
            Path mainSourcesRoot) {
        super(metadata);
        this.kubernetesResources = mainSourcesRoot != null ? mainSourcesRoot.resolve("kubernetes") : null;

        csvBuilder = new ClusterServiceVersionBuilder();

        final var metadataBuilder = csvBuilder.withNewMetadata().withName(getName());
        if (metadata.skipRange != null) {
            metadataBuilder.addToAnnotations("olm.skipRange", metadata.skipRange);
        }
        csvBuilder = metadataBuilder.endMetadata();

        final var csvSpecBuilder = csvBuilder
                .editOrNewSpec()
                .withDescription(metadata.description)
                .withDisplayName(defaultIfEmpty(metadata.displayName, getName()))
                .withKeywords(metadata.keywords)
                .withReplaces(metadata.replaces)
                .withVersion(metadata.version)
                .withMinKubeVersion(metadata.minKubeVersion)
                .withMaturity(metadata.maturity);

        if (metadata.providerName != null) {
            csvSpecBuilder.withNewProvider()
                    .withName(metadata.providerName)
                    .withUrl(metadata.providerURL)
                    .endProvider();
        }

        if (metadata.annotations != null) {
            csvSpecBuilder.addToAnnotations("containerImage", metadata.annotations.containerImage);
            csvSpecBuilder.addToAnnotations("repository", metadata.annotations.repository);
            csvSpecBuilder.addToAnnotations("capabilities", metadata.annotations.capabilities);
            csvSpecBuilder.addToAnnotations("categories", metadata.annotations.categories);
            csvSpecBuilder.addToAnnotations("certified", String.valueOf(metadata.annotations.certified));
            csvSpecBuilder.addToAnnotations("alm-examples", metadata.annotations.almExamples);
        }

        if (metadata.maintainers != null) {
            for (CSVMetadataHolder.Maintainer maintainer : metadata.maintainers) {
                csvSpecBuilder.addNewMaintainer(maintainer.email, maintainer.name);
            }
        }

        if (metadata.links != null) {
            for (CSVMetadataHolder.Link link : metadata.links) {
                csvSpecBuilder.addNewLink(link.name, link.url);
            }
        }

        final var defaultIconName = getIconName();
        // check if user has auto-detected icon
        final var defaultIcon = readIconAsBase64(defaultIconName);
        if (defaultIcon != null) {
            csvSpecBuilder.addNewIcon(defaultIcon, IMAGE_PNG);
        }

        if (metadata.icon != null) {
            // deal with explicit icons
            for (CSVMetadataHolder.Icon icon : metadata.icon) {
                if (!icon.fileName.isBlank() && !defaultIconName.equals(icon.fileName)) {
                    String iconAsBase64 = readIconAsBase64(icon.fileName);
                    if (iconAsBase64 != null) {
                        csvSpecBuilder.addNewIcon()
                                .withBase64data(iconAsBase64)
                                .withMediatype(icon.mediatype)
                                .endIcon();
                    } else {
                        throw new IllegalArgumentException(
                                "Couldn't find '" + icon.fileName + "' in " + kubernetesResources);
                    }
                }
            }
        } else {
            // legacy icon support
            try (var iconAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(defaultIconName)) {
                if (iconAsStream != null) {
                    log.warnv(
                            "Using icon found in the application's resource. It is now recommended to put icons in 'src/main/kubernetes' instead of resources and provide an explicit name / media type using the @CSVMetadata.Icon annotation. This avoids unduly bundling unneeded resources into the application.");
                    final byte[] iconAsBase64 = Base64.getEncoder().encode(iconAsStream.readAllBytes());
                    csvSpecBuilder.addNewIcon(new String(iconAsBase64), IMAGE_PNG);
                }
            } catch (IOException e) {
                // ignore
            }
        }

        if (metadata.installModes == null || metadata.installModes.length == 0) {
            csvSpecBuilder.addNewInstallMode(true, DEFAULT_INSTALL_MODE);
        } else {
            for (CSVMetadataHolder.InstallMode installMode : metadata.installModes) {
                csvSpecBuilder.addNewInstallMode(installMode.supported, installMode.type);
            }
        }

        // add owned and required CRD, also collect them
        final var crdsBuilder = csvSpecBuilder.editOrNewCustomresourcedefinitions();
        controllers.forEach(raci -> {
            // add owned CRD
            final var resourceInfo = raci.associatedResourceInfo();
            if (resourceInfo.isCR()) {
                final var asResource = resourceInfo.asResourceTargeting();
                final var fullResourceName = asResource.fullResourceName();
                ownedCRs.add(fullResourceName);
                crdsBuilder
                        .addNewOwned()
                        .withName(fullResourceName)
                        .withVersion(asResource.version())
                        .withKind(asResource.kind())
                        .endOwned();
            }

            // add required CRD for each dependent that targets a CR
            final var dependents = raci.getDependentResourceInfos();
            if (dependents != null && !dependents.isEmpty()) {
                dependents.stream()
                        .map(ResourceAssociatedAugmentedClassInfo::associatedResourceInfo)
                        .filter(ReconciledAugmentedClassInfo::isCR)
                        .map(ReconciledAugmentedClassInfo::asResourceTargeting)
                        .forEach(secondaryResource -> {
                            final var fullResourceName = secondaryResource.fullResourceName();
                            requiredCRs.add(fullResourceName);
                            crdsBuilder.addNewRequired()
                                    .withName(fullResourceName)
                                    .withVersion(secondaryResource.version())
                                    .withKind(secondaryResource.kind())
                                    .endRequired();
                        });
            }

            // add required CRDs from CSV metadata
            if (metadata.requiredCRDs != null) {
                for (RequiredCRD requiredCRD : metadata.requiredCRDs) {
                    requiredCRs.add(requiredCRD.name);
                    crdsBuilder.addNewRequired()
                            .withKind(requiredCRD.kind)
                            .withName(requiredCRD.name)
                            .withVersion(requiredCRD.version)
                            .endRequired();
                }
            }

        });
        crdsBuilder.endCustomresourcedefinitions().endSpec();
    }

    public Set<String> getOwnedCRs() {
        return Collections.unmodifiableSet(ownedCRs);
    }

    public Set<String> getRequiredCRs() {
        return Collections.unmodifiableSet(requiredCRs);
    }

    @Override
    public String getManifestType() {
        return "CSV";
    }

    public Path getFileName() {
        return Path.of(MANIFESTS, getName() + ".clusterserviceversion.yaml");
    }

    private String getIconName() {
        return getName() + ".icon.png";
    }

    private String readIconAsBase64(String fileName) {
        if (kubernetesResources != null) {
            try (var iconAsStream = new FileInputStream(kubernetesResources.resolve(fileName).toFile())) {
                final byte[] iconAsBase64 = Base64.getEncoder().encode(iconAsStream.readAllBytes());
                return new String(iconAsBase64);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    public byte[] getManifestData(List<ServiceAccount> serviceAccounts, List<ClusterRoleBinding> clusterRoleBindings,
            List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            List<Deployment> deployments) throws IOException {
        final var csvSpecBuilder = csvBuilder.editOrNewSpec();

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
            Predicate<StrategyDeploymentPermissionsBuilder> sameServiceAccountName = p -> serviceAccountName
                    .equals(p.getServiceAccountName());
            if (installSpec.hasMatchingPermission(sameServiceAccountName)) {
                var permission = installSpec.editMatchingPermission(sameServiceAccountName);
                appendRulesInPermission(permission, rules);
                permission.endPermission();
            } else {
                installSpec.addNewPermission()
                        .withServiceAccountName(serviceAccountName)
                        .addAllToRules(rules)
                        .endPermission();
            }
        }
    }

    private void handleClusterPermission(List<PolicyRule> rules,
            String serviceAccountName,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec) {

        Predicate<StrategyDeploymentPermissionsBuilder> sameServiceAccountName = p -> serviceAccountName
                .equals(p.getServiceAccountName());
        if (installSpec.hasMatchingClusterPermission(sameServiceAccountName)) {
            var permission = installSpec.editMatchingClusterPermission(sameServiceAccountName);
            appendRulesInPermission(permission, rules);
            permission.endClusterPermission();
        } else {
            installSpec.addNewClusterPermission()
                    .withServiceAccountName(serviceAccountName)
                    .addAllToRules(rules)
                    .endClusterPermission();
        }
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void appendRulesInPermission(StrategyDeploymentPermissionsFluent permission, List<PolicyRule> rules) {
        for (PolicyRule rule : rules) {
            if (!permission.hasMatchingRule(r -> r.equals(rule))) {
                permission.addToRules(rule);
            }
        }
    }

    private static String defaultIfEmpty(String possiblyNullOrEmpty, String defaultValue) {
        return Optional.ofNullable(possiblyNullOrEmpty).filter(s -> !s.isBlank() && !s.isEmpty()).orElse(defaultValue);
    }
}
