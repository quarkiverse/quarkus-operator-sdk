package io.quarkiverse.operatorsdk.deployment;

import static io.quarkus.kubernetes.deployment.Constants.KUBERNETES;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import io.dekorate.utils.Serialization;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.ClusterRole;
import io.fabric8.openshift.api.model.PolicyRule;
import io.fabric8.openshift.api.model.Role;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersionBuilder;
import io.quarkiverse.operatorsdk.runtime.CRDGenerationInfo;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;

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

    public static void generate(OutputTargetBuildItem outputTarget, CRDGenerationInfo info) {
        // load generated manifests
        final var outputDir = outputTarget.getOutputDirectory().resolve(KUBERNETES);
        File manifest = outputDir.resolve("kubernetes.yml").toFile();

        final var serviceAccountName = new String[1];
        final var clusterRole = new ClusterRole[1];
        final var role = new Role[1];
        final var deployment = new Deployment[1];
        if (manifest.exists()) {
            KubernetesList manifests;
            try (FileInputStream fis = new FileInputStream(manifest)) {
                manifests = Serialization.unmarshalAsList(fis);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            manifests.getItems().forEach(i -> {
                if (i instanceof ServiceAccount) {
                    serviceAccountName[0] = i.getMetadata().getName();
                    return;
                }

                if (i instanceof ClusterRole) {
                    clusterRole[0] = (ClusterRole) i;
                    return;
                }

                if (i instanceof Role) {
                    role[0] = (Role) i;
                    return;
                }

                if (i instanceof Deployment) {
                    deployment[0] = (Deployment) i;
                    return;
                }
            });
        }

        final var controllerToCSVBuilders = new HashMap<String, ClusterServiceVersionBuilder>(7);
        info.getCrds().forEach((crdName, crdVersionToInfo) -> {
            final var versions = info.getCRInfosFor(crdName);
            versions.forEach((version, cri) -> controllerToCSVBuilders
                    .computeIfAbsent(cri.getControllerName(), s -> new ClusterServiceVersionBuilder()
                            .withNewMetadata().withName(s).endMetadata())
                    .editOrNewSpec()
                    .editOrNewCustomresourcedefinitions()
                    .addNewOwned()
                    .withName(crdName)
                    .withVersion(version)
                    .withKind(cri.getKind())
                    .endOwned().endCustomresourcedefinitions().endSpec());
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
                    installSpec
                            .addNewClusterPermission()
                            .withServiceAccountName(serviceAccountName[0])
                            .addAllToRules(clusterRole[0].getRules().stream().map(CSVGenerator::convertPolicyRule)
                                    .collect(Collectors.toSet()))
                            .endClusterPermission();
                }

                if (role[0] != null) {
                    installSpec
                            .addNewPermission()
                            .withServiceAccountName(serviceAccountName[0])
                            .addAllToRules(
                                    role[0].getRules().stream().map(CSVGenerator::convertPolicyRule)
                                            .collect(Collectors.toSet()))
                            .endPermission();
                }

                if (deployment[0] != null) {
                    installSpec.addNewDeployment()
                            .withName(deployment[0].getMetadata().getName())
                            .withSpec(deployment[0].getSpec())
                            .endDeployment();
                }

                installSpec.endSpec().endInstall();

                final var csv = csvBuilder.build();
                YAML_MAPPER.writeValue(outputStream, csv);
                OperatorSDKProcessor.log.infov("Generated CSV for {0} controller -> {1}", controllerName, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static io.fabric8.kubernetes.api.model.rbac.PolicyRule convertPolicyRule(PolicyRule pr) {
        return new io.fabric8.kubernetes.api.model.rbac.PolicyRule(pr.getApiGroups(), pr.getNonResourceURLs(),
                pr.getResourceNames(), pr.getResources(), pr.getVerbs());
    }

}
