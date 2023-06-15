package io.quarkiverse.operatorsdk.test;

import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.ALL_VERBS;
import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.CREATE_VERB;
import static io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator.READ_VERBS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.quarkiverse.operatorsdk.deployment.AddClusterRolesDecorator;
import io.quarkiverse.operatorsdk.test.sources.CRUDConfigMap;
import io.quarkiverse.operatorsdk.test.sources.CreateOnlyService;
import io.quarkiverse.operatorsdk.test.sources.Foo;
import io.quarkiverse.operatorsdk.test.sources.NonKubeResource;
import io.quarkiverse.operatorsdk.test.sources.ReadOnlySecret;
import io.quarkiverse.operatorsdk.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.test.sources.SimpleReconciler;
import io.quarkiverse.operatorsdk.test.sources.TestCR;
import io.quarkiverse.operatorsdk.test.sources.TestReconciler;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class OperatorSDKTest {

    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("test")
            .withApplicationRoot((jar) -> jar
                    .addClasses(TestReconciler.class, TestCR.class, CRUDConfigMap.class, ReadOnlySecret.class,
                            CreateOnlyService.class, NonKubeResource.class, Foo.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldCreateRolesAndRoleBindings() throws IOException {
        final var kubeManifest = prodModeTestResults.getBuildDir().resolve("kubernetes")
                .resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));
        final var kubeIS = new FileInputStream(kubeManifest.toFile());
        // use unmarshall version with parameters map to ensure code goes through the proper processing wrt multiple documents
        @SuppressWarnings("unchecked")
        final var kubeResources = (List<HasMetadata>) Serialization.unmarshal(kubeIS);

        // check cluster role for TestReconciler
        final var testReconcilerRoleName = AddClusterRolesDecorator.getClusterRoleName(TestReconciler.NAME);
        kubeResources.stream()
                .filter(i -> testReconcilerRoleName.equals(i.getMetadata().getName()))
                .map(ClusterRole.class::cast)
                .forEach(cr -> {
                    final var rules = cr.getRules();
                    assertEquals(4, rules.size());
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getApiGroups().equals(List.of(HasMetadata.getGroup(TestCR.class))))
                            .anyMatch(rule -> {
                                final var resources = rule.getResources();
                                final var plural = HasMetadata.getPlural(TestCR.class);
                                // status is void so shouldn't be present in resources
                                return resources.equals(List.of(plural, plural + "/finalizers"))
                                        && rule.getVerbs().equals(Arrays.asList(ALL_VERBS));
                            }));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(HasMetadata.getPlural(Secret.class))))
                            .anyMatch(rule -> rule.getVerbs().equals(Arrays.asList(READ_VERBS))));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(HasMetadata.getPlural(
                                    Service.class))))
                            .anyMatch(rule -> rule.getVerbs().containsAll(Arrays.asList(READ_VERBS))
                                    && rule.getVerbs().contains(CREATE_VERB)));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(HasMetadata.getPlural(ConfigMap.class))))
                            .anyMatch(rule -> {
                                final var verbs = rule.getVerbs();
                                return verbs.size() == ALL_VERBS.length && verbs.containsAll(Arrays.asList(ALL_VERBS));
                            }));
                });

        // check cluster role for SimpleReconciler
        final var simpleReconcilerRoleName = AddClusterRolesDecorator.getClusterRoleName(SimpleReconciler.NAME);
        kubeResources.stream()
                .filter(i -> simpleReconcilerRoleName.equals(i.getMetadata().getName()))
                .map(ClusterRole.class::cast)
                .forEach(cr -> {
                    final var rules = cr.getRules();
                    assertEquals(1, rules.size());
                    final var rule = rules.get(0);
                    assertEquals(List.of(HasMetadata.getGroup(SimpleCR.class)), rule.getApiGroups());
                    final var resources = rule.getResources();
                    final var plural = HasMetadata.getPlural(SimpleCR.class);
                    // status is void so shouldn't be present in resources
                    assertEquals(List.of(plural, plural + "/status", plural + "/finalizers"), resources);
                    assertEquals(Arrays.asList(ALL_VERBS), rule.getVerbs());
                });
    }
}
