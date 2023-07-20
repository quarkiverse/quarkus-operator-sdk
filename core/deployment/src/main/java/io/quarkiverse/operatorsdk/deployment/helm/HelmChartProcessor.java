package io.quarkiverse.operatorsdk.deployment.helm;

import java.util.List;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class HelmChartProcessor {

    private static final Logger log = Logger.getLogger(HelmChartProcessor.class);

    @BuildStep
    public void handleHelmCharts(BuildProducer<ArtifactResultBuildItem> dummy,
            List<GeneratedKubernetesResourceBuildItem> generatedResources) {

        log.infov("Generating helm chart");
    }

}
