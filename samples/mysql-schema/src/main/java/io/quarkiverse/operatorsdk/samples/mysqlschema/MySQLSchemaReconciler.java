package io.quarkiverse.operatorsdk.samples.mysqlschema;

import static io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;

import javax.inject.Inject;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SchemaDependentResource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SecretDependentResource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;
import io.quarkus.logging.Log;

@ControllerConfiguration(dependents = {
        @Dependent(type = SecretDependentResource.class),
        @Dependent(type = SchemaDependentResource.class)
})
public class MySQLSchemaReconciler
        implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema> {

    @Inject
    SchemaService schemaService;

    @Override
    public UpdateControl<MySQLSchema> reconcile(MySQLSchema resource, Context<MySQLSchema> context) {
        // we only need to update the status if we just built the schema, i.e. when it's present in the
        // context
        Secret secret = context.getSecondaryResource(Secret.class).orElseThrow();
        SchemaDependentResource schemaDependentResource = context.managedDependentResourceContext()
                .getDependentResource(SchemaDependentResource.class);
        return schemaDependentResource.fetchResource(resource).map(s -> {
            updateStatusPojo(resource, secret.getMetadata().getName(),
                    secret.getData().get(MYSQL_SECRET_USERNAME));
            Log.infof("Schema %s created - updating CR status", resource.getMetadata().getName());
            return UpdateControl.updateStatus(resource);
        }).orElse(UpdateControl.noUpdate());
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
}
