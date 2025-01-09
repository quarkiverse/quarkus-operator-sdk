package io.quarkiverse.operatorsdk.bundle;

import static io.quarkiverse.operatorsdk.bundle.Utils.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.quarkiverse.operatorsdk.bundle.sources.External;
import io.quarkiverse.operatorsdk.bundle.sources.ExternalDependentResource;
import io.quarkiverse.operatorsdk.bundle.sources.First;
import io.quarkiverse.operatorsdk.bundle.sources.ReconcilerWithExternalCR;
import io.quarkiverse.operatorsdk.bundle.sources.V1Beta1CRD;
import io.quarkiverse.operatorsdk.runtime.CRDUtils;
import io.quarkus.test.LogCollectingTestResource;
import io.quarkus.test.ProdBuildResults;
import io.quarkus.test.ProdModeTestResults;
import io.quarkus.test.QuarkusProdModeTest;

public class ExternalCRDsTest {

    private static final String APP = "reconciler-with-external-crds";
    @RegisterExtension
    static final QuarkusProdModeTest config = new QuarkusProdModeTest()
            .setApplicationName(APP)
            .setLogRecordPredicate(r -> r.getLoggerName().equals(CRDUtils.class.getName()))
            .withApplicationRoot((jar) -> jar
                    .addClasses(First.class, External.class, ExternalDependentResource.class,
                            ReconcilerWithExternalCR.class))
            .overrideConfigKey("quarkus.operator-sdk.crd.external-crd-locations",
                    "src/test/external-crds/v1beta1spec.crd.yml, src/test/external-crds/external.crd.yml");

    @ProdBuildResults
    private ProdModeTestResults prodModeTestResults;

    @Test
    public void shouldProcessExternalCRDsWhenPresentAndOutputWarningsAsNeeded() throws IOException {
        final var bundle = prodModeTestResults.getBuildDir().resolve(BUNDLE);
        assertTrue(Files.exists(bundle.resolve(APP)));
        final var manifests = bundle.resolve(APP).resolve("manifests");
        assertFileExistsIn(manifests.resolve(getCRDNameFor(External.class)), manifests);
        assertFileExistsIn(manifests.resolve(getCRDNameFor(V1Beta1CRD.class)), manifests);

        final var csv = getCSVFor(bundle, APP);
        final var externalCRD = csv.getSpec().getCustomresourcedefinitions().getRequired().get(0);
        assertEquals(HasMetadata.getFullResourceName(External.class), externalCRD.getName());
        final var v1beta1 = csv.getSpec().getCustomresourcedefinitions().getRequired().get(1);
        assertEquals(HasMetadata.getFullResourceName(V1Beta1CRD.class), v1beta1.getName());

        assertEquals(1, prodModeTestResults.getRetainedBuildLogRecords().stream()
                .map(LogCollectingTestResource::format)
                .filter(logRecord -> logRecord.contains("src/test/external-crds/v1beta1spec.crd.yml")).count());
    }

}
