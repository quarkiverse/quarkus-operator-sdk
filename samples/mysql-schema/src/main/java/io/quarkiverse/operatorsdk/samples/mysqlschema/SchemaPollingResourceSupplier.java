package io.quarkiverse.operatorsdk.samples.mysqlschema;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.Schema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;

@ApplicationScoped
public class SchemaPollingResourceSupplier
        implements PerResourcePollingEventSource.ResourceFetcher<Schema, MySQLSchema> {

    @Inject
    SchemaService schemaService;

    @Override
    public Optional<Schema> fetchResource(MySQLSchema primaryResource) {
        return schemaService.getSchema(primaryResource.getMetadata().getName());
    }
}
