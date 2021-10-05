package io.quarkiverse.operatorsdk.deployment;

import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionSpecFluent;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.NamedInstallStrategyFluent;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkiverse.operatorsdk.runtime.CSVMetadataHolder;
import io.quarkiverse.operatorsdk.runtime.CustomResourceInfo;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class CSVGenerator {
    private static final ObjectMapper YAML_MAPPER;

    static {
        YAML_MAPPER = new ObjectMapper((new YAMLFactory()).enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        YAML_MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        YAML_MAPPER.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        YAML_MAPPER.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
    }

    public static void generate(OutputTargetBuildItem outputTarget, CRDGenerationInfo info,
            Map<String, CSVMetadataHolder> csvMetadata,
            List<GeneratedKubernetesResourceBuildItem> generatedKubernetesManifests) {
        // load generated manifests
        final var outputDir = outputTarget.getOutputDirectory().resolve(KUBERNETES);
        final var serviceAccountName = new String[1];
        final var clusterRole = new ClusterRole[1];
        final var role = new Role[1];
        final var deployment = new Deployment[1];

        generatedKubernetesManifests.stream()
                .filter(bi -> bi.getName().equals("kubernetes.yml"))
                .findAny()
                .ifPresent(
                        bi -> {
                            final var resources = Serialization.unmarshalAsList(new ByteArrayInputStream(bi.getContent()));
                            resources.getItems().forEach(r -> {
                                if (r instanceof ServiceAccount) {
                                    serviceAccountName[0] = r.getMetadata().getName();
                                    return;
                                }

                                if (r instanceof ClusterRole) {
                                    clusterRole[0] = (ClusterRole) r;
                                    return;
                                }

                                if (r instanceof Role) {
                                    role[0] = (Role) r;
                                    return;
                                }

                                if (r instanceof Deployment) {
                                    deployment[0] = (Deployment) r;
                                    return;
                                }
                            });
                        });

        final var controllerToCSVBuilders = new HashMap<String, ClusterServiceVersionBuilder>(7);
        final var groupToCRInfo = new HashMap<String, Set<CustomResourceInfo>>(7);
        info.getCrds().forEach((crdName, crdVersionToInfo) -> {
            final var versions = info.getCRInfosByCRVersionFor(crdName);
            versions.forEach((version, cri) -> {
                // record group name and associated CustomResourceInfos
                groupToCRInfo.computeIfAbsent(cri.getGroup(), s -> new HashSet<>()).add(cri);

                final var csvGroupName = cri.getCsvGroupName();
                final var metadata = csvMetadata.get(csvGroupName);
                final var csvSpecBuilder = controllerToCSVBuilders
                        .computeIfAbsent(csvGroupName, s -> new ClusterServiceVersionBuilder()
                                .withNewMetadata().withName(s).endMetadata())
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
                        .withName(crdName)
                        .withVersion(version)
                        .withKind(cri.getKind())
                        .endOwned().endCustomresourcedefinitions();

                // make sure that we add permissions for our own CR
                final var installSpec = csvSpecBuilder.editOrNewInstall().editOrNewSpec();
                final Integer[] ruleIndex = new Integer[1];
                final Integer[] clusterPermissionIndex = new Integer[] { 0 };
                final Boolean hasMatchingClusterPermission = hasMatchingClusterPermission(cri, installSpec, ruleIndex,
                        clusterPermissionIndex);
                final var clusterPermission = hasMatchingClusterPermission
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
                            .addToVerbs("get", "list", "watch", "create", "delete", "patch", "update")
                            .build());
                }
                clusterPermission.endClusterPermission();
                installSpec.endSpec().endInstall();
                csvSpecBuilder.endSpec();
            });
        });

        controllerToCSVBuilders.forEach((controllerName, csvBuilder) -> {
            final File file = new File(outputDir.toFile(), controllerName + ".csv.yml");

            final var csvSpec = csvBuilder.editOrNewSpec();
            // deal with icon
            try (var iconAsStream = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(controllerName + ".icon.png");
                    var outputStream = new FileOutputStream(file)) {
                if (iconAsStream != null) {
                    final byte[] iconAsBase64 = Base64.getEncoder().encode(iconAsStream.readAllBytes());
                    csvSpec.addNewIcon()
                            .withBase64data(new String(iconAsBase64))
                            .withMediatype("image/png")
                            .endIcon();
                }

                final var installSpec = csvSpec.editOrNewInstall()
                        .editOrNewSpec();
                if (clusterRole[0] != null) {
                    // todo: check if we have our CR group in the cluster role fragment and remove the one we added
                    // before since we presume that if the user defined a fragment for permissions associated with their
                    // CR we want that fragment to take precedence over automatically generated code

                    installSpec
                            .addNewClusterPermission()
                            .withServiceAccountName(serviceAccountName[0])
                            .addAllToRules(clusterRole[0].getRules())
                            .endClusterPermission();
                }

                if (role[0] != null) {
                    installSpec
                            .addNewPermission()
                            .withServiceAccountName(serviceAccountName[0])
                            .addAllToRules(role[0].getRules())
                            .endPermission();
                }

                if (deployment[0] != null) {
                    installSpec.addNewDeployment()
                            .withName(deployment[0].getMetadata().getName())
                            .withSpec(deployment[0].getSpec())
                            .endDeployment();
                }

                // do not forget to end the elements!!
                installSpec.endSpec().endInstall();
                csvSpec.endSpec();

                final var csv = csvBuilder.build();
                YAML_MAPPER.writeValue(outputStream, csv);
                OperatorSDKProcessor.log.infov("Generated CSV for {0} controller -> {1}", controllerName, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static Boolean hasMatchingClusterPermission(CustomResourceInfo cri,
            NamedInstallStrategyFluent.SpecNested<ClusterServiceVersionSpecFluent.InstallNested<ClusterServiceVersionFluent.SpecNested<ClusterServiceVersionBuilder>>> installSpec,
            Integer[] ruleIndex, Integer[] clusterPermissionIndex) {
        final var hasMatchingClusterPermission = installSpec
                .hasMatchingClusterPermission(cp -> {
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
        return hasMatchingClusterPermission;
    }
}
