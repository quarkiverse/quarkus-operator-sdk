package io.quarkiverse.operatorsdk.it;

import jakarta.inject.Singleton;

import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

@Singleton
public class TestLeaderElectionConfiguration extends LeaderElectionConfiguration {

    public TestLeaderElectionConfiguration() {
        super("testLeaseName");
    }
}
