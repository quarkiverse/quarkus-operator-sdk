package io.quarkiverse.operatorsdk.samples.mysqlschema;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;

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
        deleteWithHelm();
    }

    @Test
    void testClusterWideDeployment() {
        deployWithHelm();
        namespace = "clusterscopetest";
        createNamespace(namespace);
        client.resource(testSchema(namespace)).create();

        await().untilAsserted(() -> {
            MySQLSchema updToDateSchema = client.resources(MySQLSchema.class)
                    .inNamespace(namespace)
                    .withName(TEST_RESOURCE).get();
            assertThat(updToDateSchema, is(notNullValue()));
            assertThat(updToDateSchema.getStatus(), is(notNullValue()));
            assertThat(updToDateSchema.getStatus().getStatus(), equalTo("CREATED"));
        });
    }

    MySQLSchema testSchema(String namespace) {
        var schema = new MySQLSchema();
        schema.setMetadata(new ObjectMetaBuilder()
                .withName(TEST_RESOURCE)
                .withNamespace(namespace)
                .build());
        schema.setSpec(new SchemaSpec());
        schema.getSpec().setEncoding("utf8");
        return schema;
    }

    private void createNamespace(String namespace) {
        var ns = new Namespace();
        ns.setMetadata(new ObjectMetaBuilder()
                .withName(namespace)
                .build());
        client.namespaces().resource(ns).create();
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
        execHelmCommand("helm install mysql target/helm");
    }

    private void deleteWithHelm() {
        execHelmCommand("helm delete mysql");
    }

    private static void execHelmCommand(String command) {
        try {
            var process = Runtime.getRuntime().exec(command);
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                log.infof("Error with helm: %s", new String(process.getErrorStream().readAllBytes()));
                throw new IllegalStateException("Helm exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

}
