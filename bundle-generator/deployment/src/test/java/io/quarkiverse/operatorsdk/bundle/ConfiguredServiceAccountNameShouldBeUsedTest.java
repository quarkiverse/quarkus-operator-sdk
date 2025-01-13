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

public class ConfiguredServiceAccountNameShouldBeUsedTest {

    public static final String APPLICATION_NAME = "configured-service-account-name";
    public static final String SA_NAME = "my-operator-sa";
    public static final String NS_NAME = "some-namespace";
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APPLICATION_NAME)
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, ReconcilerWithNoCsvMetadata.class))
            .overrideConfigKey("quarkus.kubernetes.rbac.service-accounts." + SA_NAME + ".namespace", NS_NAME);

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldWriteBundleEvenWhenCsvMetadataIsNotUsed() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(BUNDLE);
        assertTrue(Files.exists(bundle.resolve(APPLICATION_NAME)));
        final var csv = getCSVFor(bundle, APPLICATION_NAME);
        final var deployment = csv.getSpec().getInstall().getSpec().getDeployments().get(0);
        assertEquals(APPLICATION_NAME, deployment.getName());
        assertEquals(SA_NAME, deployment.getSpec().getTemplate().getSpec().getServiceAccountName());
    }

}
