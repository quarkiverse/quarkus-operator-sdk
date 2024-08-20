package io.quarkiverse.operatorsdk.deployment.helm;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;

// Note that steps of this processor won't be run in dev mode because ArtifactResultBuildItems are only considered in NORMAL mode
public class HelmChartDisabledLogger {
    private static final Logger log = Logger.getLogger(HelmChartDisabledLogger.class);

    @BuildStep(onlyIfNot = HelmGenerationEnabled.class)
    @Produce(ArtifactResultBuildItem.class)
    void outputHelmGenerationDisabled() {
        log.debug("Generating Helm chart is disabled");
    }
}
