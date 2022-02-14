package io.quarkiverse.operatorsdk.csv.deployment;

import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionFluent.SpecNested;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionSpecFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionSpecFluent.InstallNested;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.NamedInstallStrategyFluent;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.ResourceInfo;

public class CSVGenerator {

    private static final ObjectMapper YAML_MAPPER;

    private CSVGenerator() {
    }

    static {
        YAML_MAPPER = new ObjectMapper((new YAMLFactory()).enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        YAML_MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        YAML_MAPPER.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        YAML_MAPPER.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
    }

    public static Set<NamedCSVBuilder> prepareGeneration(Map<String, AugmentedResourceInfo> info,
            Map<String, CSVMetadataHolder> csvMetadata) {
        final var csvBuilders = new ConcurrentHashMap<String, NamedCSVBuilder>(7);
        return info.values().parallelStream()
                .map(cri -> csvBuilders.computeIfAbsent(cri.getCsvGroupName(),
                        s -> new NamedCSVBuilder(cri, csvMetadata)))
                .collect(Collectors.toSet());
    }

    static class NamedCSVBuilder {
        private final String csvGroupName;
        private final String controllerName;
        private final ClusterServiceVersionBuilder csvBuilder;
        static final Map<String, Set<ResourceInfo>> groupToCRInfo = new ConcurrentHashMap<>(7);

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            NamedCSVBuilder that = (NamedCSVBuilder) o;

            return csvGroupName.equals(that.csvGroupName);
        }

        @Override
        public int hashCode() {
            return csvGroupName.hashCode();
        }

        public NamedCSVBuilder(AugmentedResourceInfo cri, Map<String, CSVMetadataHolder> csvMetadata) {
            // record group to CRI mapping
            groupToCRInfo.computeIfAbsent(cri.getGroup(), s -> new HashSet<>()).add(cri);

            controllerName = cri.getControllerName();
            csvGroupName = cri.getCsvGroupName();
            final var metadata = csvMetadata.get(csvGroupName);
            csvBuilder = new ClusterServiceVersionBuilder()
                    .withNewMetadata().withName(csvGroupName).endMetadata();
            final var csvSpecBuilder = csvBuilder
                    .editOrNewSpec()
                    .withDescription(metadata.description)
                    .withDisplayName(metadata.displayName)
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

            csvSpecBuilder
                    .editOrNewCustomresourcedefinitions()
                    .addNewOwned()
                    .withName(cri.getResourceFullName())
                    .withVersion(cri.getVersion())
                    .withKind(cri.getKind())
                    .endOwned().endCustomresourcedefinitions();

            // make sure that we add permissions for our own CR
            final var installSpec = csvSpecBuilder.editOrNewInstall().editOrNewSpec();
            final Integer[] ruleIndex = new Integer[1];
            final Integer[] clusterPermissionIndex = new Integer[] { 0 };
            final Boolean hasMatchingClusterPermission = hasMatchingClusterPermission(
                    cri,
                    installSpec, ruleIndex,
                    clusterPermissionIndex);
            final var clusterPermission = Boolean.TRUE.equals(hasMatchingClusterPermission)
                    ? installSpec.editClusterPermission(clusterPermissionIndex[0])
                    : installSpec.addNewClusterPermission();

            // if we found a matching rule, so retrieve it and add our resource, otherwise create a new rule
            final PolicyRuleBuilder rule = ruleIndex[0] != null
                    ? new PolicyRuleBuilder(clusterPermission.getRule(ruleIndex[0]))
                    : new PolicyRuleBuilder();
            final var plural = cri.getPlural();
            rule.addNewResource(plural);

            // if the resource has a non-Void status, also add the status resource
            cri.getStatusClassName().ifPresent(statusClass -> {
                if (!"java.lang.Void".equals(statusClass)) {
                    rule.addNewResource(plural + "/status");
                }
            });
            if (ruleIndex[0] != null) {
                clusterPermission.setToRules(ruleIndex[0], rule.build());
            } else {
                clusterPermission.addToRules(rule.addNewApiGroup(cri.getGroup())
                        .addToVerbs("get", "list", "watch", "create", "delete", "patch",
                                "update")
                        .build());
            }
            clusterPermission.endClusterPermission();
            installSpec.endSpec().endInstall();
            csvSpecBuilder.endSpec();
        }

        public String getFileName() {
            return csvGroupName + ".csv.yml";
        }

        private String getIconName() {
            return csvGroupName + ".icon.png";
        }

        byte[] getYAMLData(String serviceAccountName, ClusterRole clusterRole, Role role,
                Deployment deployment) throws IOException {
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

            final var installSpec = csvSpecBuilder.editOrNewInstall().editOrNewSpec();
            handleClusterRole(serviceAccountName, clusterRole, groupToCRInfo, installSpec);

            handlerRole(serviceAccountName, role, installSpec);

            handleDeployment(deployment, installSpec);

            // do not forget to end the elements!!
            installSpec.endSpec().endInstall();
            csvSpecBuilder.endSpec();

            final var csv = csvBuilder.build();
            return YAML_MAPPER.writeValueAsBytes(csv);

        }

        private void handleDeployment(Deployment deployment,
                NamedInstallStrategyFluent.SpecNested<InstallNested<SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
            if (deployment != null) {
                installSpec.addNewDeployment()
                        .withName(deployment.getMetadata().getName())
                        .withSpec(deployment.getSpec())
                        .endDeployment();
            }
        }

        private void handlerRole(String serviceAccountName, Role role,
                NamedInstallStrategyFluent.SpecNested<InstallNested<SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
            if (role != null) {
                installSpec
                        .addNewPermission()
                        .withServiceAccountName(serviceAccountName)
                        .addAllToRules(role.getRules())
                        .endPermission();
            }
        }

        private void handleClusterRole(String serviceAccountName, ClusterRole clusterRole,
                Map<String, Set<ResourceInfo>> groupToCRInfo,
                NamedInstallStrategyFluent.SpecNested<InstallNested<SpecNested<ClusterServiceVersionBuilder>>> installSpec) {
            if (clusterRole != null) {
                // check if we have our CR group in the cluster role fragment and remove the one we added
                // before since we presume that if the user defined a fragment for permissions associated with their
                // CR they want that fragment to take precedence over automatically generated code
                final var rules = clusterRole.getRules();
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
                                    if (p.getRules().isEmpty()) {
                                        nowEmptyPermissions.add(permissionPosition[0]);
                                    }
                                }
                            });

                            permissionPosition[0]++;
                        });

                        // remove now empty permissions
                        nowEmptyPermissions.forEach(
                                i -> clusterPermissions.remove(i.intValue()));
                        installSpec.addAllToClusterPermissions(clusterPermissions);
                    }
                });
                installSpec
                        .addNewClusterPermission()
                        .withServiceAccountName(serviceAccountName)
                        .addAllToRules(rules)
                        .endClusterPermission();
            }
        }

        public String getControllerName() {
            return controllerName;
        }

        private Boolean hasMatchingClusterPermission(ResourceInfo cri,
                NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec,
                Integer[] ruleIndex, Integer[] clusterPermissionIndex) {
            return installSpec.hasMatchingClusterPermission(cp -> {
                int i = 0;
                for (PolicyRule rule : cp.getRules()) {
                    if (rule.getApiGroups().contains(cri.getGroup())) {
                        ruleIndex[0] = i;
                        return true;
                    }
                    i++;
                }
                clusterPermissionIndex[0]++;
                return false;
            });
        }
    }
}
