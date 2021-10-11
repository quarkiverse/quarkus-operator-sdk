package io.quarkiverse.operatorsdk.csv.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "operator-sdk", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class CSVGenerationConfiguration {
    /**
     * Whether the extension should generate a ClusterServiceVersion manifest for controllers.
     */
    @ConfigItem(defaultValue = "false")
    public Optional<Boolean> generateCSV;

}
