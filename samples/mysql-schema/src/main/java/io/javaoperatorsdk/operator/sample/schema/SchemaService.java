package io.javaoperatorsdk.operator.sample.schema;

import static java.lang.String.format;

import java.sql.*;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.javaoperatorsdk.operator.sample.MySQLDbConfig;
import io.javaoperatorsdk.operator.sample.MySQLSchema;
import io.quarkus.logging.Log;

@ApplicationScoped
public class SchemaService {

    @Inject
    MySQLDbConfig mySQLDbConfig;

    public SchemaService(MySQLDbConfig mySQLDbConfig) {
        this.mySQLDbConfig = mySQLDbConfig;
    }

    public Optional<Schema> getSchema(String name) {
        try (Connection connection = getConnection()) {
            return getSchema(connection, name);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void createSchemaAndRelatedUser(Connection connection, String schemaName,
            String encoding,
            String userName,
            String password) {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        format(
                                "CREATE SCHEMA `%1$s` DEFAULT CHARACTER SET %2$s",
                                schemaName, encoding));
            }
            if (!userExists(connection, userName)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(format("CREATE USER '%1$s' IDENTIFIED BY '%2$s'", userName, password));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        format("GRANT ALL ON `%1$s`.* TO '%2$s'", schemaName, userName));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deleteSchemaAndRelatedUser(Connection connection, String schemaName,
            String userName) {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute(format("DROP DATABASE `%1$s`", schemaName));
            }
            Log.infof("Deleted Schema '%s'", schemaName);
            if (userName != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(format("DROP USER '%1$s'", userName));
                }
                Log.infof("Deleted User '%s'", userName);
            }

        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean userExists(Connection connection, String username) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM mysql.user WHERE user = ?")) {
            ps.setString(1, username);
            try (ResultSet resultSet = ps.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Schema> getSchema(Connection connection, String schemaName) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schemaName);
            try (ResultSet resultSet = ps.executeQuery()) {
                // CATALOG_NAME, SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME,
                // DEFAULT_COLLATION_NAME, SQL_PATH
                var exists = resultSet.next();
                if (!exists) {
                    return Optional.empty();
                } else {
                    return Optional.of(new Schema(resultSet.getString("SCHEMA_NAME"),
                            resultSet.getString("DEFAULT_CHARACTER_SET_NAME")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public Connection getConnection() {
        try {
            String connectionString = format("jdbc:mysql://%1$s:%2$s", mySQLDbConfig.host(),
                    mySQLDbConfig.port());

            Log.debugf("Connecting to '%s' with user '%s'", connectionString, mySQLDbConfig.user());
            return DriverManager.getConnection(connectionString, mySQLDbConfig.user(),
                    mySQLDbConfig.password());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getjdbcURL(MySQLSchema schema) {
        return format(
                "jdbc:mysql://%1$s/%2$s",
                System.getenv("MYSQL_HOST"), schema.getMetadata().getName());
    }

}
