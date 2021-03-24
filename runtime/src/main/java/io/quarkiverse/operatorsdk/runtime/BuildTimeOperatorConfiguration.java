package io.quarkiverse.operatorsdk.runtime;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class BuildTimeOperatorConfiguration {
    /**
     * Whether the operator should check that the CRD is properly deployed and that the associated
     * {@link CustomResource} implementation matches its information before registering the associated
     * controller.
     */
    @ConfigItem(defaultValue = "true")
    public Optional<Boolean> checkCRDAndValidateLocalModel;

    /**
     * The directory where the CRDs will be generated, relative to the project's output directory.
     */
    @ConfigItem(defaultValue = "kubernetes")
    public String crdOutputDirectory;

    /**
     * Maps a controller name to its configuration.
     */
    @ConfigItem
    public Map<String, BuildTimeControllerConfiguration> controllers;

}
