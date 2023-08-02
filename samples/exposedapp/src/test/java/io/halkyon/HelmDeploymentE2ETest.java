package io.halkyon;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.test.junit.QuarkusTest;

@Disabled
@QuarkusTest
class HelmDeploymentE2ETest {

    final static Logger log = Logger.getLogger(HelmDeploymentE2ETest.class);
    public static final String TEST_RESOURCE = "test1";

    @Inject
    KubernetesClient client;

    private String namespace;

    @AfterEach
    void cleanup() {
        //        deleteWithHelm();
        client.resource(testResource(namespace)).delete();
    }

    @Test
    void testClusterWideDeployment() {
        //        deployWithHelm();
        namespace = "clusterscopetest";
        createNamespace(namespace);
        client.resource(testResource(namespace)).create();

        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            var exposedApp = client.resources(ExposedApp.class)
                    .inNamespace(namespace)
                    .withName(TEST_RESOURCE).get();
            assertThat(exposedApp, is(notNullValue()));
            assertThat(exposedApp.getStatus(), is(notNullValue()));
            assertThat(exposedApp.getStatus().getMessage(), equalTo("exposed"));
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

    //    @Test
    //    void testWatchingCurrentNamespace() {
    //
    //    }
    //
    //    @Test
    //    void testWatchingSetOfNamespaces() {
    //
    //    }

    private void deployWithHelm() {
        execHelmCommand("helm install exposedapp target/helm");
    }

    private void deleteWithHelm() {
        execHelmCommand("helm delete exposedapp");
    }

    private static void execHelmCommand(String command) {
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
