package io.quarkiverse.operatorsdk.deployment;

import java.io.File;
import java.util.List;
import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.quarkiverse.operatorsdk.runtime.CRDInfos;

interface CRDGenerator {
    void generate(List<String> crdSpecVersions, File outputDir, Set<String> generated, CRDInfos converted);

    void scheduleForGeneration(Class<? extends CustomResource<?, ?>> crClass);
}
