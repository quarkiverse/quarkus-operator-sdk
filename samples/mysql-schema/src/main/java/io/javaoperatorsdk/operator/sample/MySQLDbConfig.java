package io.javaoperatorsdk.operator.sample;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "db")
public interface MySQLDbConfig {

    String host();

    @WithDefault("3306")
    int port();

    String user();

    String password();
}
