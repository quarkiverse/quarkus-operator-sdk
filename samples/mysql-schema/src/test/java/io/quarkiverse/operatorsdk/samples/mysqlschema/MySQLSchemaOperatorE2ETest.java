package io.quarkiverse.operatorsdk.samples.mysqlschema;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@WithKubernetesTestServer
@QuarkusTest
class MySQLSchemaOperatorE2ETest {

    final static Logger log = Logger.getLogger(MySQLSchemaOperatorE2ETest.class);

    @KubernetesTestServer
    KubernetesServer mockServer;

    @InjectSpy
    SchemaService schemaService;

    @Test
    void test() {
        KubernetesClient client = mockServer.getClient();

        MySQLSchema testSchema = new MySQLSchema();
        testSchema.setMetadata(new ObjectMetaBuilder()
                .withName("mydb1")
                .withNamespace(mockServer.getClient().getNamespace())
                .build());
        testSchema.setSpec(new SchemaSpec());
        testSchema.getSpec().setEncoding("utf8");

        log.infof("Creating test MySQLSchema object: %s", testSchema);
        client.resource(testSchema).createOrReplace();

        log.info("Waiting 10 seconds for expected resources to be created and updated");
        await().atMost(10, SECONDS).untilAsserted(() -> {
            MySQLSchema updatedSchema = client.resources(MySQLSchema.class)
                    .inNamespace(testSchema.getMetadata().getNamespace())
                    .withName(testSchema.getMetadata().getName()).get();
            assertThat(updatedSchema.getStatus(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getStatus(), equalTo("CREATED"));
            assertThat(updatedSchema.getStatus().getSecretName(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getUserName(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getUrl(), startsWith("jdbc:mysql://"));
        });

        verify(schemaService, times(1)).createSchemaAndRelatedUser(any(), eq("mydb1"), eq("utf8"), anyString(),
                anyString());
    }
}
