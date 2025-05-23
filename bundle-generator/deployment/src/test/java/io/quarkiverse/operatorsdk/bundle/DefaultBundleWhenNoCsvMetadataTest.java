package io.quarkiverse.operatorsdk.bundle;

import static io.quarkiverse.operatorsdk.bundle.Utils.BUNDLE;
import static io.quarkiverse.operatorsdk.bundle.Utils.getCSVFor;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.ReconcilerWithNoCsvMetadata;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class DefaultBundleWhenNoCsvMetadataTest {

    public static final String NAME = "reconciler-with-no-csv-metadata";
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(NAME)
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, ReconcilerWithNoCsvMetadata.class));

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleEvenWhenCsvMetadataIsNotUsed() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(BUNDLE);
        assertTrue(Files.exists(bundle.resolve(NAME)));
        final var csv = getCSVFor(bundle, NAME);
        final var deployment = csv.getSpec().getInstall().getSpec().getDeployments().get(0);
        assertEquals(NAME, deployment.getName());
        // by default, we shouldn't output the version label in the selector match labels as the default controlling this should be overridden by KubernetesLabelConfigOverrider
        assertNull(deployment.getSpec().getSelector().getMatchLabels().get("app.kubernetes.io/version"));
        assertEquals(System.getProperty("user.name"), csv.getSpec().getProvider().getName());
    }

}
