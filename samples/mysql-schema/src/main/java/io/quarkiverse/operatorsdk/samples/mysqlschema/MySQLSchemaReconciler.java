package io.quarkiverse.operatorsdk.samples.mysqlschema;

import static io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SchemaDependentResource.decode;
import static io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;

import jakarta.inject.Inject;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SchemaDependentResource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SecretDependentResource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.Schema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;
import io.quarkus.logging.Log;

@Workflow(dependents = {
        @Dependent(type = SecretDependentResource.class),
        @Dependent(type = SchemaDependentResource.class, name = SchemaDependentResource.NAME)
})
@ControllerConfiguration
@SuppressWarnings("unused")
public class MySQLSchemaReconciler
        implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema> {

    @Inject
    SchemaService schemaService;

    @Override
    public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema, Context<MySQLSchema> context) {
        // we only need to update the status if we just built the schema, i.e. when it's
        // present in the context
        return context.getSecondaryResource(Secret.class)
                .map(secret -> context.getSecondaryResource(Schema.class, SchemaDependentResource.NAME)
                        .map(s -> {
                            updateStatusPojo(schema, secret.getMetadata().getName(),
                                    decode(secret.getData().get(MYSQL_SECRET_USERNAME)));
                            Log.infof("Schema %s created - updating CR status", s.getName());
                            return UpdateControl.patchStatus(schema);
                        }).orElse(UpdateControl.noUpdate()))
                .orElse(UpdateControl.noUpdate());
    }

    @Override
    public ErrorStatusUpdateControl<MySQLSchema> updateErrorStatus(MySQLSchema schema,
            Context<MySQLSchema> context, Exception e) {
        Log.error("updateErrorStatus", e);
        SchemaStatus status = new SchemaStatus();
        status.setUrl(null);
        status.setUserName(null);
        status.setSecretName(null);
        status.setStatus("ERROR: " + e.getMessage());
        schema.setStatus(status);

        return ErrorStatusUpdateControl.patchStatus(schema);
    }

    private void updateStatusPojo(MySQLSchema schema, String secretName, String userName) {
        SchemaStatus status = new SchemaStatus();
        status.setUrl(schemaService.getjdbcURL(schema));
        status.setUserName(userName);
        status.setSecretName(secretName);
        status.setStatus("CREATED");
        schema.setStatus(status);
    }
}
