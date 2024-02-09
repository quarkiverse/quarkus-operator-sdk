package io.quarkiverse.operatorsdk.bundle;

import static io.quarkiverse.operatorsdk.bundle.Utils.BUNDLE;
import static io.quarkiverse.operatorsdk.bundle.Utils.getCSVFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.ReconcilerWithNoCsvMetadata;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class OutputLabelSelectorsIfRequestedTest {
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName("output-kube-labels")
            .overrideConfigKey("quarkus.kubernetes.add-version-to-label-selectors", "true")
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, ReconcilerWithNoCsvMetadata.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldHaveVersionLabelWhenRequested() throws IOException {
        final var name = "output-kube-labels";
        final var csv = getCSVFor(prodModeTestResults.getBuildDir().resolve(BUNDLE), name);
        final var deployment = csv.getSpec().getInstall().getSpec().getDeployments().get(0);
        assertEquals(name, deployment.getName());
        assertNotNull(deployment.getSpec().getSelector().getMatchLabels().get("app.kubernetes.io/version"));
    }
}
