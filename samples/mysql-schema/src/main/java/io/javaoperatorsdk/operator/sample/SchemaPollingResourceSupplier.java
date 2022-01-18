package io.javaoperatorsdk.operator.sample;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

@ApplicationScoped
public class SchemaPollingResourceSupplier
        implements PerResourcePollingEventSource.ResourceSupplier<Schema, MySQLSchema> {

    @Inject
    SchemaService schemaService;

    @Override
    public Optional<Schema> getResource(MySQLSchema resource) {
        return schemaService.getSchema(resource.getMetadata().getName());
    }
}
