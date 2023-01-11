package io.quarkiverse.operatorsdk.it;

import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import jakarta.inject.Singleton;

@Singleton
public class TestLeaderElectionConfiguration extends LeaderElectionConfiguration {

    public TestLeaderElectionConfiguration() {
        super("testLeaseName");
    }
}
