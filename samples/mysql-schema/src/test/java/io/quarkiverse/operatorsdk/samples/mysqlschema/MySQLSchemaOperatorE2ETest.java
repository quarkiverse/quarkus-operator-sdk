package io.quarkiverse.operatorsdk.samples.mysqlschema;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.Schema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;

@QuarkusTest
class MySQLSchemaOperatorE2ETest {

    final static Logger log = Logger.getLogger(MySQLSchemaOperatorE2ETest.class);
    private static final String DB_NAME = "mydb1";
    private static final String ENCODING = "utf8";

    @InjectSpy
    SchemaService schemaService;

    @Inject
    Operator operator;

    @Inject
    KubernetesClient client;

    @BeforeEach
    void startOperator() {
        operator.start();
    }

    @AfterEach
    void stopOperator() {
        operator.stop();
    }

    @Test
    void test() {

        final Optional<Schema> maybeSchema = schemaService.getSchema(DB_NAME);
        if (maybeSchema.isPresent()) {
            fail("Schema " + DB_NAME + " should not already exist");
        }

        MySQLSchema testSchema = new MySQLSchema();
        testSchema.setMetadata(new ObjectMetaBuilder()
                .withName(DB_NAME)
                .withNamespace(client.getNamespace())
                .build());
        testSchema.setSpec(new SchemaSpec());
        testSchema.getSpec().setEncoding(ENCODING);

        log.infof("Creating test MySQLSchema object: %s", testSchema);
        client.resource(testSchema).serverSideApply();

        log.info("Waiting 10 seconds for expected resources to be created and updated");
        await().pollDelay(9, SECONDS).atMost(10, SECONDS).untilAsserted(() -> {
            MySQLSchema updatedSchema = client.resources(MySQLSchema.class)
                    .inNamespace(testSchema.getMetadata().getNamespace())
                    .withName(testSchema.getMetadata().getName()).get();
            assertThat(updatedSchema.getStatus(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getStatus(), equalTo("CREATED"));
            assertThat(updatedSchema.getStatus().getSecretName(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getUserName(), is(notNullValue()));
            assertThat(updatedSchema.getStatus().getUrl(), startsWith("jdbc:mysql://"));
        });

        verify(schemaService, times(1)).createSchemaAndRelatedUser(any(), eq(DB_NAME), eq(ENCODING), anyString(),
                anyString());

        client.resource(testSchema).delete();

        await()
                .atMost(10, SECONDS)
                .ignoreExceptions()
                .untilAsserted(
                        () -> {
                            MySQLSchema updatedSchema = client
                                    .resources(MySQLSchema.class)
                                    .inNamespace(testSchema.getMetadata().getNamespace())
                                    .withName(testSchema.getMetadata().getName())
                                    .get();
                            assertThat(updatedSchema, is(nullValue()));
                        });

        verify(schemaService, times(1)).deleteSchemaAndRelatedUser(any(), eq(DB_NAME), anyString());
    }
}
