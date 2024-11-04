package io.quarkiverse.operatorsdk.bundle.deployment.builders;

import static io.quarkiverse.operatorsdk.bundle.deployment.BundleGenerator.MANIFESTS;
import static io.quarkiverse.operatorsdk.bundle.deployment.BundleProcessor.CRD_DESCRIPTION;
import static io.quarkiverse.operatorsdk.bundle.deployment.BundleProcessor.CRD_DISPLAY_NAME;
import static io.quarkiverse.operatorsdk.bundle.runtime.BundleConfiguration.*;
import static java.util.Comparator.comparing;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.fabric8.kubernetes.api.model.GroupVersionKind;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleRef;
import io.fabric8.kubernetes.api.model.rbac.Subject;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CRDDescription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CRDDescriptionBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.NamedInstallStrategyFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.StrategyDeploymentPermissionsBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.StrategyDeploymentPermissionsFluent;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadataHolder.RequiredCRD;
import io.quarkiverse.operatorsdk.common.ConfigurationUtils;
import io.quarkiverse.operatorsdk.common.ReconciledAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconciledResourceAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ReconcilerAugmentedClassInfo;
import io.quarkiverse.operatorsdk.common.ResourceAssociatedAugmentedClassInfo;
import io.quarkiverse.operatorsdk.runtime.BuildTimeOperatorConfiguration;

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
    public static final String OLM_TARGET_NAMESPACES = "metadata.annotations['olm.targetNamespaces']";
    private static final Comparator<String> nullsFirst = Comparator.nullsFirst(String::compareTo);
    private static final Comparator<GroupVersionKind> gvkComparator = comparing(GroupVersionKind::getGroup, nullsFirst)
            .thenComparing(GroupVersionKind::getKind, nullsFirst)
            .thenComparing(GroupVersionKind::getVersion, nullsFirst);
    private static final Comparator<CRDDescription> crdDescriptionComparator = comparing(CRDDescription::getName, nullsFirst);
    private ClusterServiceVersionBuilder csvBuilder;
    private final Set<CRDDescription> ownedCRs = new HashSet<>();
    private final Set<CRDDescription> requiredCRs = new HashSet<>();
    private final Path kubernetesResources;
    private final String deploymentName;
    private final List<ReconcilerAugmentedClassInfo> controllers;

    public CsvManifestsBuilder(CSVMetadataHolder metadata, BuildTimeOperatorConfiguration operatorConfiguration,
            List<ReconcilerAugmentedClassInfo> controllers,
            Path mainSourcesRoot, String deploymentName) {
        super(metadata);
        this.deploymentName = deploymentName;
        this.controllers = controllers;
        this.kubernetesResources = mainSourcesRoot != null ? mainSourcesRoot.resolve("kubernetes") : null;

        csvBuilder = new ClusterServiceVersionBuilder();

        final var metadataBuilder = csvBuilder.withNewMetadata().withName(metadata.csvName);
        if (metadata.annotations != null) {
            metadataBuilder.addToAnnotations(OLM_SKIP_RANGE_ANNOTATION, metadata.annotations.skipRange);
            metadataBuilder.addToAnnotations(CONTAINER_IMAGE_ANNOTATION, metadata.annotations.containerImage);
            metadataBuilder.addToAnnotations(REPOSITORY_ANNOTATION, metadata.annotations.repository);
            metadataBuilder.addToAnnotations(CAPABILITIES_ANNOTATION, metadata.annotations.capabilities);
            metadataBuilder.addToAnnotations(CATEGORIES_ANNOTATION, metadata.annotations.categories);
            metadataBuilder.addToAnnotations(CERTIFIED_ANNOTATION, String.valueOf(metadata.annotations.certified));
            metadataBuilder.addToAnnotations(ALM_EXAMPLES_ANNOTATION, metadata.annotations.almExamples);
            if (metadata.annotations.others != null) {
                metadata.annotations.others.forEach(metadataBuilder::addToAnnotations);
            }
        }
        csvBuilder = metadataBuilder.endMetadata();

        final var csvSpecBuilder = csvBuilder
                .editOrNewSpec()
                .withDescription(metadata.description)
                .withDisplayName(defaultIfEmpty(metadata.displayName, metadata.csvName))
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
        final var nativeApis = new ArrayList<GroupVersionKind>();
        controllers.forEach(raci -> {
            // deal with primary resource
            final var resourceInfo = raci.associatedResourceInfo();
            if (resourceInfo.isCR()) {
                final var asResource = resourceInfo.asResourceTargeting();
                // if the primary is not a CR, mark it as native API
                if (asResource.isCR()) {
                    // check if the primary resource is unowned, in which case, make it required, otherwise, it's owned
                    final var crdDescription = createCRDDescription(asResource);
                    if (operatorConfiguration.isControllerOwningPrimary(raci.nameOrFailIfUnset())) {
                        ownedCRs.add(crdDescription);
                    } else {
                        requiredCRs.add(crdDescription);
                    }
                } else {
                    nativeApis.add(new GroupVersionKind(asResource.group(), asResource.kind(),
                            asResource.version()));
                }
            }

            // add required CRD for each dependent that targets a CR
            final var dependents = raci.getDependentResourceInfos();
            if (dependents != null && !dependents.isEmpty()) {
                dependents.stream()
                        .map(ResourceAssociatedAugmentedClassInfo::associatedResourceInfo)
                        .filter(ReconciledAugmentedClassInfo::isResource)
                        .map(ReconciledAugmentedClassInfo::asResourceTargeting)
                        .forEach(secondaryResource -> {
                            if (secondaryResource.isCR()) {
                                requiredCRs.add(createCRDDescription(secondaryResource));
                            } else {
                                nativeApis.add(new GroupVersionKind(secondaryResource.group(), secondaryResource.kind(),
                                        secondaryResource.version()));
                            }
                        });
            }
        });

        // add required CRDs from CSV metadata
        if (metadata.requiredCRDs != null) {
            for (RequiredCRD requiredCRD : metadata.requiredCRDs) {
                requiredCRs.add(new CRDDescriptionBuilder()
                        .withKind(requiredCRD.kind)
                        .withName(requiredCRD.name)
                        .withVersion(requiredCRD.version)
                        .build());
            }
        }

        // add sorted native APIs
        csvSpecBuilder.addAllToNativeAPIs(nativeApis.stream()
                .distinct()
                .sorted(gvkComparator)
                .toList());

        csvSpecBuilder.editOrNewCustomresourcedefinitions()
                .addAllToOwned(ownedCRs.stream().sorted(crdDescriptionComparator).toList())
                .addAllToRequired(requiredCRs.stream().sorted(crdDescriptionComparator).toList())
                .endCustomresourcedefinitions()
                .endSpec();
    }

    private CRDDescription createCRDDescription(ReconciledResourceAugmentedClassInfo<?> secondaryResource) {
        final var fullResourceName = secondaryResource.fullResourceName();
        return new CRDDescriptionBuilder()
                .withName(fullResourceName)
                .withDisplayName(secondaryResource.getExtendedInfo(CRD_DISPLAY_NAME, String.class))
                .withDescription(secondaryResource.getExtendedInfo(CRD_DESCRIPTION, String.class))
                .withVersion(secondaryResource.version())
                .withKind(secondaryResource.kind())
                .build();
    }

    public Set<String> getOwnedCRs() {
        return ownedCRs.stream()
                .map(CRDDescription::getName)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> getRequiredCRs() {
        return requiredCRs.stream()
                .map(CRDDescription::getName)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private void handleDeployments(List<Deployment> deployments, NamedInstallStrategyFluent<?>.SpecNested<?> installSpec) {
        deployments.forEach(deployment -> handleDeployment(deployment, installSpec));
    }

    private void handlePermissions(List<ClusterRole> clusterRoles, List<RoleBinding> roleBindings, List<Role> roles,
            String defaultServiceAccountName,
            NamedInstallStrategyFluent<?>.SpecNested<?> installSpec) {
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
            NamedInstallStrategyFluent<?>.SpecNested<?> installSpec) {
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

    private void handleDeployment(Deployment deployment, NamedInstallStrategyFluent<?>.SpecNested<?> installSpec) {
        if (deployment != null) {
            final var deploymentName = deployment.getMetadata().getName();
            var deploymentSpec = deployment.getSpec();

            // if we're dealing with the operator's deployment, modify it to add the namespaces env variables
            if (deploymentName.equals(this.deploymentName)) {
                final var containerBuilder = new DeploymentSpecBuilder(deploymentSpec)
                        .editTemplate()
                        .editSpec()
                        .editFirstContainer();
                controllers.stream()
                        .map(ResourceAssociatedAugmentedClassInfo::nameOrFailIfUnset)
                        .forEach(reconcilerName -> {
                            final var envVarName = ConfigurationUtils.getNamespacesPropertyName(reconcilerName, true);
                            containerBuilder
                                    .addNewEnv()
                                    .withName(envVarName)
                                    .withNewValueFrom()
                                    .withNewFieldRef()
                                    .withFieldPath(OLM_TARGET_NAMESPACES)
                                    .endFieldRef()
                                    .endValueFrom()
                                    .endEnv();
                        });
                deploymentSpec = containerBuilder
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .build();
            }
            installSpec.addNewDeployment()
                    .withName(deploymentName)
                    .withSpec(deploymentSpec)
                    .endDeployment();
        }
    }

    private void handlerPermission(List<PolicyRule> rules, String serviceAccountName,
            NamedInstallStrategyFluent<?>.SpecNested<?> installSpec) {
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
            NamedInstallStrategyFluent<?>.SpecNested<?> installSpec) {

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
