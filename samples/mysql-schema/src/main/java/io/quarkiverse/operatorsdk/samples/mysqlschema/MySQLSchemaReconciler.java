package io.quarkiverse.operatorsdk.samples.mysqlschema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.RandomStringUtils;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.Schema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;
import io.quarkus.logging.Log;

@ControllerConfiguration
public class MySQLSchemaReconciler
        implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema>, EventSourceInitializer<MySQLSchema>,
        Cleaner<MySQLSchema> {
    public static final String SECRET_FORMAT = "%s-secret";
    public static final String USERNAME_FORMAT = "%s-user";
    public static final int POLL_PERIOD = 500;

    @Inject
    KubernetesClient kubernetesClient;

    @Inject
    SchemaPollingResourceSupplier schemaPollingResourceSupplier;

    @Inject
    SchemaService schemaService;

    @Override
    public List<EventSource> prepareEventSources(EventSourceContext<MySQLSchema> context) {
        return List.of(new PerResourcePollingEventSource<>(schemaPollingResourceSupplier, context.getPrimaryCache(),
                POLL_PERIOD, Schema.class));
    }

    @Override
    public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema, Context<MySQLSchema> context) {
        Log.infof("Reconciling MySQLSchema with name: %s", schema.getMetadata().getName());
        var dbSchema = context.getSecondaryResource(Schema.class);
        Log.debugf("Schema: %s found for: %s ", dbSchema, schema.getMetadata().getName());
        try (Connection connection = schemaService.getConnection()) {
            if (dbSchema.isEmpty()) {
                Log.debugf("Creating Schema and related resources for: %s", schema.getMetadata().getName());
                var schemaName = schema.getMetadata().getName();
                String password = RandomStringUtils.random(16);
                String secretName = String.format(SECRET_FORMAT, schemaName);
                String userName = String.format(USERNAME_FORMAT, schemaName);

                schemaService.createSchemaAndRelatedUser(connection, schemaName,
                        schema.getSpec().getEncoding(), userName, password);
                createSecret(schema, password, secretName, userName);
                updateStatusPojo(schema, secretName, userName);
                Log.infof("Schema %s created - updating CR status", schema.getMetadata().getName());
                return UpdateControl.updateStatus(schema);
            } else {
                Log.debugf("No update on MySQLSchema with name: %s", schema.getMetadata().getName());
                return UpdateControl.noUpdate();
            }
        } catch (SQLException e) {
            Log.error("Error while creating Schema", e);

            throw new IllegalStateException(e);
        }
    }

    @Override
    public DeleteControl cleanup(MySQLSchema schema, Context context) {
        Log.infof("Cleaning up for: %s", schema.getMetadata().getName());
        try (Connection connection = schemaService.getConnection()) {
            var dbSchema = schemaService.getSchema(connection, schema.getMetadata().getName());
            if (dbSchema.isPresent()) {
                var userName = schema.getStatus() != null ? schema.getStatus().getUserName() : null;
                schemaService.deleteSchemaAndRelatedUser(connection, schema.getMetadata().getName(),
                        userName);
            } else {
                Log.infof(
                        "Delete event ignored for schema '%s', real schema doesn't exist",
                        schema.getMetadata().getName());
            }
            return DeleteControl.defaultDelete();
        } catch (SQLException e) {
            Log.error("Error while trying to delete Schema", e);
            return DeleteControl.noFinalizerRemoval();
        }
    }

    @Override
    public ErrorStatusUpdateControl<MySQLSchema> updateErrorStatus(MySQLSchema schema,
            Context<MySQLSchema> context, Exception e) {
        SchemaStatus status = new SchemaStatus();
        status.setUrl(null);
        status.setUserName(null);
        status.setSecretName(null);
        status.setStatus("ERROR: " + e.getMessage());
        schema.setStatus(status);

        return ErrorStatusUpdateControl.updateStatus(schema);
    }

    private void updateStatusPojo(MySQLSchema schema, String secretName, String userName) {
        SchemaStatus status = new SchemaStatus();
        status.setUrl(schemaService.getjdbcURL(schema));
        status.setUserName(userName);
        status.setSecretName(secretName);
        status.setStatus("CREATED");
        schema.setStatus(status);
    }

    private void createSecret(MySQLSchema schema, String password, String secretName,
            String userName) {

        var currentSecret = kubernetesClient.secrets().inNamespace(schema.getMetadata().getNamespace())
                .withName(secretName).get();
        if (currentSecret != null) {
            return;
        }
        Secret credentialsSecret = new SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .withOwnerReferences(new OwnerReference("mysql.sample.javaoperatorsdk/v1",
                        false, false, "MySQLSchema",
                        schema.getMetadata().getName(), schema.getMetadata().getUid()))
                .endMetadata()
                .addToData(
                        "MYSQL_USERNAME", Base64.getEncoder().encodeToString(userName.getBytes()))
                .addToData(
                        "MYSQL_PASSWORD", Base64.getEncoder().encodeToString(password.getBytes()))
                .build();
        this.kubernetesClient
                .secrets()
                .inNamespace(schema.getMetadata().getNamespace())
                .create(credentialsSecret);
    }
}
