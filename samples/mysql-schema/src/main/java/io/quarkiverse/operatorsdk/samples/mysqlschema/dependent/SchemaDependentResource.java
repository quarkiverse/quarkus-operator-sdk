package io.quarkiverse.operatorsdk.samples.mysqlschema.dependent;

import static io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SecretDependentResource.MYSQL_SECRET_PASSWORD;
import static io.quarkiverse.operatorsdk.samples.mysqlschema.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.quarkiverse.operatorsdk.samples.mysqlschema.MySQLSchema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.Schema;
import io.quarkiverse.operatorsdk.samples.mysqlschema.schema.SchemaService;

@ApplicationScoped
public class SchemaDependentResource
        extends PerResourcePollingDependentResource<Schema, MySQLSchema>
        implements EventSourceProvider<MySQLSchema>,
        DependentResourceConfigurator<ResourcePollerConfig>,
        Creator<Schema, MySQLSchema>,
        Deleter<MySQLSchema> {
    public static final String NAME = "schema";
    private static final Logger log = LoggerFactory.getLogger(SchemaDependentResource.class);

    @Inject
    SchemaService schemaService;

    public SchemaDependentResource() {
        super(Schema.class);
    }

    @Override
    public void configureWith(ResourcePollerConfig config) {
        if (config != null) {
            setPollingPeriod(config.getPollPeriod());
        }
    }

    @Override
    public Optional<ResourcePollerConfig> configuration() {
        return Optional.of(new ResourcePollerConfig((int) getPollingPeriod()));
    }

    @Override
    public Schema desired(MySQLSchema primary, Context<MySQLSchema> context) {
        return new Schema(primary.getMetadata().getName(), primary.getSpec().getEncoding());
    }

    @Override
    public Schema create(Schema target, MySQLSchema mySQLSchema, Context<MySQLSchema> context) {
        try (Connection connection = schemaService.getConnection()) {
            final var secret = context.getSecondaryResource(Secret.class).orElseThrow();
            var username = decode(secret.getData().get(MYSQL_SECRET_USERNAME));
            var password = decode(secret.getData().get(MYSQL_SECRET_PASSWORD));
            return schemaService.createSchemaAndRelatedUser(
                    connection,
                    target.getName(),
                    target.getCharacterSet(), username, password);
        } catch (SQLException e) {
            log.error("Error while creating Schema", e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void delete(MySQLSchema primary, Context<MySQLSchema> context) {
        log.info("Delete schema");
        try (Connection connection = schemaService.getConnection()) {
            var userName = primary.getStatus() != null ? primary.getStatus().getUserName() : null;
            schemaService.deleteSchemaAndRelatedUser(connection, primary.getMetadata().getName(),
                    userName);
        } catch (SQLException e) {
            throw new RuntimeException("Error while trying to delete Schema", e);
        }
    }

    @Override
    public Set<Schema> fetchResources(MySQLSchema primaryResource) {
        try (Connection connection = schemaService.getConnection()) {
            return schemaService.getSchema(connection, primaryResource.getMetadata().getName())
                    .map(Set::of).orElse(Collections.emptySet());
        } catch (SQLException e) {
            throw new RuntimeException("Error while trying read Schema", e);
        }
    }

    public static String decode(String value) {
        return new String(Base64.getDecoder().decode(value.getBytes()));
    }
}
