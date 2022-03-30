package io.quarkiverse.operatorsdk.samples.mysqlschema.dependent;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.MySQLSchema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.Schema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;

@ApplicationScoped
public class SchemaPollingResourceFetcher
        implements PerResourcePollingEventSource.ResourceFetcher<Schema, MySQLSchema> {

    @Inject
    SchemaService schemaService;

    @Override
    public Optional<Schema> fetchResource(MySQLSchema resource) {
        return schemaService.getSchema(resource.getMetadata().getName());
    }

}
