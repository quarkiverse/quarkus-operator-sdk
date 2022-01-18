package io.javaoperatorsdk.operator.sample;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.kubernetes.client.KubernetesTestServer;
import io.quarkus.test.kubernetes.client.WithKubernetesTestServer;

@WithKubernetesTestServer
@QuarkusTest
class MySQLSchemaOperatorE2E {

    final static Logger log = Logger.getLogger(MySQLSchemaOperatorE2E.class);

    @KubernetesTestServer
    KubernetesServer mockServer;

    @InjectMock
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

        Mockito.when(schemaService.getSchema("mydb1")).thenReturn(Optional.empty());
        Mockito.doNothing().when(schemaService).createSchemaAndRelatedUser(isNull(), eq("mydb1"), eq("utf8"), anyString(),
                anyString());
        Mockito.when(schemaService.getjdbcURL(any(MySQLSchema.class))).thenReturn("jdbc:foo://bar");

        log.infof("Creating test MySQLSchema object: %s", testSchema);
        client.resource(testSchema).createOrReplace();

        log.info("Waiting 5 minutes for expected resources to be created and updated");
        await().atMost(10, SECONDS).untilAsserted(() -> {
            MySQLSchema updatedSchema = client.resources(MySQLSchema.class).inNamespace(testSchema.getMetadata().getNamespace())
                    .withName(testSchema.getMetadata().getName()).get();
            assertThat(updatedSchema.getStatus(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getStatus(), equalTo("CREATED"));
            assertThat(updatedSchema.getStatus().getSecretName(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getUserName(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getUrl(), equalTo("jdbc:foo://bar"));
        });

        verify(schemaService, times(1)).createSchemaAndRelatedUser(isNull(), eq("mydb1"), eq("utf8"), anyString(), anyString());
    }
}
