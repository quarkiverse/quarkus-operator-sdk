package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class BuildTimeOperatorConfiguration {

    /**
     * Maps a controller name to its configuration.
     */
    @ConfigItem
    public Map<String, BuildTimeControllerConfiguration> controllers;

    /**
     * The optional CRD-related configuration options
     */
    @ConfigItem
    public Optional<CRDConfiguration> crd;

}
