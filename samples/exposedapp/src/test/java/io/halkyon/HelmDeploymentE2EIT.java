package io.halkyon;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@ExtendWith(HelmDeploymentE2EIT.RunOnlyIfHelmIsAvailableCondition.class)
class HelmDeploymentE2EIT {

    final static Logger log = Logger.getLogger(HelmDeploymentE2EIT.class);

    static class RunOnlyIfHelmIsAvailableCondition implements ExecutionCondition {
        private static boolean helmIsAvailable = false;

        RunOnlyIfHelmIsAvailableCondition() {
            try {
                Process exec = Runtime.getRuntime().exec("helm");
                if (exec.waitFor() == 0) {
                    helmIsAvailable = true;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (helmIsAvailable) {
                return ConditionEvaluationResult.enabled("Helm is available");
            } else {
                log.info("Helm is not available, skipping this test");
                return ConditionEvaluationResult.disabled("Helm is not available");
            }
        }
    }

    public static final String TEST_RESOURCE = "test1";
    public static final String DEPLOYMENT_NAME = "quarkus-operator-sdk-samples-exposedapp";
    public static final String DEFAULT_NAMESPACE = "default";
    public static final String WATCH_NAMESPACES_KEY = "app.envs.QUARKUS_OPERATOR_SDK_CONTROLLERS_EXPOSEDAPP_NAMESPACES";

    @Inject
    KubernetesClient client;

    private String namespace;
    private File kubeConfigFile;

    @PostConstruct
    public void init() {
        kubeConfigFile = KubeUtils.generateConfigFromClient(client);
    }

    @AfterEach
    void cleanup() {
        deleteHelmDeployment();
    }

    @Test
    void testClusterWideDeployment() {
        deployWithHelm();

        log.info("Deploying test resource in clusterscopetest namespace");
        namespace = "clusterscopetest";
        createNamespace(namespace);
        client.resource(testResource(namespace)).create();

        checkResourceProcessed(namespace);

        client.resource(testResource(namespace)).delete();
    }

    @Test
    void testWatchingCurrentNamespace() {
        deployWithHelm(WATCH_NAMESPACES_KEY, Constants.WATCH_CURRENT_NAMESPACE);

        namespace = "own-ns-test";
        createNamespace(namespace);
        client.resource(testResource(namespace)).create();

        checkResourceNotProcessed(namespace);

        // resource is reconciled in default namespace where controller runs
        client.resource(testResource(DEFAULT_NAMESPACE)).create();
        checkResourceProcessed(DEFAULT_NAMESPACE);

        client.resource(testResource(namespace)).delete();
        client.resource(testResource(DEFAULT_NAMESPACE)).delete();
    }

    @Test
    void testWatchingSetOfNamespaces() {
        String excludedNS = "excludedns1";
        String ns1 = "testns1";
        String ns2 = "testns2";
        createNamespace(excludedNS);
        createNamespace(ns1);
        createNamespace(ns2);

        deployWithHelm(WATCH_NAMESPACES_KEY, ns1 + "\\," + ns2);

        client.resource(testResource(excludedNS)).create();
        checkResourceNotProcessed(excludedNS);

        client.resource(testResource(ns1)).create();
        client.resource(testResource(ns2)).create();
        checkResourceProcessed(ns1);
        checkResourceProcessed(ns2);

        client.resource(testResource(ns1)).delete();
        client.resource(testResource(ns2)).delete();
        client.resource(testResource(excludedNS)).delete();
    }

    private void checkResourceNotProcessed(String namespace) {
        await("Resource not reconciled in other namespace")
                .pollDelay(Duration.ofSeconds(5)).untilAsserted(() -> {
                    var exposedApp = client.resources(ExposedApp.class)
                            .inNamespace(namespace)
                            .withName(TEST_RESOURCE).get();
                    assertThat(exposedApp, is(notNullValue()));
                    assertThat(exposedApp.getStatus().getMessage(), equalTo("reconciled-not-exposed"));
                });
    }

    private void checkResourceProcessed(String namespace) {
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            var exposedApp = client.resources(ExposedApp.class)
                    .inNamespace(namespace)
                    .withName(TEST_RESOURCE).get();
            assertThat(exposedApp, is(notNullValue()));
            assertThat(exposedApp.getStatus().getMessage(), anyOf(equalTo("exposed"), equalTo("reconciled-not-exposed")));
        });
    }

    ExposedApp testResource(String namespace) {
        var app = new ExposedApp();
        app.setMetadata(new ObjectMetaBuilder()
                .withName(TEST_RESOURCE)
                .withNamespace(namespace)
                .build());
        app.setSpec(new ExposedAppSpec());
        app.getSpec().setImageRef("nginx:1.14.2");
        return app;
    }

    private void createNamespace(String namespace) {
        var ns = new Namespace();
        ns.setMetadata(new ObjectMetaBuilder()
                .withName(namespace)
                .build());
        if (client.namespaces().resource(ns).get() == null) {
            client.namespaces().resource(ns).create();
        }
    }

    private void deployWithHelm(String... values) {
        StringBuilder command = createHelmCommand("helm install exposedapp target/helm/kubernetes/" + DEPLOYMENT_NAME);
        for (int i = 0; i < values.length; i = i + 2) {
            command.append(" --set ").append(values[i]).append("=").append(values[i + 1]);
        }

        execHelmCommand(command.toString());
        client.apps().deployments().inNamespace(DEFAULT_NAMESPACE).withName(DEPLOYMENT_NAME)
                .waitUntilReady(120, TimeUnit.SECONDS);
    }

    private StringBuilder createHelmCommand(String command) {
        return new StringBuilder()
                .append(command)
                .append(" --kubeconfig ").append(kubeConfigFile.getPath());
    }

    private void deleteHelmDeployment() {
        execHelmCommand(createHelmCommand("helm delete exposedapp").toString());
        await().untilAsserted(() -> {
            var deployment = client.apps().deployments().inNamespace(DEFAULT_NAMESPACE).withName(DEPLOYMENT_NAME).get();
            assertThat(deployment, is(nullValue()));
        });

        // CRDs are not deleted automatically by helm, so we need to delete them manually
        client.apiextensions().v1().customResourceDefinitions().withName("exposedapps.halkyon.io").delete();
    }

    private static void execHelmCommand(String command) {
        log.infof("Executing command: %s", command);
        execHelmCommand(command, false);
    }

    private static void execHelmCommand(String command, boolean silent) {
        try {
            var process = Runtime.getRuntime().exec(command);
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                log.infof("Error with helm: %s", new String(process.getErrorStream().readAllBytes()));
                if (!silent) {
                    throw new IllegalStateException("Helm exit code: " + exitCode);
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
