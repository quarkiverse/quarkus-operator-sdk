package io.quarkiverse.operatorsdk.csv.deployment;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.quarkiverse.operatorsdk.csv.deployment.builders.CsvManifestsBuilder;
import io.quarkiverse.operatorsdk.csv.deployment.builders.ManifestsBuilder;
import io.quarkiverse.operatorsdk.csv.deployment.builders.RbacManifestsBuilder;
import io.quarkiverse.operatorsdk.csv.runtime.CSVMetadataHolder;
import io.quarkus.arc.impl.Sets;

public class ManifestsGenerator {

    private ManifestsGenerator() {
    }

    public static Set<ManifestsBuilder> prepareGeneration(Map<String, AugmentedResourceInfo> info,
            Map<String, CSVMetadataHolder> csvMetadata) {
        final var builders = new ConcurrentHashMap<String, Set<ManifestsBuilder>>(7);
        return info.values().parallelStream()
                .flatMap(cri -> builders.computeIfAbsent(cri.getCsvGroupName(),
                        s -> Sets.of(new CsvManifestsBuilder(cri, csvMetadata), new RbacManifestsBuilder(cri)))
                        .stream())
                .collect(Collectors.toSet());
    }
}
