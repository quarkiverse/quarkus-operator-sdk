package io.quarkiverse.operatorsdk.test;

import static io.quarkiverse.operatorsdk.annotations.RBACVerbs.*;
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

import io.fabric8.kubernetes.api.Pluralize;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import io.quarkiverse.operatorsdk.annotations.RBACVerbs;
import io.quarkiverse.operatorsdk.deployment.ClusterRoles;
import io.quarkiverse.operatorsdk.deployment.RoleBindings;
import io.quarkiverse.operatorsdk.test.sources.CRUDConfigMap;
import io.quarkiverse.operatorsdk.test.sources.CreateOnlyService;
import io.quarkiverse.operatorsdk.test.sources.Foo;
import io.quarkiverse.operatorsdk.test.sources.InjectedDependency;
import io.quarkiverse.operatorsdk.test.sources.NoDefaultArgConstructorDependent;
import io.quarkiverse.operatorsdk.test.sources.NonKubeResource;
import io.quarkiverse.operatorsdk.test.sources.ReadOnlySecret;
import io.quarkiverse.operatorsdk.test.sources.SimpleCR;
import io.quarkiverse.operatorsdk.test.sources.SimpleReconciler;
import io.quarkiverse.operatorsdk.test.sources.SimpleSpec;
import io.quarkiverse.operatorsdk.test.sources.SimpleStatus;
import io.quarkiverse.operatorsdk.test.sources.TestCR;
import io.quarkiverse.operatorsdk.test.sources.TestReconciler;
import io.quarkiverse.operatorsdk.test.sources.TypelessAnotherKubeResource;
import io.quarkiverse.operatorsdk.test.sources.TypelessKubeResource;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class OperatorSDKTest {

    public static final List<String> READ_VERBS_LIST = Arrays.asList(READ_VERBS);
    private static final String APPLICATION_NAME = "test";
    // Start unit test with your extension loaded
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APPLICATION_NAME)
            .withApplicationRoot((jar) -> jar
                    .addClasses(TestReconciler.class, TestCR.class, CRUDConfigMap.class, ReadOnlySecret.class,
                            CreateOnlyService.class, NonKubeResource.class, Foo.class,
                            TypelessKubeResource.class, TypelessAnotherKubeResource.class,
                            NoDefaultArgConstructorDependent.class, InjectedDependency.class,
                            SimpleReconciler.class, SimpleCR.class, SimpleSpec.class, SimpleStatus.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    private static boolean hasReadAndAdditionalVerbsOnly(PolicyRule rule, String... additionalVerbs) {
        final var verbs = rule.getVerbs();
        return (verbs.size() == READ_VERBS_LIST.size() + additionalVerbs.length) && verbs.containsAll(READ_VERBS_LIST)
                && verbs.containsAll(List.of(additionalVerbs));
    }

    private static boolean isReadOnly(PolicyRule rule) {
        return rule.getVerbs().equals(READ_VERBS_LIST);
    }

    private static boolean hasOnlyCommonVerbs(PolicyRule rule) {
        final var verbs = rule.getVerbs();
        return verbs.size() == ALL_COMMON_VERBS.length
                && verbs.containsAll(Arrays.asList(ALL_COMMON_VERBS));
    }

    @Test
    public void shouldCreateRolesAndRoleBindings() throws IOException {
        final var kubernetesDir = prodModeTestResults.getBuildDir().resolve("kubernetes");
        final var kubeManifest = kubernetesDir.resolve("kubernetes.yml");
        Assertions.assertTrue(Files.exists(kubeManifest));
        final var kubeIS = new FileInputStream(kubeManifest.toFile());
        final var serializer = new KubernetesSerialization();
        @SuppressWarnings("unchecked")
        final var kubeResources = (List<HasMetadata>) serializer.unmarshal(kubeIS);

        // check cluster role for TestReconciler
        final var testReconcilerRoleName = ClusterRoles.getClusterRoleName(TestReconciler.NAME);

        // make sure the target role exists because otherwise the test will succeed without actually checking anything
        assertTrue(kubeResources.stream().anyMatch(i -> testReconcilerRoleName.equals(i.getMetadata().getName())));

        kubeResources.stream()
                .filter(i -> testReconcilerRoleName.equals(i.getMetadata().getName()))
                .map(ClusterRole.class::cast)
                .forEach(cr -> {
                    final var rules = cr.getRules();
                    assertEquals(6, rules.size());
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getApiGroups().equals(List.of(HasMetadata.getGroup(TestCR.class))))
                            .anyMatch(rule -> {
                                final var resources = rule.getResources();
                                final var plural = HasMetadata.getPlural(TestCR.class);
                                // status is void so shouldn't be present in resources
                                return resources.equals(List.of(plural, plural + "/finalizers"))
                                        && rule.getVerbs().equals(Arrays.asList(ALL_COMMON_VERBS));
                            }));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(HasMetadata.getPlural(Secret.class))))
                            .anyMatch(OperatorSDKTest::isReadOnly));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(HasMetadata.getPlural(
                                    Service.class))))
                            .anyMatch(rule -> hasReadAndAdditionalVerbsOnly(rule, CREATE)));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(HasMetadata.getPlural(ConfigMap.class))))
                            .anyMatch(OperatorSDKTest::hasOnlyCommonVerbs));
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getResources().equals(List.of(RBACRule.ALL)))
                            .anyMatch(rule -> rule.getVerbs().equals(List.of(UPDATE))
                                    && rule.getApiGroups().equals(List.of(RBACRule.ALL))));
                    // Both typeless dependents are using the same GVK so the verbs associated with their policy rules should be merged into a single one
                    assertEquals(1, rules.stream()
                            .filter(rule -> rule.getApiGroups().equals(List.of(TypelessKubeResource.GROUP))
                                    && rule.getResources().equals(List.of(Pluralize.toPlural(TypelessKubeResource.KIND))))
                            .count());
                    assertTrue(rules.stream()
                            .filter(rule -> rule.getApiGroups().equals(List.of(TypelessKubeResource.GROUP))
                                    && rule.getResources().equals(List.of(Pluralize.toPlural(TypelessKubeResource.KIND))))
                            .allMatch(rule -> hasReadAndAdditionalVerbsOnly(rule, DELETE)));
                });

        // check that we have a role binding for TestReconciler and that it uses the operator-level specified namespace
        final var testReconcilerBindingName = RoleBindings.getRoleBindingName(TestReconciler.NAME);
        assertTrue(kubeResources.stream().anyMatch(i -> testReconcilerBindingName.equals(i.getMetadata().getName())));
        kubeResources.stream()
                .filter(i -> testReconcilerBindingName.equals(i.getMetadata().getName()))
                .map(RoleBinding.class::cast)
                .forEach(rb -> {
                    assertEquals("operator-level-buildtime-ns", rb.getMetadata().getNamespace());
                    assertEquals(testReconcilerRoleName, rb.getRoleRef().getName());
                });

        // check cluster role for SimpleReconciler
        final var simpleReconcilerRoleName = ClusterRoles.getClusterRoleName(SimpleReconciler.NAME);

        //make sure the target role exists because otherwise the test will succeed without actually checking anything
        assertTrue(kubeResources.stream()
                .anyMatch(i -> simpleReconcilerRoleName.equals(i.getMetadata().getName())));

        kubeResources.stream()
                .filter(i -> simpleReconcilerRoleName.equals(i.getMetadata().getName()))
                .map(ClusterRole.class::cast)
                .forEach(cr -> {
                    final var rules = cr.getRules();
                    assertEquals(3, rules.size());

                    var rule = rules.get(0);
                    assertEquals(List.of(HasMetadata.getGroup(SimpleCR.class)), rule.getApiGroups());
                    final var resources = rule.getResources();
                    final var plural = HasMetadata.getPlural(SimpleCR.class);
                    // status is void so shouldn't be present in resources
                    assertEquals(List.of(plural, plural + "/status", plural + "/finalizers"), resources);
                    assertEquals(Arrays.asList(ALL_COMMON_VERBS), rule.getVerbs());

                    // check additional rules
                    rule = rules.get(1);
                    assertEquals(List.of(SimpleReconciler.CERTIFICATES_K8S_IO_GROUP), rule.getApiGroups());
                    assertEquals(List.of(RBACVerbs.UPDATE), rule.getVerbs());
                    assertEquals(List.of(SimpleReconciler.ADDITIONAL_UPDATE_RESOURCE), rule.getResources());
                    assertTrue(rule.getResourceNames().isEmpty());
                    assertTrue(rule.getNonResourceURLs().isEmpty());

                    rule = rules.get(2);
                    assertEquals(List.of(SimpleReconciler.CERTIFICATES_K8S_IO_GROUP), rule.getApiGroups());
                    assertEquals(List.of(SimpleReconciler.SIGNERS_VERB), rule.getVerbs());
                    assertEquals(List.of(SimpleReconciler.SIGNERS_RESOURCE), rule.getResources());
                    assertEquals(List.of(SimpleReconciler.SIGNERS_RESOURCE_NAMES), rule.getResourceNames());
                    assertTrue(rule.getNonResourceURLs().isEmpty());
                });

        // check that we have a role binding for SimpleReconciler and that it uses the watched namespace defined in application.properties
        // since generate-with-watched-namespaces only specifies one namespace, this should be a role binding, not a cluster role binding
        final var simpleReconcilerBindingName = RoleBindings.getRoleBindingName(SimpleReconciler.NAME);
        assertTrue(kubeResources.stream().anyMatch(i -> simpleReconcilerBindingName.equals(i.getMetadata().getName())));
        kubeResources.stream()
                .filter(i -> simpleReconcilerBindingName.equals(i.getMetadata().getName()))
                .map(RoleBinding.class::cast)
                .forEach(rb -> {
                    assertEquals("simple-ns", rb.getMetadata().getNamespace());
                    assertEquals(simpleReconcilerRoleName, rb.getRoleRef().getName());
                });

        // check that we have an additional role binding (again, single watched namespace) as specified by annotation on SimpleReconciler
        final var additionalRBName = RoleBindings.getSpecificRoleBindingName(SimpleReconciler.NAME,
                SimpleReconciler.ROLE_REF_NAME);
        assertTrue(kubeResources.stream().anyMatch(i -> additionalRBName.equals(i.getMetadata().getName())));
        kubeResources.stream()
                .filter(i -> additionalRBName.equals(i.getMetadata().getName()))
                .map(RoleBinding.class::cast)
                .forEach(rb -> {
                    assertEquals("simple-ns", rb.getMetadata().getNamespace());
                    assertEquals(SimpleReconciler.ROLE_REF_NAME, rb.getRoleRef().getName());
                });

        // CRD validation is activated by default and there should therefore be a cluster role and 2 associated bindings to access CRDs (one per reconciler)
        assertTrue(kubeResources.stream()
                .anyMatch(i -> ClusterRoles.JOSDK_CRD_VALIDATING_CLUSTER_ROLE_NAME.equals(i.getMetadata().getName())
                        && i instanceof ClusterRole));
        final var simpleCRDValidatingCRBName = RoleBindings.getCRDValidatingBindingName(SimpleReconciler.NAME);
        final var testCRDValidatingCRBName = RoleBindings.getCRDValidatingBindingName(TestReconciler.NAME);
        kubeResources.stream()
                .filter(ClusterRoleBinding.class::isInstance)
                .map(ClusterRoleBinding.class::cast)
                .forEach(crb -> {
                    final var name = crb.getMetadata().getName();
                    assertTrue(simpleCRDValidatingCRBName.equals(name) || testCRDValidatingCRBName.equals(name));
                    // the bindings should bind the CRD validating CR to the operator's service account
                    final var roleRef = crb.getRoleRef();
                    assertEquals(RoleBindings.CRD_VALIDATING_ROLE_REF, roleRef);
                    assertEquals(1, crb.getSubjects().size());
                    assertEquals(APPLICATION_NAME, crb.getSubjects().get(0).getName());
                    assertEquals(HasMetadata.getKind(ServiceAccount.class), crb.getSubjects().get(0).getKind());
                });

        // checks that CRDs are generated
        Assertions.assertTrue(Files.exists(kubernetesDir.resolve(CustomResource.getCRDName(TestCR.class) + "-v1.yml")));
        Assertions.assertTrue(Files.exists(kubernetesDir.resolve(CustomResource.getCRDName(SimpleCR.class) + "-v1.yml")));
    }
}
