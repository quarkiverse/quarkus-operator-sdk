package io.quarkiverse.operatorsdk.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.operatorsdk.runtime.CRDUtils;

public class CRDInfoAugmentingCRDPostProcessorTest {

    @Test
    void processShouldWork() throws IOException {
        final var proc = new CRDGeneratorV2.CRDInfoAugmentingCRDPostProcessor(null);
        final var crd = CRDUtils.loadFrom(Path.of("src/test/resources/external.crd.yml"));
        proc.process(crd, CRDUtils.DEFAULT_CRD_SPEC_VERSION);

        assertEquals(Set.of("spec"), proc.selectableFields(CRDUtils.DEFAULT_CRD_SPEC_VERSION, crd.getFullResourceName()));
    }
}
